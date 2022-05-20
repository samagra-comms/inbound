package com.uci.inbound.cdac;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.uci.adapter.cdac.CdacBulkSmsAdapter;
import com.uci.adapter.Request.CommonMessage;
import com.uci.inbound.utils.XMsgProcessingUtil;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.BotService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import com.uci.utils.kafka.RecordProducer;

import io.opentelemetry.api.trace.Tracer;

@Slf4j
@CrossOrigin
@RestController
@RequestMapping(value = "/cdac")
public class CDACConverter {

    @Value("${inboundProcessed}")
    private String inboundProcessed;

    @Value("${gupshup-opted-out}")
    private String optedOut;

    @Value("${inbound-error}")
    private String inboundError;

    @Autowired
    @Qualifier("cdacBulkSmsAdapter")
    private CdacBulkSmsAdapter cdacBulkSmsAdapter;

    @Autowired
    public RecordProducer kafkaProducer;

@Autowired
public Tracer tracer;

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

    @RequestMapping(value = "/sms/bulk/", method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void cdacBulk(@Valid CommonMessage message) throws JsonProcessingException {

        XMsgProcessingUtil.builder()
                .adapter(cdacBulkSmsAdapter)
                .xMsgRepo(xmsgRepo)
                .inboundMessage(message)
                .topicFailure(inboundError)
                .topicSuccess(inboundProcessed)
                .kafkaProducer(kafkaProducer)
                .botService(botService)
                .redisCacheService(redisCacheService)
                .topicOutbound(outboundTopic)
                .topicReport(topicReport)
                .build().process();
    }

    @RequestMapping(value = "/sms/single/", method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void cdacSingle(@Valid CommonMessage message) throws JsonProcessingException {

        XMsgProcessingUtil.builder()
                .adapter(cdacBulkSmsAdapter)
                .xMsgRepo(xmsgRepo)
                .inboundMessage(message)
                .topicFailure(inboundError)
                .topicSuccess(inboundProcessed)
                .kafkaProducer(kafkaProducer)
                .botService(botService)
                .redisCacheService(redisCacheService)
                .topicOutbound(outboundTopic)
                .topicReport(topicReport)
                .build().process();
    }
}
