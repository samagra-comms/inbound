package com.uci.inbound.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.adapter.provider.factory.AbstractProvider;
import com.uci.adapter.Request.CommonMessage;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.dao.utils.XMessageDAOUtils;
import com.uci.utils.BotService;
import com.uci.utils.bot.util.BotUtil;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.SenderReceiverInfo;
import messagerosa.core.model.XMessage;
import messagerosa.core.model.XMessagePayload;
import messagerosa.xml.XMessageParser;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

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
    String topicOutbound;
    String topicReport;
    BotService botService;
    RedisCacheService redisCacheService;

    public void process() throws JsonProcessingException {

        log.info("incoming message {}", new ObjectMapper().writeValueAsString(inboundMessage));
        
        try {
            adapter.convertMessageToXMsg(inboundMessage)
                    .doOnError(genericError("Error in converting to XMessage by Adapter"))
                    .subscribe(xmsg -> {
                        /* If Message State is send, deliverd, read, send the event to report topic
                        * else process as replied message */
                        if(xmsg.getMessageState().equals(XMessage.MessageState.SENT)
                                || xmsg.getMessageState().equals(XMessage.MessageState.DELIVERED)
                                || xmsg.getMessageState().equals(XMessage.MessageState.READ)) {
                            LocalDateTime yesterday = LocalDateTime.now().minusDays(1L);
                            getLatestXMessageForReport(xmsg.getFrom().getUserID(), yesterday, XMessage.MessageState.SENT.name())
                                    .doOnError(genericError("Exception in finding latest xMessage for sent/delivered/read message")).subscribe(xMessageLast -> {
                                if(xMessageLast.getApp() != null && !xMessageLast.getApp().isEmpty()) {
                                    log.info("App name found: "+xMessageLast.getApp()+" for user id: "+xmsg.getFrom().getUserID());
                                    xmsg.setApp(xMessageLast.getApp());
                                    XMessageDAO currentMessageToBeInserted = XMessageDAOUtils.convertXMessageToDAO(xmsg);
                                    xMsgRepo.insert(currentMessageToBeInserted)
                                            .doOnError(genericError("Error in inserting current message"))
                                            .subscribe(xMessageDAO -> {
                                                sendEventToKafka(xmsg);
                                            });
                                } else {
                                    log.error("App name not found for user id: "+xmsg.getFrom().getUserID()+" for sent/delivered/read message");
                                }
                            });
                        } else {
                            getAppName(xmsg.getPayload().getText(), xmsg.getFrom())
                                    .subscribe(resultPair -> {
                                        log.info("getAppName response:" + resultPair);
                                        /* If bot is invalid, send error message to outbound, else process message */
                                        if (!resultPair.getLeft()) {
                                            log.info("Bot is invalid");
                                            processInvalidBotMessage(xmsg, (Pair<Object, String>) resultPair.getRight());
                                        } else {
                                            Pair<Boolean, String> checkBotPair = (Pair<Boolean, String>) resultPair.getRight();
                                            /* If bot check required, validate bot, else process message */
                                            if (checkBotPair.getLeft()) {
                                                log.info("Bot check required.");
                                                validateBot(checkBotPair.getRight().toString())
                                                        .subscribe(resPair -> {
                                                            log.info("ValidateBot response:" + resPair);
                                                            /* If bot is invalid, send error message to outbound, else process message */
                                                            if (!resPair.getLeft()) {
                                                                log.info("Bot is invalid");
                                                                processInvalidBotMessage(xmsg, (Pair<Object, String>) resPair.getRight());
                                                            } else {
                                                                log.info("Process bot message");
                                                                String appName = resPair.getRight().toString();
                                                                processBotMessage(xmsg, appName);
                                                            }
                                                        });
                                            } else {
                                                log.info("Process bot message");
                                                String appName = checkBotPair.getRight().toString();
                                                processBotMessage(xmsg, appName);
                                            }
                                        }
                                    });
                        }
                    });

        } catch (JAXBException e) {
        	log.info("Error Message: "+e.getMessage());
            e.printStackTrace();
        } catch (NullPointerException e){
            log.error("Error Message: "+e.getMessage());
        }
    }
    
    /**
     * Process Bot Invalid Message - Send bot invalid message to outbound to process
     * @param xmsg
     * @param botDataPair
     */
    private void processInvalidBotMessage(XMessage xmsg, Pair<Object, String> botDataPair) {
    	ObjectNode botNode = (ObjectNode) botDataPair.getLeft();
    	String message = botDataPair.getRight().toString();
    	xmsg.setApp(botNode.path("name").asText());
    	XMessageDAO currentMessageToBeInserted = XMessageDAOUtils.convertXMessageToDAO(xmsg);
    
    	String campaignId;
    	if(botNode.path("logic").get(0).path("adapter").findValue("id") != null) {
			campaignId = botNode.path("logic").get(0).path("adapter").findValue("id").asText();
		} else {
			campaignId = botNode.path("logic").get(0).path("adapter").asText();
		}
    	
    	xMsgRepo.insert(currentMessageToBeInserted)
            .doOnError(genericError("Error in inserting current message"))
            .subscribe(xMessageDAO -> {
//            	SenderReceiverInfo from = xmsg.getFrom();
//            	from.setUserID("admin");
//            	xmsg.setFrom(from);
            	
            	SenderReceiverInfo to = SenderReceiverInfo.builder().userID(xmsg.getFrom().getUserID()).build();
            	xmsg.setTo(to);
            	xmsg.setAdapterId(campaignId);
            	XMessagePayload payload = XMessagePayload.builder().text(message).build();
            	xmsg.setPayload(payload);
            	sendEventToOutboundKafka(xmsg);
            });
    }
    
    /**
     * Process Bot Message - send message to orchestrator for processing
     * @param xmsg
     * @param appName
     */
    private void processBotMessage(XMessage xmsg, String appName) {
    	xmsg.setApp(appName);
        XMessageDAO currentMessageToBeInserted = XMessageDAOUtils.convertXMessageToDAO(xmsg);
    	if (isCurrentMessageNotAReply(xmsg)) {
            String whatsappId = xmsg.getMessageId().getChannelMessageId();
            getLatestXMessage(xmsg.getFrom().getUserID(), XMessage.MessageState.REPLIED)
                    .doOnError(genericError("Error in getting last message"))
                    .subscribe(new Consumer<XMessageDAO>() {
                        @Override
                        public void accept(XMessageDAO previousMessage) {
                            previousMessage.setMessageId(whatsappId);
                            xMsgRepo.save(previousMessage)
                                    .doOnError(genericError("Error in saving previous message"))
                                    .subscribe(new Consumer<XMessageDAO>() {
                                        @Override
                                        public void accept(XMessageDAO updatedPreviousMessage) {
                                            xMsgRepo.insert(currentMessageToBeInserted)
                                                    .doOnError(genericError("Error in inserting current message"))
                                                    .subscribe(insertedMessage -> {
                                                        sendEventToKafka(xmsg);
                                                    });
                                        }
                                    });
                        }
                    });
        } else {
            xMsgRepo.insert(currentMessageToBeInserted)
                    .doOnError(genericError("Error in inserting current message"))
                    .subscribe(xMessageDAO -> {
                        sendEventToKafka(xmsg);
                    });
        }
    }

    /**
     * Validate Bot & return app name as pair of 
     	* Pair<is bot valid: true, Bot Name>
     	* Pair<is bot valid: false, Pair<Bot Json, Bot Invalid Error Message>>
     * @param botName
     * @return
     */
    private Mono<Pair<Boolean, Object>> validateBot(String botName) {
    	try {
    		return botService.getBotFromName(botName)
            		.flatMap(new Function<JsonNode, Mono<? extends Pair<Boolean, Object>>>() {
                        @Override
                        public Mono<Pair<Boolean, Object>> apply(JsonNode botNode) {
                        	log.info("validateBot botNode:"+botNode);
                        	String appName1 = null;
                        	if(botNode != null && !botNode.path("result").isEmpty()) {
                        		String botValid= BotUtil.getBotValidFromJsonNode(botNode.path("result").path("data").get(0));
                            	if(!botValid.equals("true")) {
                            		return Mono.just(Pair.of(false, Pair.of(botNode.path("result").path("data").get(0), botValid)));
    							}
                            	
                            	try {
                            		JsonNode name = botNode.path("result").path("data").get(0).path("name");
                                	appName1 = name.asText();
                            	} catch (Exception e) {
                            		log.error("Exception in validateBot: "+e.getMessage());
                            	}	
                        	}
                        	
                            return Mono.just(Pair.of(true, (appName1 == null || appName1.isEmpty()) ? "finalAppName" : appName1));
                        }
                    });
    	} catch (Exception e) {
    		log.error("Exception in validateBot: "+e.getMessage());
    		return Mono.just(Pair.of(true, "finalAppName"));
    	}
    }


    private Consumer<Throwable> genericError(String s) {
        return c -> {
            log.error(s + "::" + c.getMessage());
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

        if(xmsg.getMessageState().equals(XMessage.MessageState.SENT)
                || xmsg.getMessageState().equals(XMessage.MessageState.DELIVERED)
                || xmsg.getMessageState().equals(XMessage.MessageState.READ)) {
            kafkaProducer.send(topicReport, xmessage);
        } else {
            kafkaProducer.send(topicSuccess, xmessage);
        }

    }
    
    private void sendEventToOutboundKafka(XMessage xmsg) {
        String xmessage = null;
        try {
            xmessage = xmsg.toXML();
            log.info("xmessage: "+xmessage);
        } catch (JAXBException e) {
//            kafkaProducer.send(topicFailure, inboundMessage.toString());
        }
        kafkaProducer.send(topicOutbound, xmessage);
    }

    private Mono<XMessageDAO> getLatestXMessage(String userID, XMessage.MessageState messageState) {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1L);
        return xMsgRepo
                .findAllByFromIdAndTimestampAfter(userID, yesterday)
                .doOnError(genericError(String.format("Unable to find previous Message for userID %s", userID)))
                .collectList()
                .map(xMessageDAOS -> {
                	if (xMessageDAOS.size() > 0) {
                        List<XMessageDAO> filteredList = new ArrayList<>();
                        for (XMessageDAO xMessageDAO : xMessageDAOS) {
                        	if (xMessageDAO.getMessageState().equals(messageState.name())) {
                            	filteredList.add(xMessageDAO);
                            }
                                
                        }
                        if (filteredList.size() > 0) {
                            filteredList.sort(Comparator.comparing(XMessageDAO::getTimestamp));
                        }

                        return xMessageDAOS.get(0);
                    }
                    return new XMessageDAO();
                });
    }

    /**
     * Get App name as pair of 
     	* Pair<is bot valid: true, Pair<Is Bot Check Needed, Bot Name>>
     	* Pair<is bot valid: false, Pair<Bot Json, Bot Invalid Error Message>> 
     * @param text
     * @param from
     * @return
     */
    private Mono<Pair<Boolean, Object>> getAppName(String text, SenderReceiverInfo from) {
    	LocalDateTime yesterday = LocalDateTime.now().minusDays(1L);
        if (text != null && text.equals("")) {
            try {
            	return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Pair<Boolean, Object>>() {
                    @Override
                    public Pair<Boolean, Object> apply(XMessageDAO xMessageLast) {
                    	return Pair.of(true, Pair.of(true, xMessageLast.getApp()));
                    }
                }).doOnError(genericError("Error in getting latest xmessage"));
            } catch (Exception e2) {
                return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Pair<Boolean, Object>>() {
                    @Override
                    public Pair<Boolean, Object> apply(XMessageDAO xMessageLast) {
                        return Pair.of(true, Pair.of(true, xMessageLast.getApp()));
                    }
                }).doOnError(genericError("Error in getting latest xmessage - catch"));
            }
        } else {
            try {
            	return botService.getBotFromStartingMessage(text)
                		.flatMap(new Function<JsonNode, Mono<? extends Pair<Boolean, Object>>>() {
                            @Override
                            public Mono<Pair<Boolean, Object>> apply(JsonNode botNode) {
                            	log.info("botNode:"+botNode);
                            	String appName1 = null;
                            	if(botNode != null && !botNode.path("result").isEmpty()
                                        && !botNode.path("result").path("data").isEmpty()) {
                            		String botValid= BotUtil.getBotValidFromJsonNode(botNode.path("result").path("data"));
                                	if(!botValid.equals("true")) {
                                		return Mono.just(Pair.of(false, Pair.of(botNode.path("result").path("data"), botValid)));
    								}
                                	JsonNode name = botNode.path("result").path("data").path("name");
    								appName1 = name.asText();
                            	} else {
                            		appName1 = null;
                            	}
                            	if (appName1 == null || appName1.equals("")) {
                            		log.info("getLatestXMessage user id 1: "+from.getUserID()+", yesterday: "+yesterday+", status: "+XMessage.MessageState.SENT.name());
                                    try {
                                        return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Pair<Boolean, Object>>() {
                                            @Override
                                            public Pair<Boolean, Object> apply(XMessageDAO xMessageLast) {
                                            	log.info("getApp 1: "+xMessageLast.getApp());
                                                return Pair.of(true, Pair.of(true, (xMessageLast.getApp() == null || xMessageLast.getApp().isEmpty()) ? "finalAppName" : xMessageLast.getApp()));
                                            }
                                        }).doOnError(genericError("Error in getting latest xmessage when app name empty"));
                                    } catch (Exception e2) {
                                        return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Pair<Boolean, Object>>() {
                                            @Override
                                            public Pair<Boolean, Object> apply(XMessageDAO xMessageLast) {
                                            	log.info("getApp 2: "+xMessageLast.getApp());
                                                return Pair.of(true, Pair.of(true, (xMessageLast.getApp() == null || xMessageLast.getApp().isEmpty()) ? "finalAppName" : xMessageLast.getApp()));
                                            }
                                        }).doOnError(genericError("Error in getting latest xmessage when app name empty - catch"));
                                    }
                                }
                                return Mono.just(Pair.of(true, Pair.of(false, (appName1 == null || appName1.isEmpty()) ? "finalAppName" : appName1)));
                            }
                        });
            } catch (Exception e) {
            	log.error("Exception in getCampaignFromStartingMessage :"+e.getMessage());
            	log.info("getLatestXMessage user id 2: "+from.getUserID()+", yesterday: "+yesterday+", status: "+XMessage.MessageState.SENT.name());
                try {
                    return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Pair<Boolean, Object>>() {
                        @Override
                        public Pair<Boolean, Object> apply(XMessageDAO xMessageLast) {
                        	log.info("getApp 21: "+xMessageLast.getApp());
                            return Pair.of(true, Pair.of(true, xMessageLast.getApp()));
                        }
                    }).doOnError(genericError("Error in getting latest xmessage when exception in getCampaignFromStartingMessage"));
                } catch (Exception e2) {
                	Pair<Boolean, Object> message1 = Pair.of(false, null);
                    return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Pair<Boolean, Object>>() {
                        @Override
                        public Pair<Boolean, Object> apply(XMessageDAO xMessageLast) {
                        	log.info("getApp 22: "+xMessageLast.getApp());
                        	return Pair.of(true, Pair.of(true, xMessageLast.getApp()));
                        }
                    }).doOnError(genericError("Error in getting latest xmessage when exception in getCampaignFromStartingMessage - catch"));
                }
            }
        }
    }
    
    private Mono<XMessageDAO> getLatestXMessage(String userID, LocalDateTime yesterday, String messageState) {
    	XMessageDAO xMessageDAO = (XMessageDAO) redisCacheService.getXMessageDaoCache(userID);
	  	if(xMessageDAO != null) {
	  		log.info("Redis xMsgDao id: "+xMessageDAO.getId()+", dao app: "+xMessageDAO.getApp()
			+", From id: "+xMessageDAO.getFromId()+", user id: "+xMessageDAO.getUserId()
			+", status: "+xMessageDAO.getMessageState()+", timestamp: "+xMessageDAO.getTimestamp());
	  		return Mono.just(xMessageDAO);
	  	}
        
    	return xMsgRepo.findAllByUserIdAndTimestampAfter(userID, yesterday)
                .collectList()
                .map(new Function<List<XMessageDAO>, XMessageDAO>() {
                    @Override
                    public XMessageDAO apply(List<XMessageDAO> xMessageDAOS) {
                    	log.info("xMsgDaos size: "+xMessageDAOS.size()+", messageState.name: "+XMessage.MessageState.SENT.name());
                        if (xMessageDAOS.size() > 0) {
                            List<XMessageDAO> filteredList = new ArrayList<>();
                            for (XMessageDAO xMessageDAO : xMessageDAOS) {
                            	if (xMessageDAO.getMessageState().equals(XMessage.MessageState.SENT.name())
					                || xMessageDAO.getMessageState().equals(XMessage.MessageState.REPLIED.name())) {
                            		filteredList.add(xMessageDAO);
                            	}
                                    
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

    private Mono<XMessageDAO> getLatestXMessageForReport(String userID, LocalDateTime yesterday, String messageState) {
        return xMsgRepo.findAllByUserIdAndTimestampAfter(userID, yesterday)
                .collectList()
                .map(new Function<List<XMessageDAO>, XMessageDAO>() {
                    @Override
                    public XMessageDAO apply(List<XMessageDAO> xMessageDAOS) {
                        if (xMessageDAOS.size() > 0) {
                            List<XMessageDAO> filteredList = new ArrayList<>();
                            for (XMessageDAO xMessageDAO : xMessageDAOS) {
                                if (xMessageDAO.getMessageState().equals(XMessage.MessageState.SENT.name())
                                        || xMessageDAO.getMessageState().equals(XMessage.MessageState.REPLIED.name())) {
                                    filteredList.add(xMessageDAO);
                                }

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
