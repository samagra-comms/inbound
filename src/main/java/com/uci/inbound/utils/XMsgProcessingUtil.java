package com.uci.inbound.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.provider.factory.AbstractProvider;
import com.uci.adapter.Request.CommonMessage;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.dao.utils.XMessageDAOUtils;
import com.uci.utils.BotService;
import com.uci.utils.kafka.SimpleProducer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.SenderReceiverInfo;
import messagerosa.core.model.XMessage;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Builder
public class XMsgProcessingUtil {

	AbstractProvider adapter;
	CommonMessage inboundMessage;
	SimpleProducer kafkaProducer;
	XMessageRepository xMsgRepo;
	String topicSuccess;
	String topicFailure;
	BotService botService;
	Tracer tracer;

	public void process() throws JsonProcessingException {
		Span rootSpan = tracer.spanBuilder("inbound-processMessage").startSpan();

		int data;
		log.info("incoming message {}", new ObjectMapper().writeValueAsString(inboundMessage));
		try (Scope scope = rootSpan.makeCurrent()) {
			Context currentContext = Context.current();
			Span childSpan1 = createChildSpan("convertMessageToXMsg", currentContext, rootSpan);
			adapter.convertMessageToXMsg(inboundMessage)
					.doOnError(genericError("convertMessageToXMsg", childSpan1))
					.subscribe(xmsg -> {
						childSpan1.end();
						Span childSpan2 = createChildSpan("getAppName", currentContext, rootSpan);
						getAppName(xmsg.getPayload().getText(), xmsg.getFrom())
								.doOnError(genericError("getAppName", childSpan2))
								.subscribe(appName -> {
									childSpan2.end();
									xmsg.setApp(appName);
									XMessageDAO currentMessageToBeInserted = XMessageDAOUtils
											.convertXMessageToDAO(xmsg);
									if (isCurrentMessageNotAReply(xmsg)) {
										Span childSpan3 = createChildSpan("getLatestXMessage", currentContext,
												rootSpan);
										String whatsappId = xmsg.getMessageId().getChannelMessageId();
										getLatestXMessage(xmsg.getFrom().getUserID(), XMessage.MessageState.REPLIED)
												.doOnError(genericError("getLatestXMessage", childSpan3))
												.subscribe(new Consumer<XMessageDAO>() {
													@Override
													public void accept(XMessageDAO previousMessage) {
														childSpan3.end();
														Span childSpan4 = createChildSpan(
																"updatePreviousXMessage", currentContext,
																rootSpan);
														previousMessage.setMessageId(whatsappId);
														xMsgRepo.save(previousMessage)
																.doOnError(genericError(
																		"updatePreviousXMessage", childSpan4))
																.subscribe(new Consumer<XMessageDAO>() {
																	@Override
																	public void accept(
																			XMessageDAO updatedPreviousMessage) {
																		childSpan4.end();
																		Span childSpan5 = createChildSpan(
																				"insertXmessage",
																				currentContext, rootSpan);
																		xMsgRepo.insert(currentMessageToBeInserted)
																				.doOnError(genericError(
																						"insertXmessage", childSpan5))
																				.subscribe(insertedMessage -> {
																					childSpan5.end();
																					Span childSpan6 = createChildSpan(
																							"sendEventToKafka",
																							currentContext, rootSpan);
//																					log.info("current context l1: "
//																							+ currentContext);
																					GlobalOpenTelemetry.getPropagators()
																							.getTextMapPropagator()
																							.inject(currentContext,
																									xmsg, null);
																					sendEventToKafka(xmsg);
																					childSpan6.end();
																					rootSpan.end();
																				});
																	}
																});
													}
												});
									} else {
										Span childSpan3 = createChildSpan("insertXmessage", currentContext,
												rootSpan);
										xMsgRepo.insert(currentMessageToBeInserted)
												.doOnError(
														genericError("insertXmessage", childSpan3))
												.subscribe(xMessageDAO -> {
													childSpan3.end();
													Span childSpan4 = createChildSpan("sendEventToKafka",
															currentContext, rootSpan);
//													log.info("current context l2: " + currentContext);
													GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
															.inject(currentContext, xmsg, null);
													sendEventToKafka(xmsg);
//													log.info("current context lc2: " + currentContext);
													childSpan4.end();
													rootSpan.end();
												});
									}
								});

					});

		} catch (JAXBException e) {
			e.printStackTrace();
			genericException(e.getMessage(), rootSpan);
		} catch (Throwable e) {
			genericException(e.getMessage(), rootSpan);
		} finally {
//			rootSpan.end();
		}
	}

