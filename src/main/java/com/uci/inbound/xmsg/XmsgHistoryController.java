package com.uci.inbound.xmsg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.BotService;
import com.uci.utils.model.HttpApiResponse;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.XMessage;
import messagerosa.xml.XMessageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

@Slf4j
@RestController
@RequestMapping("/xmsg")
public class XmsgHistoryController {

    private static final char[] HEX_CODE = "0123456789ABCDEF".toCharArray();

    private static final String DEFAULT_CURSOR_MARK = "-1";

    @Autowired
    private XMessageRepository xMsgRepo;

    @Autowired
    private BotService botService;

    enum MessageState {
        SENT,
        DELIVERED,
        READ;
    }

    /**
     * User History
     * @param userId
     * @param startDate
     * @param endDate
     * @param provider
     * @param tag
     * @return
     */
    @RequestMapping(value = "/history", method = RequestMethod.GET, produces = {"application/json", "text/json"})
    public Mono<Object> getHistory(@RequestParam(value = "userId") String userId,
                                      @RequestParam("startDate") String startDate,
                                      @RequestParam("endDate") String endDate,
                                      @RequestParam(value = "provider") String provider,
                                      @RequestParam(value = "tag", required = false) String tag) {
        try {
            HttpApiResponse response = HttpApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .path("/xmsg/history")
                    .build();

            Pageable paging = (Pageable) CassandraPageRequest.of(PageRequest.of(0, 1000),
                    null
            );

            DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
            Date startD = formatter.parse(startDate);
            Date endD = formatter.parse(endDate);
            Timestamp startTimestamp = new Timestamp(startD.getTime());
            Timestamp endTimestamp = new Timestamp(endD.getTime());
            endTimestamp.setHours(23);
            endTimestamp.setMinutes(59);
            endTimestamp.setSeconds(59);

            return findUserQuery(paging, userId, startTimestamp, endTimestamp, provider.toLowerCase(), tag)
                    .map(new Function<Slice<XMessageDAO>, Object>() {
                        @Override
                        public Object apply(Slice<XMessageDAO> xMessageDAOS) {
                            Map<String, Object> result = new HashMap<>();
                            List<Map<String, Object>> xMessageDAOListNew = filterMessageState(xMessageDAOS.getContent());
                            result.put("total", xMessageDAOListNew.size());
                            result.put("records", xMessageDAOListNew);
                            response.setResult(result);
                            return response;
                        }
                    });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Bot History Dump
     * @param botId
     * @param startDate
     * @param endDate
     * @param provider
     * @param tag
     * @return
     */
    @RequestMapping(value = "/history/dump", method = RequestMethod.GET, produces = {"application/json", "text/json"})
    public Object getHistoryDump(@RequestParam(value = "botId") String botId,
                                      @RequestParam("startDate") String startDate,
                                      @RequestParam("endDate") String endDate,
                                      @RequestParam(value = "provider") String provider,
                                      @RequestParam(value = "tag", required = false) String tag) {
        try {
            HttpApiResponse response = HttpApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .path("/xmsg/history/dump")
                    .build();

            Pageable paging = (Pageable) CassandraPageRequest.of(PageRequest.of(0, 10000000),
                    null
            );

            DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
            Date startD = formatter.parse(startDate);
            Date endD = formatter.parse(endDate);
            Timestamp startTimestamp = new Timestamp(startD.getTime());
            Timestamp endTimestamp = new Timestamp(endD.getTime());
            endTimestamp.setHours(23);
            endTimestamp.setMinutes(59);
            endTimestamp.setSeconds(59);
            long differenceInDays = ((endTimestamp.getTime()-startTimestamp.getTime())/ (1000 * 60 * 60 * 24))%365;

            if(differenceInDays > 15) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
                response.setMessage("Start & end date difference should not exceed 15 days.");
                return Mono.just(response);
            }

            return botService.getBotNodeFromId(botId)
                    .doOnError(s -> log.info(s.getMessage()))
                    .map(new Function<JsonNode, Object>() {
                        @Override
                        public Object apply(JsonNode campaignDetails) {
                            ObjectMapper mapper = new ObjectMapper();

                            String botName = campaignDetails.path("name").asText();

                            return findBotQuery(paging, botName, startTimestamp, endTimestamp, provider.toLowerCase(), tag)
                                    .map(new Function<Slice<XMessageDAO>, Object>() {
                                        @Override
                                        public Object apply(Slice<XMessageDAO> xMessageDAOS) {
                                            Map<String, Object> result = new HashMap<>();
                                            List<Map<String, Object>> xMessageDAOListNew = filterMessageState(xMessageDAOS.getContent());
                                            result.put("total", xMessageDAOListNew.size());
                                            result.put("records", xMessageDAOListNew);
                                            response.setResult(result);
                                            return response;
                                        }
                                    });
                        }
                    });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Find query for user history api - by params
     * @param paging
     * @param userId
     * @param startTimestamp
     * @param endTimestamp
     * @param provider
     * @param tag
     * @return
     */
    public Mono<Slice<XMessageDAO>> findUserQuery(Pageable paging, String userId, Timestamp startTimestamp, Timestamp endTimestamp, String provider, String tag) {
        if(tag != null && !tag.isEmpty()) {
            return xMsgRepo.findAllByUserIdInAndFromIdInAndTimestampAfterAndTimestampBeforeAndProviderAndTagsContains(paging, List.of("admin", userId), List.of("admin", userId), startTimestamp, endTimestamp, provider.toLowerCase(), tag);
        }
        return xMsgRepo.findAllByUserIdInAndFromIdInAndTimestampAfterAndTimestampBeforeAndProvider(paging, List.of("admin", userId), List.of("admin", userId), startTimestamp, endTimestamp, provider.toLowerCase());
    }

    /**
     * Find query for bot history api - by params
     * @param paging
     * @param botName
     * @param startTimestamp
     * @param endTimestamp
     * @param provider
     * @param tag
     * @return
     */
    public Mono<Slice<XMessageDAO>> findBotQuery(Pageable paging, String botName, Timestamp startTimestamp, Timestamp endTimestamp, String provider, String tag) {
        if(tag != null && !tag.isEmpty()) {
            return xMsgRepo.findAllByAppAndTimestampAfterAndTimestampBeforeAndProviderAndTagsContains(paging, botName, startTimestamp, endTimestamp, provider.toLowerCase(), tag);
        }
        return xMsgRepo.findAllByAppAndTimestampAfterAndTimestampBeforeAndProvider(paging, botName, startTimestamp, endTimestamp, provider.toLowerCase());
    }

    /**
     * Convert ByteBuffer data to Hex String
     *
     * @param buffer
     * @return
     */
    public static String toHexString(ByteBuffer buffer) {
        final StringBuilder r = new StringBuilder(buffer.remaining() * 2);
        while (buffer.hasRemaining()) {
            final byte b = buffer.get();
            r.append(HEX_CODE[(b >> 4) & 0xF]);
            r.append(HEX_CODE[(b & 0xF)]);
        }
        return r.toString();
    }

    /**
     * Filter XMessages by state for user/bot history
     * @param xMessageDAOList
     * @return
     */
    public List<Map<String, Object>> filterMessageState(List<XMessageDAO> xMessageDAOList) {

        Set<String> messageIdSet = new HashSet<>();
        Map<String, XMessageDAO> sentMap = new HashMap<>();
        Map<String, XMessageDAO> deliverdMap = new HashMap<>();
        Map<String, XMessageDAO> readMap = new HashMap<>();

        xMessageDAOList.forEach(xMessageDAO -> {
            messageIdSet.add(xMessageDAO.getMessageId());
            if (xMessageDAO.getMessageState().equalsIgnoreCase(MessageState.SENT.name())) {
                sentMap.put(xMessageDAO.getMessageId(), xMessageDAO);
            }
            if (xMessageDAO.getMessageState().equalsIgnoreCase(MessageState.DELIVERED.name())) {
                deliverdMap.put(xMessageDAO.getMessageId(), xMessageDAO);
            }
            if (xMessageDAO.getMessageState().equalsIgnoreCase(MessageState.READ.name())) {
                readMap.put(xMessageDAO.getMessageId(), xMessageDAO);
            }
        });

        List<XMessageDAO> xMessageDAOListNew = new ArrayList<>();
        /* Add message receipts with highest message state only in order READ->DELIVERED->SENT, given READ is highest order */
        messageIdSet.forEach(messageId -> {
            if (readMap != null && readMap.containsKey(messageId)) {
                xMessageDAOListNew.add(readMap.get(messageId));
            } else if (deliverdMap != null && deliverdMap.containsKey(messageId)) {
                xMessageDAOListNew.add(deliverdMap.get(messageId));
            } else if (sentMap != null && sentMap.containsKey(messageId)) {
                xMessageDAOListNew.add(sentMap.get(messageId));
            }
        });
        /* Sort by timestamp in descending order */
        List<Map<String, Object>> list = new ArrayList<>();
        xMessageDAOListNew.sort(Comparator.comparing(XMessageDAO::getTimestamp).reversed());
        xMessageDAOListNew.forEach(xMessageDAO -> {
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
            try{
                if(sentMap.get(xMessageDAO.getMessageId()) != null) {
                    String xMessage = sentMap.get(xMessageDAO.getMessageId()).getXMessage();
                    XMessage currentXmsg = XMessageParser.parse(new ByteArrayInputStream(xMessage.getBytes()));
                    Map<String, Object> payloadMap= new HashMap<>();
                    if(currentXmsg.getPayload().getText() != null) {
                        payloadMap.put("text", currentXmsg.getPayload().getText());
                    }
                    if(currentXmsg.getPayload().getMedia() != null) {
                        payloadMap.put("media", currentXmsg.getPayload().getMedia());
                    }
                    if(currentXmsg.getPayload().getButtonChoices() != null) {
                        payloadMap.put("buttonChoices", currentXmsg.getPayload().getButtonChoices());
                    }
                    if(currentXmsg.getPayload().getLocation() != null) {
                        payloadMap.put("location", currentXmsg.getPayload().getLocation());
                    }
                    if(currentXmsg.getPayload().getContactCard() != null) {
                        payloadMap.put("contactCard", currentXmsg.getPayload().getContactCard());
                    }
                    daoMap.put("payload", payloadMap);
                    daoMap.put("sentTimestamp", sentMap.get(xMessageDAO.getMessageId()).getTimestamp());
                } else {
                    daoMap.put("sentTimestamp", null);
                }
                if(deliverdMap.get(xMessageDAO.getMessageId()) != null) {
                    daoMap.put("deliveryTimestamp", deliverdMap.get(xMessageDAO.getMessageId()).getTimestamp());
                } else {
                    daoMap.put("deliveryTimestamp", null);
                }
                if(readMap.get(xMessageDAO.getMessageId()) != null) {
                    daoMap.put("readTimestamp", readMap.get(xMessageDAO.getMessageId()).getTimestamp());
                } else {
                    daoMap.put("readTimestamp", null);
                }
            } catch (Exception ex) {
                log.error("Exception when fetching payload: "+ex.getMessage());
            }

            list.add(daoMap);
        });
        return list;
    }

}
