package com.uci.inbound.xmsg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.CampaignService;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.XMessage;
import org.checkerframework.checker.index.qual.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;

@Slf4j
@RestController
@RequestMapping("/xmsg")
public class XmsgHistoryController {
    @Autowired
    private XMessageRepository xMsgRepo;

    @Autowired
    private CampaignService campaignService;

    @RequestMapping(value = "/getBotHistory", method = RequestMethod.GET, produces = {"application/json", "text/json"})
    public Mono<Flux<Object>> getBotHistory(@RequestParam(value = "botId", required = false) String botId,
                                            @RequestParam(value = "userId", required = false) String userId,
//                                                        @RequestParam("messageState") String messageState,
                                            @RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate,
                                            @RequestParam(value = "pageNo", required = false, defaultValue = "0") @Positive int pageNo,
                                            @RequestParam(value = "pageSize", required = false, defaultValue = "10") @Positive int pageSize,
                                            @RequestParam(value = "provider", defaultValue = "firebase") String provider) {
        try {
            log.info("Called getBotHistory");
//            List<String> messageStates = new ArrayList<>();
//            messageStates.add(XMessage.MessageState.SENT.name());
//            messageStates.add(XMessage.MessageState.DELIVERED.name());
//            messageStates.add(XMessage.MessageState.READ.name());

            if (botId == null && userId == null) {
                return null;
            }

            if (startDate == null || endDate == null) {
                return null;
            }

            Pageable paging = (Pageable) CassandraPageRequest.of(PageRequest.of(0, 100000),
                    null
            );

            DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
            Date startD = formatter.parse(startDate);
            Date endD = formatter.parse(endDate);
            Timestamp startTimestamp = new Timestamp(startD.getTime());
            Timestamp endTimestamp = new Timestamp(endD.getTime());

            if (userId != null && !userId.isEmpty()) {
//                return Mono.just(Flux.just(null));
                return Mono.just(
                        xMsgRepo.findAllByUserIdAndTimestampAfterAndTimestampBeforeAndProvider(paging, userId, startTimestamp, endTimestamp, provider.toLowerCase())
                                .map(new Function<Slice<XMessageDAO>, Object>() {
                                    @Override
                                    public Object apply(Slice<XMessageDAO> xMessageDAOS) {
//                                        xMessageDAOS.getPageable().get
                                        return xMessageDAOS.getContent();
                                    }
                                })
                );
            } else if (botId != null && !botId.isEmpty()) {
                return campaignService.getCampaignFromID(botId)
                        .doOnError(s -> log.info(s.getMessage()))
                        .map(new Function<JsonNode, Flux<Object>>() {
                            @Override
                            public Flux<Object> apply(JsonNode jsonNode) {
                                JsonNode campaignDetails = jsonNode.get("data");
                                ObjectMapper mapper = new ObjectMapper();

                                String botName = campaignDetails.path("name").asText();

//                                return Flux.just(null);
                                return xMsgRepo.findAllByAppAndTimestampAfterAndTimestampBeforeAndProvider(paging, botName, startTimestamp, endTimestamp, provider.toLowerCase())
                                        .map(new Function<Slice<XMessageDAO>, Object>() {
                                            @Override
                                            public Object apply(Slice<XMessageDAO> xMessageDAOS) {
                                                return xMessageDAOS.getContent();
                                            }
                                        });
                            }
                        });

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

}
