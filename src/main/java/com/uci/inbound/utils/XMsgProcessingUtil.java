package com.uci.inbound.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import reactor.core.publisher.Mono;

import jakarta.xml.bind.JAXBException;

import java.time.LocalDateTime;
import java.util.*;
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
                        /* If Message State is replied, process as message sent
                        * Else if message state is sent, print error message
                        * Else if message state is deliverd/read, send the event to report topic
                        * Else print invalid message error */
                        if(xmsg.getMessageState().equals(XMessage.MessageState.REPLIED)) {
                            getAppName(xmsg.getPayload().getText(), xmsg.getFrom())
                                    .subscribe(result -> {
                                        log.info("getAppName response:" + result);
                                        /* If bot is invalid, send error message to outbound, else process message */
                                        if (result.get("botExists").toString().equals("false")) {
                                            log.info("Bot is invalid");
                                            processInvalidBotMessage(xmsg, (ObjectNode) result.get("botNode"), result.get("errorMsg").toString());
                                        } else {
                                            /* If bot check required, validate bot, else process message */
                                            if (result.get("botCheckRequired").toString().equals("true")) {
                                                log.info("Bot check required.");
                                                validateBot(result.get("appName").toString())
                                                        .subscribe(res -> {
                                                            log.info("ValidateBot response:" + res);
                                                            /* If bot is invalid, send error message to outbound, else process message */
                                                            if (!res.get("botValid").toString().equals("true")) {
                                                                log.info("Bot is invalid");
                                                                processInvalidBotMessage(xmsg, (ObjectNode) res.get("botNode"), res.get("errorMsg").toString());
                                                            } else {
                                                                log.info("Process bot message");
                                                                String appName = res.get("appName").toString();
                                                                processBotMessage(xmsg, res.get("appName").toString(), result.get("sessionId"), result.get("ownerOrgId"), result.get("ownerId"), result.get("botUuid"));
                                                            }
                                                        });
                                            } else {
                                                log.info("Process bot message");
                                                String appName = result.get("appName").toString();
                                                processBotMessage(xmsg, appName, result.get("sessionId"), result.get("ownerOrgId"), result.get("ownerId"), result.get("botUuid"));
                                            }
                                        }
                                    });
                        } else if(xmsg.getMessageState().equals(XMessage.MessageState.DELIVERED)
                                || xmsg.getMessageState().equals(XMessage.MessageState.READ)) {
                            getSentXMessageForReport(xmsg.getMessageState(), xmsg.getMessageId().getChannelMessageId(), xmsg.getFrom().getUserID())
                                    .doOnError(genericError("Exception in sent latest xMessage for received receipt request."))
                                    .subscribe(dataMap -> {
                                        if(dataMap.get("proceed") != null && dataMap.get("proceed").toString().equals("true")) {
                                            XMessageDAO xMessageLast = (XMessageDAO) dataMap.get("xMessageDao");
                                            if(xMessageLast.getApp() != null && !xMessageLast.getApp().isEmpty()) {
                                                log.info("App name found: "+xMessageLast.getApp()+" for user id: "+xmsg.getFrom().getUserID());
                                                xmsg.setApp(xMessageLast.getApp());
                                                xmsg.setSessionId(xMessageLast.getSessionId());
                                                xmsg.setOwnerOrgId(xMessageLast.getOwnerOrgId());
                                                xmsg.setOwnerId(xMessageLast.getOwnerId());
                                                xmsg.setBotId(xMessageLast.getBotUuid());
                                                XMessageDAO currentMessageToBeInserted = XMessageDAOUtils.convertXMessageToDAO(xmsg);
                                                xMsgRepo.insert(currentMessageToBeInserted)
                                                        .doOnError(genericError("Error in inserting current message"))
                                                        .subscribe(xMessageDAO -> {
                                                            sendEventToKafka(xmsg);
                                                        });
                                            } else {
                                                log.error("App name not found for user id: "+xmsg.getFrom().getUserID()+" for sent/delivered/read message");
                                            }
                                        } else {
                                            if(dataMap.get("message") != null && !dataMap.get("message").toString().isEmpty()) {
                                                log.error("Error message : "+dataMap.get("message").toString());
                                            }
                                        }
                                    });
                        } else {
                            log.error("Error message: Invalid message");
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
     * @param botNode
     * @param message
     */
    private void processInvalidBotMessage(XMessage xmsg, ObjectNode botNode, String message) {
        xmsg.setBotId(UUID.fromString(botNode.path("id").asText()));
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
    private void processBotMessage(XMessage xmsg, String appName, Object sessionId, Object ownerOrgId, Object ownerId, Object botUuid) {
    	xmsg.setApp(appName);
        if(sessionId != null && !sessionId.toString().isEmpty()) {
            xmsg.setSessionId(UUID.fromString(sessionId.toString()));
        }
        if(ownerOrgId != null && !ownerOrgId.toString().isEmpty()) {
            xmsg.setOwnerOrgId(ownerOrgId.toString());
        }
        if(ownerId != null && !ownerId.toString().isEmpty()) {
            xmsg.setOwnerId(ownerId.toString());
        }
        if(botUuid != null && !botUuid.toString().isEmpty()) {
            xmsg.setBotId(UUID.fromString(botUuid.toString()));
        }
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
    private Mono<Map<String, Object>> validateBot(String botName) {
    	Map<String, Object> dataMap = new HashMap<>();
        try {
    		return botService.getBotFromName(botName)
            		.flatMap(new Function<JsonNode, Mono<? extends Map<String, Object>>>() {
                        @Override
                        public Mono<Map<String, Object>> apply(JsonNode botNode) {
                        	log.info("validateBot botNode:"+botNode);
                        	String appName1 = null;
                        	if(botNode != null && !botNode.path("result").isEmpty()) {
                        		String botValid= BotUtil.getBotValidFromJsonNode(botNode.path("result").path("data").get(0));
                            	if(!botValid.equals("true")) {
                                    dataMap.put("botValid", "false");
                                    dataMap.put("botNode", botNode.path("result").path("data").get(0));
                                    dataMap.put("errorMsg", botValid);
                                    return Mono.just(dataMap);
    							}
                            	
                            	try {
                            		JsonNode name = botNode.path("result").path("data").get(0).path("name");
                                	appName1 = name.asText();
                            	} catch (Exception e) {
                            		log.error("Exception in validateBot: "+e.getMessage());
                            	}	
                        	}

                            dataMap.put("botValid", "true");
                            dataMap.put("appName", (appName1 == null || appName1.isEmpty()) ? "finalAppName" : appName1);
                            return Mono.just(dataMap);
                        }
                    });
    	} catch (Exception e) {
            log.error("Exception in validateBot: "+e.getMessage());
            dataMap.put("botValid", "true");
            dataMap.put("appName", "finalAppName");
            return Mono.just(dataMap);
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

        if(xmsg.getMessageState().equals(XMessage.MessageState.REPLIED)) {
            kafkaProducer.send(topicSuccess, xmessage);
        } else {
            kafkaProducer.send(topicReport, xmessage);
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
    private Mono<Map<String, Object>> getAppName(String text, SenderReceiverInfo from) {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1L);
        if (text != null && text.equals("")) {
            try {
            	return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(XMessageDAO xMessageLast) {
                        return createExistingConversationData("true", "true", xMessageLast);
                    }
                }).doOnError(genericError("Error in getting latest xmessage"));
            } catch (Exception e2) {
                return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(XMessageDAO xMessageLast) {
                        return createExistingConversationData("true", "true", xMessageLast);
                    }
                }).doOnError(genericError("Error in getting latest xmessage - catch"));
            }
        } else {
            try {
            	return botService.getBotFromStartingMessage(text)
                		.flatMap(new Function<JsonNode, Mono<? extends Map<String, Object>>>() {
                            @Override
                            public Mono<Map<String, Object>> apply(JsonNode botNode) {
                            	log.info("botNode:"+botNode);
                            	String appName1 = null;
                                String ownerId = null;
                                String ownerOrgId = null;
                                String botUuid = null;
                            	if(botNode != null && !botNode.isEmpty()) {
                            		String botValid= BotUtil.getBotValidFromJsonNode(botNode);
                                    if(!botValid.equals("true")) {
                                        Map<String, Object> dataMap = new HashMap<>();
                                        dataMap.put("botExists", "false");
                                        dataMap.put("botNode", botNode);
                                        dataMap.put("errorMsg", botValid);
                                        return Mono.just(dataMap);
    								}
                                	JsonNode name = botNode.path("name");
    								appName1 = name.asText();
                                    ownerOrgId = botNode.path("ownerOrgID") != null && !botNode.path("ownerOrgID").asText().equals("null") ? botNode.path("ownerOrgID").asText() : null;
                                    ownerId = botNode.path("ownerID") != null && !botNode.path("ownerID").asText().equals("null") ? botNode.path("ownerID").asText() : null;
                            	    botUuid = botNode.path("id") != null && !botNode.path("id").asText().isEmpty() ? botNode.path("id").asText() : null;
                                } else {
                            		appName1 = null;
                            	}
                            	if (appName1 == null || appName1.equals("")) {
                            		log.info("getLatestXMessage user id 1: "+from.getUserID()+", yesterday: "+yesterday+", status: "+XMessage.MessageState.SENT.name());
                                    try {
                                        return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Map<String, Object>>() {
                                            @Override
                                            public Map<String, Object> apply(XMessageDAO xMessageLast) {
                                            	log.info("getApp 1: "+xMessageLast.getApp());
                                                return createExistingConversationData("true", "true", xMessageLast);
                                            }
                                        }).doOnError(genericError("Error in getting latest xmessage when app name empty"));
                                    } catch (Exception e2) {
                                        return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Map<String, Object>>() {
                                            @Override
                                            public Map<String, Object> apply(XMessageDAO xMessageLast) {
                                            	log.info("getApp 2: "+xMessageLast.getApp());
                                                return createExistingConversationData("true", "true", xMessageLast);
                                            }
                                        }).doOnError(genericError("Error in getting latest xmessage when app name empty - catch"));
                                    }
                                }
                                return Mono.just(createNewConversationData("true", "false", appName1, ownerOrgId, ownerId, botUuid));
                            }
                        });
            } catch (Exception e) {
            	log.error("Exception in getCampaignFromStartingMessage :"+e.getMessage());
            	log.info("getLatestXMessage user id 2: "+from.getUserID()+", yesterday: "+yesterday+", status: "+XMessage.MessageState.SENT.name());
                try {
                    return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Map<String, Object>>() {
                        @Override
                        public Map<String, Object> apply(XMessageDAO xMessageLast) {
                        	log.info("getApp 21: "+xMessageLast.getApp());
                            return createExistingConversationData("true", "true", xMessageLast);
                        }
                    }).doOnError(genericError("Error in getting latest xmessage when exception in getCampaignFromStartingMessage"));
                } catch (Exception e2) {
                	return getLatestXMessage(from.getUserID(), yesterday, XMessage.MessageState.SENT.name()).map(new Function<XMessageDAO, Map<String, Object>>() {
                        @Override
                        public Map<String, Object> apply(XMessageDAO xMessageLast) {
                        	log.info("getApp 22: "+xMessageLast.getApp());
                            return createExistingConversationData("true", "true", xMessageLast);
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

    private Mono<Map<String, Object>> getSentXMessageForReport(XMessage.MessageState messageState, String messageId, String userId) {
        return xMsgRepo.findAllByMessageIdAndUserIdInAndFromIdIn(messageId, List.of("admin", userId), List.of("admin", userId)).collectList()
                .map(new Function<List<XMessageDAO>, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(List<XMessageDAO> xMessageDAOS) {
                        Map<String, Object> dataMap = new HashMap<>();
                        List<XMessageDAO> filteredList = new ArrayList<>();
                        if (xMessageDAOS.size() > 0) {
                            xMessageDAOS.forEach(dao -> {
                                log.info("dao: "+dao.getId());
                                if(dao.getMessageState().equals(messageState.name())) {
                                    dataMap.put("proceed", "false");
                                    dataMap.put("message", "Receipt for messageState already exists.");
                                    return;
                                }
                                if(dao.getMessageState().equals(XMessage.MessageState.SENT.name())) {
                                    filteredList.add(dao);
                                }
                            });
                        }

                        if(dataMap.get("proceed") != null &&
                                dataMap.get("proceed").toString().equals("false")) {
                            // use data map generated in the for loop
                        } else if(filteredList.size() > 0) {
                            dataMap.put("proceed", "true");
                            dataMap.put("xMessageDao", filteredList.get(0));
                        } else {
                            dataMap.put("proceed", "false");
                            dataMap.put("message", "Sent Message for message id does not exists.");
                        }

                        return dataMap;
                    }
                });
    }

    /**
     * Create Existing Conversation Related Map Data
     * @param botExists
     * @param botCheckRequired
     * @param xMessageLast
     * @return
     */
    private Map<String, Object> createExistingConversationData(String botExists, String botCheckRequired, XMessageDAO xMessageLast) {
        Map<String, Object> dataMap = new HashMap();
        dataMap.put("botExists", botExists);
        dataMap.put("botCheckRequired", botCheckRequired);
        dataMap.put("appName", getXMessageAppName(xMessageLast));
        dataMap.put("sessionId", getXMessageSessionId(xMessageLast));
        dataMap.put("ownerOrgId", getXMessageOwnerOrgId(xMessageLast));
        dataMap.put("ownerId", getXMessageOwnerId(xMessageLast));
        dataMap.put("botUuid", getXMessageBotUuid(xMessageLast));

        return dataMap;
    }

    /**
     * Create New Conversation Related Map Data
     * @param botExists
     * @param botCheckRequired
     * @param appName
     * @return
     */
    private Map<String, Object> createNewConversationData(String botExists, String botCheckRequired, String appName, String ownerOrgId, String ownerId, String botUuid) {
        Map<String, Object> dataMap = new HashMap();
        dataMap.put("botExists", botExists);
        dataMap.put("botCheckRequired", botCheckRequired);
        dataMap.put("appName", (appName == null || appName.isEmpty()) ? "finalAppName" : appName);
        dataMap.put("sessionId", newConversationSessionId().toString());
        dataMap.put("ownerOrgId", ownerOrgId);
        dataMap.put("ownerId", ownerId);
        dataMap.put("botUuid", botUuid);
        return dataMap;
    }

    /**
     * Get App name from XMessage Dao
     * @param xMessageDAO
     * @return
     */
    private String getXMessageAppName(XMessageDAO xMessageDAO) {
        return (xMessageDAO.getApp() == null || xMessageDAO.getApp().isEmpty()) ? "finalAppName" : xMessageDAO.getApp();
    }

    /**
     * Get Owner UUID as string from XMessage Dao
     * @param xMessageDAO
     * @return
     */
    private String getXMessageBotUuid(XMessageDAO xMessageDAO) {
        return xMessageDAO.getBotUuid() != null ? xMessageDAO.getBotUuid().toString() : null;
    }

    /**
     * Get Session UUID as string from XMessage Dao
     * @param xMessageDAO
     * @return
     */
    private String getXMessageSessionId(XMessageDAO xMessageDAO) {
        return xMessageDAO.getSessionId() != null ? xMessageDAO.getSessionId().toString() : "";
    }

    /**
     * Get Owner UUID as string from XMessage Dao
     * @param xMessageDAO
     * @return
     */
    private String getXMessageOwnerId(XMessageDAO xMessageDAO) {
        return xMessageDAO.getOwnerId() != null ? xMessageDAO.getOwnerId() : "";
    }

    /**
     * Get Owner Org id from XMessage Dao
     * @param xMessageDAO
     * @return
     */
    private String getXMessageOwnerOrgId(XMessageDAO xMessageDAO) {
        return xMessageDAO.getOwnerOrgId() != null ? xMessageDAO.getOwnerOrgId() : "";
    }

    /**
     * New Conversation Session UUID
     * @return
     */
    private UUID newConversationSessionId() {
        return UUID.randomUUID();
    }
}
