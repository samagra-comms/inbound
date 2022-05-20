package com.uci.inbound.incoming;

import javax.validation.Valid;
import javax.xml.bind.JAXBException;

import com.uci.adapter.gs.whatsapp.GupShupWhatsappAdapter;
import com.uci.adapter.utils.MediaSizeLimit;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.BotService;
import com.uci.utils.azure.AzureBlobService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.inbound.utils.XMsgProcessingUtil;
import com.uci.utils.kafka.SimpleProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.uci.adapter.gs.whatsapp.GSWhatsAppMessage;

import lombok.extern.slf4j.Slf4j;
import com.uci.utils.kafka.RecordProducer;

import io.opentelemetry.api.trace.Tracer;

@Slf4j
@RestController
@RequestMapping(value = "/gupshup")
public class GupShupWhatsappConverter {

    @Value("${inboundProcessed}")
    private String inboundProcessed;

    @Value("${gupshup-opted-out}")
    private String optedOut;

    @Value("${inbound-error}")
    private String inboundError;

    private GupShupWhatsappAdapter gupShupWhatsappAdapter;

    @Autowired
    public RecordProducer kafkaProducer;

    @Autowired
    public Tracer tracer;

    @Autowired
    public XMessageRepository xmsgRepository;

    @Autowired
    public BotService botService; 
    
    @Autowired
    public RedisCacheService redisCacheService;
    
    @Value("${outbound}")
    public String outboundTopic;

    @Value("${messageReport}")
    public String topicReport;

    @Autowired
    public AzureBlobService azureBlobService;

    @Autowired
    public MediaSizeLimit mediaSizeLimit;

    @RequestMapping(value = "/whatsApp", method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void gupshupWhatsApp(@Valid GSWhatsAppMessage message) throws JsonProcessingException, JAXBException {
    	log.info("message:" +message);
    	
        gupShupWhatsappAdapter = GupShupWhatsappAdapter.builder()
                .botservice(botService)
                .xmsgRepo(xmsgRepository)
                .azureBlobService(azureBlobService)
                .mediaSizeLimit(mediaSizeLimit)
                .build();

        XMsgProcessingUtil.builder()
                .adapter(gupShupWhatsappAdapter)
                .xMsgRepo(xmsgRepository)
                .inboundMessage(message)
                .topicFailure(inboundError)
                .topicSuccess(inboundProcessed)
                .kafkaProducer(kafkaProducer)
                .botService(botService)
                .redisCacheService(redisCacheService)
                .topicOutbound(outboundTopic)
                .topicReport(topicReport)
                .tracer(tracer)
                .build()
                .process();
    }
}