	/**
	 * Create Child Span with current context & parent span
	 * @param spanName
	 * @param context
	 * @param parentSpan
	 * @return childSpan
	 */
	private Span createChildSpan(String spanName, Context context, Span parentSpan) {
		String prefix = "inbound-";
		return tracer.spanBuilder(prefix + spanName).setParent(context.with(parentSpan)).startSpan();
	}

	private void propagateContext(Context currectContext, XMessage xmsg) {
		log.info("current context: " + currectContext);
//    	ContextPropagators propagators = GlobalOpenTelemetry.getPropagators();
//        TextMapPropagator textMapPropagator = propagators.getTextMapPropagator();

		Map<String, String> map = new HashMap();
		map.put("from", "inbound");
		GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(currectContext, xmsg, null);
	}
	
	/**
	 * Log Exceptions & if span exists, add error to span
	 * @param eMsg
	 * @param span
	 */
	private void genericException(String eMsg, Span span) {
		eMsg = "Exception: " + eMsg;
		log.error(eMsg);
		if(span != null) {
			span.setStatus(StatusCode.ERROR, "Exception: " + eMsg);
			span.end();
		}
	}

	/**
	 * Log Exception & if span exists, add error to span 
	 * @param s
	 * @param span
	 * @return
	 */
	private Consumer<Throwable> genericError(String s, Span span) {
		return c -> {
			String msg = "Error in " + s + "::" + c.getMessage();
			log.error(msg);
			if (span != null) {
				log.info("generic message - span");
				span.setStatus(StatusCode.ERROR, msg);
				span.end();
			}
		};
	}

	private boolean isCurrentMessageNotAReply(XMessage xmsg) {
		return !xmsg.getMessageState().equals(XMessage.MessageState.REPLIED);
	}

	private void sendEventToKafka(XMessage xmsg) {
		String xmessage = null;
		try {
			xmessage = xmsg.toXML();
		} catch (JAXBException e) {
			kafkaProducer.send(topicFailure, inboundMessage.toString());
		}
		kafkaProducer.send(topicSuccess, xmessage);
	}

	private Mono<XMessageDAO> getLatestXMessage(String userID, XMessage.MessageState messageState) {
		LocalDateTime yesterday = LocalDateTime.now().minusDays(1L);
		return xMsgRepo.findAllByFromIdAndTimestampAfter(userID, yesterday)
				.doOnError(genericError(String.format("finding previous Message for userID %s", userID), null))
				.collectList().map(xMessageDAOS -> {
					if (xMessageDAOS.size() > 0) {
						List<XMessageDAO> filteredList = new ArrayList<>();
						for (XMessageDAO xMessageDAO : xMessageDAOS) {
							if (xMessageDAO.getMessageState().equals(messageState.name()))
								filteredList.add(xMessageDAO);
						}
						if (filteredList.size() > 0) {
							filteredList.sort(Comparator.comparing(XMessageDAO::getTimestamp));
						}

						return xMessageDAOS.get(0);
					}
					return new XMessageDAO();
				});
	}

