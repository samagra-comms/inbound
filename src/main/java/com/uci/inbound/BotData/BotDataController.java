package com.uci.inbound.BotData;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/botData")
public class BotDataController {

    @Autowired
    private XMessageRepository xMessageRepository;

    @GetMapping(path = "/getBroadcastReport", produces = { "application/json" })
    public Mono<ResponseEntity> getBroadcastReport(@Param("botId") String botId, @Param("nextPage") String nextPage,
                                                   @RequestParam(value = "limit", defaultValue = "1000") int limit,
                                                   @RequestParam(value = "createdAt", required = false) String createdAt) {
        if (limit == 0) {
            limit = 1000;
        }
        PageRequest pageRequest = PageRequest.of(0, limit);
        ByteBuffer pagingState = null;
        LocalDateTime botCreationDate = LocalDateTime.now().minusDays(7L);
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
        if (createdAt != null) {
            try {
                botCreationDate = new Timestamp(Long.parseLong(createdAt)).toLocalDateTime();
            }
            catch (NumberFormatException e) {
                return Mono.just(ResponseEntity.badRequest().body("Invalid DateTime parameter passed!"));
            }
        }
        CassandraPageRequest cassandraPageRequest = CassandraPageRequest.of(pageRequest, pagingState);
        return xMessageRepository.findAllByBotUuidAndTimestampAfter(botUuid, botCreationDate, cassandraPageRequest)
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
                    Pattern numberPattern = Pattern.compile("^\\d+$");
                    xMessageDAOS.getContent().forEach(xMessageDAO -> {
                        if (xMessageDAO.getMessageState() != null && !xMessageDAO.getMessageState().isEmpty()) {
                            if (numberPattern.matcher(xMessageDAO.getUserId()).matches()) {
                                if (xMessageDAO.getMessageState().equalsIgnoreCase("NOT_SENT")) {
                                    notSent.add(xMessageDAO.getUserId());
                                }
                                else {
                                    sent.add(xMessageDAO.getUserId());
                                }
                            }
                        }
                    });
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode responseBody = mapper.createObjectNode();
                    responseBody.put("next_page", nextPageStateEncoded);
                    ArrayNode sentArrayNode = mapper.createArrayNode();
                    ArrayNode notSentArrayNode = mapper.createArrayNode();
                    sent.forEach(sentArrayNode::add);
                    notSent.forEach(notSentArrayNode::add);
                    responseBody.set("success", sentArrayNode);
                    responseBody.set("failure", notSentArrayNode);
                    ResponseEntity response = ResponseEntity.ok().body(responseBody);
                    return response;
                })
                .next();
    }
}
