package com.uci.inbound.incoming;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.uci.adapter.cdac.CdacBulkSmsAdapter;
import com.uci.adapter.cdac.TrackDetails;
import com.uci.adapter.provider.factory.ProviderFactory;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.dao.utils.XMessageDAOUtils;
import com.uci.utils.BotService;
import com.uci.utils.bot.util.BotUtil;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.xml.bind.JAXBException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@CrossOrigin
@RestController
@RequestMapping(value = "/campaign")
public class CampaignController {
    @Value("${notificationInboundProcessed}")
    private String notificationInboundProcessed;

    @Autowired
    public SimpleProducer kafkaProducer;

    @Autowired
    private ProviderFactory factoryProvider;

    @Autowired
    private BotService botService;

    @Autowired
    private XMessageRepository xMsgRepo;

    @Value("${inbound-error}")
    String topicFailure;

    private long cassInsertCount;
    private long cassInsertErrorCount;

    @RequestMapping(value = "/start", method = RequestMethod.GET)
    public ResponseEntity<String> startCampaign(@RequestParam("campaignId") String campaignId, @RequestParam(value = "page", required = false) String page,
                                                @RequestHeader(value = "Conversation-Authorization", required = false) String conversationAuthorization) {
        final long startTime = System.nanoTime();
        logTimeTaken(startTime, 0, "process-start: %d ms");
        log.info("Call campaign service : "+campaignId+" page : "+page);
        Map<String, String> meta;
        if(page != null && !page.isEmpty()){
            meta = new HashMap<>();
            meta.put("page", page);
        } else {
            meta = null;
        }
        if(conversationAuthorization != null && !conversationAuthorization.isEmpty()){
            log.info("Conversation Authorization found : True");
            if(meta == null){
                meta = new HashMap<>();
            }
            meta.put("conversation-authorization", conversationAuthorization);
        }
        Map<String, String> finalMeta = meta;
        botService.getBotNodeFromId(campaignId).subscribe(data -> {
                    try{
                        SenderReceiverInfo from = new SenderReceiverInfo().builder().userID("9876543210").deviceType(DeviceType.PHONE).meta(finalMeta).build();
                        SenderReceiverInfo to = new SenderReceiverInfo().builder().userID("admin").build();
                        MessageId msgId = new MessageId().builder().channelMessageId(UUID.randomUUID().toString()).replyId("9876543210").build();
                        XMessagePayload payload = new XMessagePayload().builder().text(BotUtil.getBotNodeData(data, "startingMessage")).build();
                        JsonNode adapter = BotUtil.getBotNodeAdapter(data);
                        log.info("adapter:" + adapter + ", node:" + data);
                        if (adapter.path("provider").asText().equals("firebase")) {
                            from.setDeviceType(DeviceType.PHONE_FCM);
                        } else if (adapter.path("provider").asText().equals("pwa")) {
                            from.setDeviceType(DeviceType.PHONE_PWA);
                        }

                        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

                        XMessage xmsg = new XMessage().builder()
                                .botId(UUID.fromString(BotUtil.getBotNodeData(data, "id")))
                                .app(BotUtil.getBotNodeData(data, "name"))
                                .adapterId(BotUtil.getBotNodeAdapterId(data))
                                .sessionId(BotUtil.newConversationSessionId())
                                .ownerId(BotUtil.getBotNodeData(data, "ownerID"))
                                .ownerOrgId(BotUtil.getBotNodeData(data, "ownerOrgID"))
                                .from(from)
                                .to(to)
                                .messageId(msgId)
                                .messageState(XMessage.MessageState.REPLIED)
                                .messageType(XMessage.MessageType.TEXT)
                                .payload(payload)
                                .providerURI(adapter.path("provider").asText())
                                .channelURI(adapter.path("channel").asText())
                                .timestamp(timestamp.getTime())
                                .tags(BotUtil.getBotNodeTags(data))
                                .build();

                        XMessageDAO currentMessageToBeInserted = XMessageDAOUtils.convertXMessageToDAO(xmsg);
                        xMsgRepo.insert(currentMessageToBeInserted)
                                .doOnError(genericError("Error in inserting current message"))
                                .doOnSuccess(xMessageDAO -> {
                                    cassInsertCount++;
                                    log.info("Data insert in Cassandra Count : "+cassInsertCount);
                                })
                                .subscribe(xMessageDAO -> {
                                    sendEventToKafka(xmsg);
                                    logTimeTaken(startTime, 0, "process-end: %d ms");
                                });
                    } catch(Exception ex) {
                        log.error("Inbound:CampaignController::startCampaign::Error: " + ex.getMessage());
                    }
                }
        );
        return new ResponseEntity<>("Notification Sending", HttpStatus.OK);
    }

    private void sendEventToKafka(XMessage xmsg) {
        String xmessage = null;
        try {
            xmessage = xmsg.toXML();
        } catch (JAXBException e) {
            kafkaProducer.send(topicFailure, "Start request for bot.");
        }
        kafkaProducer.send(notificationInboundProcessed, xmessage);
    }

    private Consumer<Throwable> genericError(String s) {
        return c -> {
            cassInsertErrorCount++;
            log.info("Data not inserted in Cassandra Count : " + cassInsertErrorCount);
            log.error(s + "::" + c.getMessage());
        };
    }

    private void logTimeTaken(long startTime, int checkpointID, String formatedMsg) {
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000;
        if(formatedMsg == null) {
            log.info(String.format("CP-%d: %d ms", checkpointID, duration));
        } else {
            log.info(String.format(formatedMsg, duration));
        }
    }

    @RequestMapping(value = "/pause", method = RequestMethod.GET)
    public void pauseCampaign(@RequestParam("campaignId") String campaignId) throws JsonProcessingException, JAXBException {
        kafkaProducer.send(notificationInboundProcessed, campaignId);
        return;
    }

    @RequestMapping(value = "/resume", method = RequestMethod.GET)
    public void resumeCampaign(@RequestParam("campaignId") String campaignId) throws JsonProcessingException, JAXBException {
        kafkaProducer.send(notificationInboundProcessed, campaignId);
        return;
    }

    @RequestMapping(value = "/status/cdac/bulk", method = RequestMethod.GET)
    public TrackDetails getCampaignStatus(@RequestParam("campaignId") String campaignId) {
        CdacBulkSmsAdapter iprovider = (CdacBulkSmsAdapter) factoryProvider.getProvider("cdac", "SMS");
        try {
             iprovider.getLastTrackingReport(campaignId);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }
}