	private Mono<String> getAppName(String text, SenderReceiverInfo from) {
		LocalDateTime yesterday = LocalDateTime.now().minusDays(1L);
		if (text.equals("")) {
			try {
				return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name())
						.map(new Function<XMessageDAO, String>() {
							@Override
							public String apply(XMessageDAO xMessageLast) {
								return xMessageLast.getApp();
							}
						}).doOnError(genericError("getLatestXMessage", null));
			} catch (Exception e2) {
				return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name())
						.map(new Function<XMessageDAO, String>() {
							@Override
							public String apply(XMessageDAO xMessageLast) {
								return xMessageLast.getApp();
							}
						}).doOnError(genericError("getLatestXMessage - catch", null));
			}
		} else {
			try {
				log.error("getCampaignFromStartingMessage text: " + text);
				return botService.getCampaignFromStartingMessage(text)
						.flatMap(new Function<String, Mono<? extends String>>() {
							@Override
							public Mono<String> apply(String appName1) {
								if (appName1 == null || appName1.equals("")) {
									try {
										return getLatestXMessage(from.getUserID(), yesterday,
												XMessage.MessageState.SENT.name())
														.map(new Function<XMessageDAO, String>() {
															@Override
															public String apply(XMessageDAO xMessageLast) {
																return (xMessageLast.getApp() == null
																		|| xMessageLast.getApp().isEmpty())
																				? "finalAppName"
																				: xMessageLast.getApp();
															}
														}).doOnError(genericError(
																"getLatestXMessage when appName empty", null));
									} catch (Exception e2) {
										return getLatestXMessage(from.getUserID(), yesterday,
												XMessage.MessageState.SENT.name())
														.map(new Function<XMessageDAO, String>() {
															@Override
															public String apply(XMessageDAO xMessageLast) {
																return (xMessageLast.getApp() == null
																		|| xMessageLast.getApp().isEmpty())
																				? "finalAppName"
																				: xMessageLast.getApp();
															}
														}).doOnError(genericError(
																"getLatestXMessage when appName empty - catch", null));
									}
								}
								return (appName1 == null || appName1.isEmpty()) ? Mono.just("finalAppName")
										: Mono.just(appName1);
							}
						});
			} catch (Exception e) {
				log.error("Exception in getCampaignFromStartingMessage :" + e.getMessage());
				try {
					return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name())
							.map(new Function<XMessageDAO, String>() {
								@Override
								public String apply(XMessageDAO xMessageLast) {
									return xMessageLast.getApp();
								}
							}).doOnError(genericError(
									"getLatestXMessage when exception in getCampaignFromStartingMessage", null));
				} catch (Exception e2) {
					return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name())
							.map(new Function<XMessageDAO, String>() {
								@Override
								public String apply(XMessageDAO xMessageLast) {
									return xMessageLast.getApp();
								}
							}).doOnError(genericError(
									"getLatestXMessage when exception in getCampaignFromStartingMessage - catch", null));
				}
			}
		}
	}

	private Mono<XMessageDAO> getLatestXMessage(String userID, LocalDateTime yesterday, String messageState) {
		return xMsgRepo.findAllByUserIdAndTimestampAfter(userID, yesterday).collectList()
				.map(new Function<List<XMessageDAO>, XMessageDAO>() {
					@Override
					public XMessageDAO apply(List<XMessageDAO> xMessageDAOS) {
						if (xMessageDAOS.size() > 0) {
							List<XMessageDAO> filteredList = new ArrayList<>();
							for (XMessageDAO xMessageDAO : xMessageDAOS) {
								if (xMessageDAO.getMessageState().equals(XMessage.MessageState.SENT.name()))
									filteredList.add(xMessageDAO);
							}
							if (filteredList.size() > 0) {
								filteredList.sort(new Comparator<XMessageDAO>() {
									@Override
									public int compare(XMessageDAO o1, XMessageDAO o2) {
										return o1.getTimestamp().compareTo(o2.getTimestamp());
									}
								});
							}
							return xMessageDAOS.get(0);
						}
						return new XMessageDAO();
					}
				});
	}
}