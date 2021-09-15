package com.uci.inbound.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.provider.factory.AbstractProvider;
import com.uci.adapter.Request.CommonMessage;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.dao.utils.XMessageDAOUtils;
import com.uci.utils.BotService;
import com.uci.utils.CampaignService;
import com.uci.utils.kafka.SimpleProducer;
import com.uci.utils.telemetry.LogTelemetryMessage;
import com.uci.utils.telemetry.TelemetryLogger;
import com.uci.utils.telemetry.util.TelemetryEventNames;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.SenderReceiverInfo;
import messagerosa.core.model.XMessage;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
	CampaignService campaignService;

	@Value("${producer.id}")
	private String producerID;

	private static final Logger telemetrylogger = LogManager.getLogger(TelemetryLogger.class);

	public void process() throws JsonProcessingException {

		String incomingMessage = new ObjectMapper().writeValueAsString(inboundMessage);
		log.info("incoming message {}", incomingMessage);
		try {
			adapter.convertMessageToXMsg(inboundMessage)
					.doOnError(genericError("Error in converting to XMessage by Adapter", null)).subscribe(xmsg -> {
						getAppName(xmsg.getPayload().getText(), xmsg.getFrom()).subscribe(appName -> {
							xmsg.setApp(appName);
							XMessageDAO currentMessageToBeInserted = XMessageDAOUtils.convertXMessageToDAO(xmsg);
							if (isCurrentMessageNotAReply(xmsg)) {
								String whatsappId = xmsg.getMessageId().getChannelMessageId();
								getLatestXMessage(xmsg.getFrom().getUserID(), XMessage.MessageState.REPLIED)
										.doOnError(genericError("Error in getting last message", xmsg))
										.subscribe(new Consumer<XMessageDAO>() {
											@Override
											public void accept(XMessageDAO previousMessage) {
												previousMessage.setMessageId(whatsappId);
												xMsgRepo.save(previousMessage)
														.doOnError(genericError("Error in saving previous message", xmsg))
														.subscribe(new Consumer<XMessageDAO>() {
															@Override
															public void accept(XMessageDAO updatedPreviousMessage) {
																xMsgRepo.insert(currentMessageToBeInserted)
																		.doOnError(genericError(
																				"Error in inserting current message", xmsg))
																		.subscribe(insertedMessage -> {
																			sendEventToKafka(xmsg);
																		});
															}
														});
											}
										});
							} else {
								/* Log telemtery event - Start Conversation */
								campaignService.getCampaignFromNameTransformer(xmsg.getCampaign())
								.doOnError(genericError("Error in getting campaign data for telemetry log", null))
								.subscribe(new Consumer<JsonNode>() {
									@Override
									public void accept(JsonNode t) {
										String id = t.get("id") != null ? t.get("id").asText() : null;
										String ownerId = t.get("owner") != null ? t.get("owner").asText() : null;
										System.out.println("hey");
										telemetrylogger.info(new LogTelemetryMessage(String.format("Start Conversation with incoming message : %s", incomingMessage),
												TelemetryEventNames.STARTCONVERSATION, "", xmsg.getChannel(),
												xmsg.getProvider(), producerID, xmsg.getFrom().getUserID(), id, ownerId));
									}
								});
								
								xMsgRepo.insert(currentMessageToBeInserted)
										.doOnError(genericError("Error in inserting current message", xmsg))
										.subscribe(xMessageDAO -> {
											sendEventToKafka(xmsg);
										});
							}
						});

					});

		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

	/* Error to be logged */
	private Consumer<Throwable> genericError(String s, XMessage xmsg) {
		return c -> {
			log.error(s + "::" + c.getMessage());
			/* Log Telemetry event - Exception */
			if(xmsg != null) {
				telemetrylogger.info(new LogTelemetryMessage(s,
						TelemetryEventNames.AUDITEXCEPTIONS, "", xmsg.getChannel(),
						xmsg.getProvider(), producerID, xmsg.getFrom().getUserID()));
			} else {
				telemetrylogger.info(new LogTelemetryMessage(s,
						TelemetryEventNames.AUDITEXCEPTIONS));
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
				.doOnError(genericError(String.format("Unable to find previous Message for userID %s", userID), null))
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
						});
			} catch (Exception e2) {
				return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name())
						.map(new Function<XMessageDAO, String>() {
							@Override
							public String apply(XMessageDAO xMessageLast) {
								return xMessageLast.getApp();
							}
						});
			}
		} else {
			try {
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
														});
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
														});
									}
								}
								return (appName1 == null || appName1.isEmpty()) ? Mono.just("finalAppName")
										: Mono.just(appName1);
							}
						});
			} catch (Exception e) {
				try {
					return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name())
							.map(new Function<XMessageDAO, String>() {
								@Override
								public String apply(XMessageDAO xMessageLast) {
									return xMessageLast.getApp();
								}
							});
				} catch (Exception e2) {
					return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name())
							.map(new Function<XMessageDAO, String>() {
								@Override
								public String apply(XMessageDAO xMessageLast) {
									return xMessageLast.getApp();
								}
							});
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