package com.uci.inbound.services;

import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.model.HttpApiResponse;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.XMessage;
import messagerosa.xml.XMessageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Service
public class XmsgHistoryService {
    @Autowired
    private XMessageRepository xMsgRepo;
    @Autowired
    private RedisCacheService redisCacheService;


    /**
     * Preparing conversation history response
     * @param xMessageDAOList
     * @return
     */
    public Map<String, Object> prepareConversationHistoryResponse(List<XMessageDAO> xMessageDAOList) {
        Map<String, Object> result = new HashMap<>();
        if (xMessageDAOList == null) {
            result.put("total", 0);
            result.put("records", "");
            return result;
        }
        List<Map<String, Object>> xMessageDAOListNew = filterConversationHistory(xMessageDAOList);
        result.put("total", xMessageDAOListNew.size());
        result.put("records", xMessageDAOListNew);
        if (xMessageDAOListNew.size() < 5) {
            log.info("Response :" + result);
        } else {
            log.info("Response :" + xMessageDAOListNew.size());
        }
        return result;
    }

    /**
     * Get Conversation History from Cassandra
     * According to UserId and BotId and Date Range
     * @param userId
     * @param botName
     * @param provider
     * @param startTimestamp
     * @param endTimestamp
     * @param response
     * @param conversationHistoryRedisKey
     * @return
     */
    public Mono<Object> getConversationHistoryFromCassandra(String userId, String botName, String provider, Timestamp startTimestamp, Timestamp endTimestamp, HttpApiResponse response, String conversationHistoryRedisKey) {
        Pageable paging = (Pageable) CassandraPageRequest.of(PageRequest.of(0, 1000),
                null
        );
        return xMsgRepo.findAllByUserIdInAndFromIdInAndAppAndTimestampAfterAndTimestampBeforeAndProvider(paging, List.of("admin", userId), List.of("admin", userId), botName, startTimestamp, endTimestamp, provider)
                .map(new Function<Slice<XMessageDAO>, Object>() {
                    @Override
                    public Object apply(Slice<XMessageDAO> xMessageDAOSlice) {
                        Map<String, Object> result = new HashMap<>();
                        List<XMessageDAO> xMessageDAOList = new ArrayList<>();

                        if (xMessageDAOSlice != null && !xMessageDAOSlice.isEmpty()) {
                            xMessageDAOList.addAll(xMessageDAOSlice.getContent());
                        }
                        if (!xMessageDAOList.isEmpty()) {
                            redisCacheService.setConversationHistoryCache(conversationHistoryRedisKey, xMessageDAOList);
                        }
//                                                    List<Map<String, Object>> xMessageDAOListNew = filterConversationHistory(xMessageDAOS.getContent());
//                                                    result.put("total", xMessageDAOListNew.size());
//                                                    result.put("records", xMessageDAOListNew);
//                                                    if (xMessageDAOListNew.size() < 5) {
//                                                        log.info("Response :" + result);
//                                                    } else {
//                                                        log.info("Response :" + xMessageDAOListNew.size());
//                                                    }
                        sortList(xMessageDAOList, "desc");
                        response.setResult(prepareConversationHistoryResponse(xMessageDAOList));
                        return response;
                    }
                });
    }

    /**
     * Filter conversation history data
     * which gets from cassandra
     * @param xMessageDAOList
     * @return
     */
    public List<Map<String, Object>> filterConversationHistory(List<XMessageDAO> xMessageDAOList) {
        List<Map<String, Object>> list = new ArrayList<>();
//        xMessageDAOList.sort(Comparator.comparing(XMessageDAO::getTimestamp).reversed());
        xMessageDAOList.forEach(xMessageDAO -> {
            Map<String, Object> daoMap = new HashMap<>();
            daoMap.put("id", xMessageDAO.getId());
            daoMap.put("messageId", xMessageDAO.getMessageId());
            daoMap.put("messageState", xMessageDAO.getMessageState());
            daoMap.put("channel", xMessageDAO.getChannel());
            daoMap.put("provider", xMessageDAO.getProvider());
            daoMap.put("fromId", xMessageDAO.getFromId());
            daoMap.put("userId", xMessageDAO.getUserId());
            daoMap.put("ownerId", xMessageDAO.getOwnerId());
            daoMap.put("ownerOrgId", xMessageDAO.getOwnerOrgId());
            daoMap.put("sessionId", xMessageDAO.getSessionId());
            daoMap.put("botUuid", xMessageDAO.getBotUuid());
            daoMap.put("tags", xMessageDAO.getTags());
//            daoMap.put("xMessage", xMessageDAO.getXMessage());
//            daoMap.put("timestamp", xMessageDAO.getTimestamp());
            try {

                String xMessage = xMessageDAO.getXMessage();
                XMessage currentXmsg = XMessageParser.parse(new ByteArrayInputStream(xMessage.getBytes()));
                Map<String, Object> payloadMap = new HashMap<>();
                if (currentXmsg.getPayload().getText() != null) {
                    payloadMap.put("text", currentXmsg.getPayload().getText());
                }
                if (currentXmsg.getPayload().getMedia() != null) {
                    payloadMap.put("media", currentXmsg.getPayload().getMedia());
                }
                if (currentXmsg.getPayload().getButtonChoices() != null) {
                    payloadMap.put("buttonChoices", currentXmsg.getPayload().getButtonChoices());
                }
                if (currentXmsg.getPayload().getLocation() != null) {
                    payloadMap.put("location", currentXmsg.getPayload().getLocation());
                }
                if (currentXmsg.getPayload().getContactCard() != null) {
                    payloadMap.put("contactCard", currentXmsg.getPayload().getContactCard());
                }
                daoMap.put("payload", payloadMap);
                if (xMessageDAO.getMessageState().equalsIgnoreCase(XMessage.MessageState.SENT.name())) {
                    daoMap.put("sentTimestamp", xMessageDAO.getTimestamp().toString());
                } else {
                    daoMap.put("sentTimestamp", null);
                }
                if (xMessageDAO.getMessageState().equalsIgnoreCase(XMessage.MessageState.REPLIED.name())) {
                    daoMap.put("repliedTimestamp", xMessageDAO.getTimestamp().toString());
                } else {
                    daoMap.put("repliedTimestamp", null);
                }
                if (xMessageDAO.getMessageState().equalsIgnoreCase(XMessage.MessageState.DELIVERED.name())) {
                    daoMap.put("deliveredTimestamp", xMessageDAO.getTimestamp().toString());
                } else {
                    daoMap.put("deliveredTimestamp", null);
                }
                if (xMessageDAO.getMessageState().equalsIgnoreCase(XMessage.MessageState.READ.name())) {
                    daoMap.put("readTimestamp", xMessageDAO.getTimestamp().toString());
                } else {
                    daoMap.put("readTimestamp", null);
                }
            } catch (Exception ex) {
                log.error("Exception when fetching payload: " + ex.getMessage());
            }

            list.add(daoMap);
        });
        return list;
    }

    /**
     * Sorting List of XMessageDao
     * @param xMessageDAOList
     * @param orderBy
     */
    public void sortList(List<XMessageDAO> xMessageDAOList, String orderBy) {
        if (orderBy != null && orderBy.equalsIgnoreCase("desc")) {
            Collections.sort(xMessageDAOList, Comparator.comparing(XMessageDAO::getTimestamp).reversed());
        } else {
            Collections.sort(xMessageDAOList, Comparator.comparing(XMessageDAO::getTimestamp));
        }
    }

}
