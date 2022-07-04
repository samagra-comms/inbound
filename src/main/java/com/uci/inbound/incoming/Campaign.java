package com.uci.inbound.incoming;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.uci.adapter.cdac.CdacBulkSmsAdapter;
import com.uci.adapter.cdac.TrackDetails;
import com.uci.adapter.provider.factory.ProviderFactory;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.dao.utils.XMessageDAOUtils;
import com.uci.utils.CampaignService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.*;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@CrossOrigin
@RestController
@RequestMapping(value = "/campaign")
public class Campaign {
    @Value("${campaign}")
    private String campaign;

    @Autowired
    public SimpleProducer kafkaProducer;

    @Autowired
    private ProviderFactory factoryProvider;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private XMessageRepository xMsgRepo;

    @Value("${inboundProcessed}")
    String topicSuccess;

    @Value("${inbound-error}")
    String topicFailure;

    @RequestMapping(value = "/start", method = RequestMethod.GET)
    public void startCampaign(@RequestParam("campaignId") String campaignId) throws JsonProcessingException, JAXBException {
//        kafkaProducer.send(campaign, campaignId);
//        return;
        campaignService.getCampaignFromID(campaignId).subscribe(node -> {
                JsonNode data = node.get("data");
                SenderReceiverInfo from = new SenderReceiverInfo().builder().userID("7597185708").deviceType(DeviceType.PHONE).build();
                SenderReceiverInfo to = new SenderReceiverInfo().builder().userID("admin").build();
                MessageId msgId = new MessageId().builder().channelMessageId(UUID.randomUUID().toString()).replyId("7597185708").build();
                XMessagePayload payload = new XMessagePayload().builder().text(data.path("startingMessage").asText()).build();
                JsonNode adapter = data.findValues("logic").get(0).get(0).get("adapter");
                log.info("adapter:"+adapter+", node:"+node);
                if(adapter.path("provider").asText().equals("firebase")) {
                    from.setDeviceType(DeviceType.PHONE_FCM);
                } else if(adapter.path("provider").asText().equals("pwa")) {
                    from.setDeviceType(DeviceType.PHONE_PWA);
                }
                String ownerOrgId = data.path("ownerOrgID") != null && !data.path("ownerOrgID").asText().equals("null") ? data.path("ownerOrgID").asText() : null;
                UUID ownerId = data.path("ownerID") != null && !data.path("ownerID").asText().equals("null") ? UUID.fromString(data.path("ownerID").asText()) : null;

                Timestamp timestamp = new Timestamp(System.currentTimeMillis());

                XMessage xmsg = new XMessage().builder()
                        .app(data.path("name").asText())
                        .sessionId(newConversationSessionId())
                        .ownerId(ownerId)
                        .ownerOrgId(ownerOrgId)
                        .from(from)
                        .to(to)
                        .messageId(msgId)
                        .messageState(XMessage.MessageState.REPLIED)
                        .messageType(XMessage.MessageType.TEXT)
                        .payload(payload)
                        .providerURI(adapter.path("provider").asText())
                        .channelURI(adapter.path("channel").asText())
                        .timestamp(timestamp.getTime())
                        .build();

                XMessageDAO currentMessageToBeInserted = XMessageDAOUtils.convertXMessageToDAO(xmsg);
                xMsgRepo.insert(currentMessageToBeInserted)
                        .doOnError(genericError("Error in inserting current message"))
                        .subscribe(xMessageDAO -> {
                            sendEventToKafka(xmsg);
                        });
            }
        );
    }

    /**
     * New Conversation Session UUID
     * @return
     */
    private UUID newConversationSessionId() {
        return UUID.randomUUID();
    }

    private void sendEventToKafka(XMessage xmsg) {
        String xmessage = null;
        try {
            xmessage = xmsg.toXML();
        } catch (JAXBException e) {
            kafkaProducer.send(topicFailure, "Start request for bot.");
        }
        kafkaProducer.send(topicSuccess, xmessage);
    }

    private Consumer<Throwable> genericError(String s) {
        return c -> {
            log.error(s + "::" + c.getMessage());
        };
    }

    @RequestMapping(value = "/pause", method = RequestMethod.GET)
    public void pauseCampaign(@RequestParam("campaignId") String campaignId) throws JsonProcessingException, JAXBException {
        kafkaProducer.send(campaign, campaignId);
        return;
    }

    @RequestMapping(value = "/resume", method = RequestMethod.GET)
    public void resumeCampaign(@RequestParam("campaignId") String campaignId) throws JsonProcessingException, JAXBException {
        kafkaProducer.send(campaign, campaignId);
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