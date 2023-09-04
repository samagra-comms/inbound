package com.uci.inbound.firebase.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.firebase.web.FirebaseNotificationAdapter;
import com.uci.adapter.firebase.web.inbound.FirebaseWebMessage;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.inbound.entity.DeliveryReport;
import com.uci.inbound.repository.DeliveryReportRepository;
import com.uci.utils.BotService;
import com.uci.utils.bot.util.BotUtil;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;
import java.util.List;
import java.util.function.Function;

@Slf4j
@RestController
@RequestMapping(value = "/firebase")
public class FirebaseWebController {
    @Autowired
    private DeliveryReportRepository deliveryReportRepository;

    @Value("${inboundProcessed}")
    private String inboundProcessed;

    public static ObjectMapper mapper = new ObjectMapper();

    @Value("${inbound-error}")
    private String inboundError;

    private FirebaseNotificationAdapter firebaseNotificationAdapter;

    @Autowired
    public SimpleProducer kafkaProducer;

    @Autowired
    public XMessageRepository xmsgRepo;

    @Autowired
    public BotService botService;

    @Autowired
    public RedisCacheService redisCacheService;

    @Value("${outbound}")
    public String outboundTopic;

    @Value("${messageReport}")
    public String topicReport;

    /**
     * this is Delivery Report for NL App
     *
     * @param message
     * @throws JsonProcessingException
     * @throws JAXBException
     */
    @RequestMapping(value = "/web", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public void deliveryReport(@RequestBody FirebaseWebMessage message) throws JsonProcessingException, JAXBException, InterruptedException {
        Flux.just(message)
                .flatMap(this::processMessage)
                .thenMany(Flux.empty())
                .subscribe();
    }

    /**
     * Checking external id and userid in Cache
     *
     * @param message
     * @return
     */
    private Mono<Void> processMessage(FirebaseWebMessage message) {
        return Mono.defer(() -> {
            if (message != null && message.getReport() != null && message.getReport().getExternalId() != null && !message.getReport().getExternalId().isEmpty()) {
                String externalId = message.getReport().getExternalId();
                String userId = message.getReport().getDestAdd();

                if (redisCacheService.isKeyExists(externalId + "_" + userId)) {
                    log.info("FirebaseWebController:processMessage:: externalId found in cache : " + externalId + " for this user : " + userId);
                    XMessageDAO xMessageDAO = (XMessageDAO) redisCacheService.getCache(externalId + "_" + userId);
                    if (xMessageDAO != null) {
                        return createDeliveryReport(xMessageDAO, message)
                                .flatMap(this::saveDeliveryReport)
                                .then();
                    }
                } else {
                    return Flux.just(message)
                            .flatMap(this::getXMessageDaoCass)
                            .flatMap(xMessageDAOList -> {
                                if (xMessageDAOList != null && !xMessageDAOList.isEmpty()) {
                                    return createDeliveryReport(xMessageDAOList.get(0), message);
                                } else {
                                    return Mono.empty(); // Return an empty Mono if xMessageDAOList is empty
                                }
                            })
                            .flatMap(this::saveDeliveryReport)
                            .then();
                }
            } else {
                log.error("FirebaseWebController:processMessage:: Invalid Request - ExternalId or UserId not found in the request");
                return Mono.empty();
            }
            return Mono.empty();
        });
    }

    /**
     * Inserting Delivery Report in PostgresDB
     *
     * @param deliveryReport
     * @return
     */
    private Mono<Void> saveDeliveryReport(DeliveryReport deliveryReport) {
        return deliveryReportRepository.save(deliveryReport)
                .doOnError(error -> log.error("An error occurred: " + error.getMessage(), error))
                .doOnSuccess(deliveryReport1 -> {
                    if (deliveryReport1.getId() != null) {
                        log.info("FirebaseWebController: Delivery Report Inserted Success : " + deliveryReport1);
                    } else {
                        log.error("FirebaseWebController: Delivery Report not Inserted : " + deliveryReport1);
                    }
                })
                .then();
    }

    /**
     * @param xMessageDAO
     * @param message
     * @return
     */
    private Mono<DeliveryReport> createDeliveryReport(XMessageDAO xMessageDAO, FirebaseWebMessage message) {
        return Mono.just(DeliveryReport.builder()
                .botId(xMessageDAO.getBotUuid().toString())
                .botName(xMessageDAO.getApp())
                .externalId(message.getReport().getExternalId())
                .messageState(message.getEventType())
                .fcmToken(message.getReport().getFcmDestAdd())
                .userId(message.getReport().getDestAdd())
                .cassId(xMessageDAO.getId() != null ? xMessageDAO.getId().toString() : null)
                .build());
    }

    private Mono<List<XMessageDAO>> getXMessageDaoCass(FirebaseWebMessage message) {
        log.info("getXMessageDaoCass::Fetching data in Cass : " + message.getReport().getExternalId() + " :: UserId : " + message.getReport().getDestAdd());
        return xmsgRepo.findAllByMessageIdAndUserIdInAndFromIdIn(message.getReport().getExternalId(), List.of(BotUtil.adminUserId, message.getReport().getDestAdd()), List.of(BotUtil.adminUserId, message.getReport().getDestAdd())).collectList()
                .map(new Function<List<XMessageDAO>, List<XMessageDAO>>() {
                    @Override
                    public List<XMessageDAO> apply(List<XMessageDAO> xMessageDAOS) {
                        if (xMessageDAOS != null && xMessageDAOS.size() > 0) {
                            log.info("Cassandra::Data found for this externalId : " + message.getReport().getExternalId() + " :: UserId: " + message.getReport().getDestAdd());
                        } else {
                            log.error("Cassandra::No Data found for this externalId : " + message.getReport().getExternalId() + " :: UserId: " + message.getReport().getDestAdd());
                        }
                        return xMessageDAOS;
                    }
                });
    }
}

