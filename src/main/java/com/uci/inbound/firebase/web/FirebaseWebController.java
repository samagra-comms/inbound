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
        if (message != null && message.getReport() != null && message.getReport().getExternalId() != null && !message.getReport().getExternalId().isEmpty()) {
            String externalId = message.getReport().getExternalId();
            String userId = message.getReport().getDestAdd();
            return Mono.defer(() -> {
                if (redisCacheService.isKeyExists(externalId + "_" + userId)) {
                    log.info("FirebaseWebController:processMessage:: externalId found in cache : " + externalId + " for this user : " + userId);
                    XMessageDAO xMessageDAO = (XMessageDAO) redisCacheService.getCache(externalId + "_" + userId);
                    if (xMessageDAO != null) {
                        return createDeliveryReport(xMessageDAO, message)
                                .flatMap(this::saveDeliveryReport);
                    }
                } else {
                    log.error("FirebaseWebController:processMessage:: externalId not found in cache : " + externalId + " for this user : " + userId);
                }
                return Mono.empty();
            });
        }
        return Mono.empty();
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
                .cassId(xMessageDAO.getId().toString())
                .build());
    }

//    private void init(FirebaseWebMessage message) throws JsonProcessingException {
//        log.info("FirebaseWebController:deliveryReport:: Request: " + mapper.writeValueAsString(message));
//        if (message != null && message.getReport() != null && message.getReport().getExternalId() != null && !message.getReport().getExternalId().isEmpty()) {
//
//            String externalId = message.getReport().getExternalId();
//            String userId = message.getReport().getDestAdd();
//
//            if (redisCacheService.isKeyExists(externalId + "_" + userId)) {
//                XMessageDAO xMessageDAO = (XMessageDAO) redisCacheService.getCache(externalId + "_" + userId);
//                if (xMessageDAO != null) {
//                    DeliveryReport deliveryReport = DeliveryReport.builder()
//                            .botId(xMessageDAO.getBotUuid().toString())
//                            .botName(xMessageDAO.getApp())
//                            .externalId(message.getReport().getExternalId())
//                            .messageState(message.getEventType())
//                            .fcmToken(message.getReport().getFcmDestAdd())
//                            .userId(message.getReport().getDestAdd())
//                            .build();
//                    saveDeliveryReport(deliveryReport);
//                }
//            } else {
//                log.info("Fetching data from Cassandra... External Id : " + externalId + " : UserId : " + userId);
////                getXMessageDaoCass(externalId, userId).subscribe(new Consumer<List<XMessageDAO>>() {
////                    @Override
////                    public void accept(List<XMessageDAO> xMessageDAOList) {
////                        if (xMessageDAOList != null && xMessageDAOList.isEmpty()) {
////                            log.error("getXMessageDaoCass Dao Empty found : " + xMessageDAOList.size() + " : ExternalId : " + externalId + " : UserId : " + userId);
////                        }
////                        for (XMessageDAO xMessageDAO : xMessageDAOList) {
////                            DeliveryReport deliveryReport = DeliveryReport.builder()
////                                    .botId(xMessageDAO.getBotUuid().toString())
////                                    .botName(xMessageDAO.getApp())
////                                    .externalId(message.getReport().getExternalId())
////                                    .messageState(message.getEventType())
////                                    .fcmToken(message.getReport().getFcmDestAdd())
////                                    .userId(message.getReport().getDestAdd())
////                                    .build();
////                            saveDeliveryReport(deliveryReport);
////                        }
////                    }
////                });
//            }
//        } else {
//            log.error("FirebaseWebController:deliveryReport:: Invalid Request: " + message);
//        }
//
//    }

    /**
     * @param deliveryReport
     * @return
     */
//    private void saveDeliveryReport1(DeliveryReport deliveryReport) {
//        log.info("saving delivery report start ...");
//        deliveryReportRepository.save(deliveryReport)
//                .doOnError(error -> {
//                    log.error("An error occurred: " + error.getMessage(), error);
//                })
//                .subscribe(new Consumer<DeliveryReport>() {
//                    @Override
//                    public void accept(DeliveryReport deliveryReport1) {
//                        if (deliveryReport1.getId() != null) {
//                            log.info("FirebaseWebController:Delivery Report Inserted Success : " + deliveryReport1);
//                        } else {
//                            log.info("FirebaseWebController:Delivery Report not Inserted : " + deliveryReport1);
//                        }
//                    }
//                });
//    }

//    private Mono<List<XMessageDAO>> getXMessageDaoCass(String externalId, String userId) {
//        if (externalId != null && !externalId.isEmpty() && userId != null && !userId.isEmpty()) {
//            return xmsgRepo.findAllByMessageIdAndUserIdInAndFromIdIn(externalId, List.of(BotUtil.adminUserId, userId), List.of(BotUtil.adminUserId, userId)).collectList()
//                    .map(new Function<List<XMessageDAO>, List<XMessageDAO>>() {
//                        @Override
//                        public List<XMessageDAO> apply(List<XMessageDAO> xMessageDAOS) {
//                            if (xMessageDAOS != null && xMessageDAOS.size() > 0) {
//                                log.info("xMessageDAOList where messageId : Bot Name : " + xMessageDAOS.get(0).getApp() + " : Bot Id: " + xMessageDAOS.get(0).getBotUuid());
//                            }
//                            return xMessageDAOS;
//                        }
//                    });
//        } else {
//            log.error("ExternalId or UserId not found : " + externalId + " :: Userid : " + userId);
//            return Mono.empty();
//        }
//    }
}

