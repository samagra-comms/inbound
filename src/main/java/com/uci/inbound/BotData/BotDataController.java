package com.uci.inbound.BotData;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.dao.repository.XMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashSet;
import java.util.UUID;

@RestController
@RequestMapping("/botData")
public class BotDataController {

    @Autowired
    private XMessageRepository xMessageRepository;

    @GetMapping(path = "/getBroadcastReport", produces = { "application/json" })
    public Mono<ResponseEntity> getBroadcastReport(@Param("botId") String botId, @Param("nextPage") String nextPage, @RequestParam(value = "limit", defaultValue = "1000") int limit) {
        if (limit == 0) {
            limit = 1000;
        }
        PageRequest pageRequest = PageRequest.of(0, limit);
        ByteBuffer pagingState = null;
        UUID botUuid;
        if (botId == null || botId.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("'botId' is required!"));
        }
        try {
            botUuid = UUID.fromString(botId);
        }
        catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body("Invalid format of 'botId'!"));
        }
        if (nextPage != null && !nextPage.isEmpty()) {
            try {
                pagingState = ByteBuffer.wrap(Base64.getUrlDecoder().decode(nextPage));
            }
            catch (IllegalArgumentException e) {
                return Mono.just(ResponseEntity.badRequest().body("Invalid 'nextPage' value!"));
            }
        }
        CassandraPageRequest cassandraPageRequest = CassandraPageRequest.of(pageRequest, pagingState);
        return xMessageRepository.findAllByBotUuid(botUuid, cassandraPageRequest)
                .map(xMessageDAOS -> {
                    String nextPageStateEncoded = null;
                    if (xMessageDAOS.hasNext()) {
                        ByteBuffer nextPageState = ((CassandraPageRequest)xMessageDAOS.nextPageable()).getPagingState();
                        if (nextPageState != null) {
                            byte[] bytes = new byte[nextPageState.remaining()];
                            nextPageState.get(bytes);
                            nextPageStateEncoded = Base64.getUrlEncoder().encodeToString(bytes);
                        }
                    }
                    HashSet<String> notSent = new HashSet<>(), sent = new HashSet<>();
                    xMessageDAOS.getContent().forEach(xMessageDAO -> {
                        if (xMessageDAO.getMessageState() != null && !xMessageDAO.getMessageState().isEmpty()) {
                            if (xMessageDAO.getMessageState().equalsIgnoreCase("NOT_SENT")) {
                                notSent.add(xMessageDAO.getUserId());
                            }
                            else {
                                sent.add(xMessageDAO.getUserId());
                            }
                        }
                    });
                    ObjectNode responseBody = new ObjectMapper().createObjectNode();
                    responseBody.put("next_page", nextPageStateEncoded);
                    responseBody.put("success", sent.toString());
                    responseBody.put("failure", notSent.toString());
                    ResponseEntity response = ResponseEntity.ok().body(responseBody);
                    return response;
                })
                .next();
    }
}
