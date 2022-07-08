package com.uci.inbound.xmsg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.BotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

    @RequestMapping(value = "/getBotHistory", method = RequestMethod.GET, produces = {"application/json", "text/json"})
    public Mono<Object> getBotHistory(@RequestParam(value = "botId", required = false) String botId,
                                      @RequestParam(value = "userId", required = false) String userId,
                                      @RequestParam("startDate") String startDate,
                                      @RequestParam("endDate") String endDate,
                                      @RequestParam(value = "provider", defaultValue = "firebase") String provider,
                                      @RequestParam(value = "msgId", required = false) String msgId) {
        try {
            XMessageHistoryResponse response = new XMessageHistoryResponse();
            if (botId == null && userId == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST.value());
                response.setErrorMsg("Bot id/user id required.");
                return Mono.just(response);
            }

            Pageable paging = (Pageable) CassandraPageRequest.of(PageRequest.of(0, 10000),
                    null
            );

            DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
            Date startD = formatter.parse(startDate);
            Date endD = formatter.parse(endDate);
            Timestamp startTimestamp = new Timestamp(startD.getTime());
            Timestamp endTimestamp = new Timestamp(endD.getTime());

            if (userId != null && !userId.isEmpty()) {
                return xMsgRepo.findAllByUserIdInAndFromIdInAndTimestampAfterAndTimestampBeforeAndProvider(paging, List.of("admin", userId), List.of("admin", userId), startTimestamp, endTimestamp, provider.toLowerCase())
                        .map(new Function<Slice<XMessageDAO>, Object>() {
                            @Override
                            public Object apply(Slice<XMessageDAO> xMessageDAOS) {
                                response.setStatusCode(HttpStatus.OK.value());
                                response.setRecords(xMessageDAOS.getContent());
//                                        if(xMessageDAOS.isLast()) {
//                                            response.setNextCursorMark(DEFAULT_CURSOR_MARK);
//                                        } else {
//                                            response.setNextCursorMark(toHexString(((CassandraPageRequest)xMessageDAOS.getPageable()).getPagingState()));
//                                            log.info("after cursor");
//                                        }
                                List<XMessageDAO> xMessageDAOListNew = filterMessageState(xMessageDAOS);
                                response.setRecords(xMessageDAOListNew);
                                return response;
                            }
                        });
            } else if (botId != null && !botId.isEmpty()) {
                return botService.getBotNodeFromId(botId)
                        .doOnError(s -> log.info(s.getMessage()))
                        .map(new Function<JsonNode, Mono<Object>>() {
                            @Override
                            public Mono<Object> apply(JsonNode campaignDetails) {
                                ObjectMapper mapper = new ObjectMapper();

                                String botName = campaignDetails.path("name").asText();

                                return xMsgRepo.findAllByAppAndTimestampAfterAndTimestampBeforeAndProvider(paging, botName, startTimestamp, endTimestamp, provider.toLowerCase())
                                        .map(new Function<Slice<XMessageDAO>, Object>() {
                                            @Override
                                            public Object apply(Slice<XMessageDAO> xMessageDAOS) {
                                                response.setStatusCode(HttpStatus.OK.value());
                                                response.setRecords(xMessageDAOS.getContent());

//                                                if(xMessageDAOS.isLast()) {
//                                                    response.setNextCursorMark(DEFAULT_CURSOR_MARK);
//                                                } else {
//                                                    response.setNextCursorMark(toHexString(((CassandraPageRequest)xMessageDAOS.getPageable()).getPagingState()));
//                                                    log.info("after cursor");
//                                                }
                                                List<XMessageDAO> xMessageDAOListNew = filterMessageState(xMessageDAOS);
                                                response.setRecords(xMessageDAOListNew);
                                                return response;
                                            }
                                        });
                            }
                        }).flatMap(new Function<Mono<Object>, Mono<? extends Object>>() {
                            @Override
                            public Mono<? extends Object> apply(Mono<Object> objectFlux) {
                                return objectFlux;
                            }
                        });

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
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

    public List<XMessageDAO> filterMessageState(Slice<XMessageDAO> xMessageDAOS) {
        List<XMessageDAO> xMessageDAOList = xMessageDAOS.getContent();

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
        messageIdSet.forEach(messageId -> {
            if (readMap != null && readMap.containsKey(messageId)) {
                xMessageDAOListNew.add(readMap.get(messageId));
            } else if (deliverdMap != null && deliverdMap.containsKey(messageId)) {
                xMessageDAOListNew.add(deliverdMap.get(messageId));
            } else if (sentMap != null && sentMap.containsKey(messageId)) {
                xMessageDAOListNew.add(sentMap.get(messageId));
            }
        });

        return xMessageDAOListNew;
    }

}
