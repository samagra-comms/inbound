package com.uci.inbound.firebase.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.firebase.web.FirebaseNotificationAdapter;
import com.uci.adapter.firebase.web.inbound.FirebaseWebMessage;
import com.uci.inbound.utils.XMsgProcessingUtil;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.BotService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.xml.bind.JAXBException;

@Slf4j
@RestController
@RequestMapping(value = "/firebase")
public class FirebaseWebController {

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

    @RequestMapping(value = "/web", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public void dikshaWeb(@RequestBody FirebaseWebMessage message) throws JsonProcessingException, JAXBException {

        log.info("FirebaseWebController:dikshaWeb:: Request: " + mapper.writeValueAsString(message));

        firebaseNotificationAdapter = FirebaseNotificationAdapter.builder()
                .botService(botService)
                .build();

        XMsgProcessingUtil.builder()
                .adapter(firebaseNotificationAdapter)
                .xMsgRepo(xmsgRepo)
                .inboundMessage(message)
                .topicFailure(inboundError)
                .topicSuccess(inboundProcessed)
                .kafkaProducer(kafkaProducer)
                .botService(botService)
                .redisCacheService(redisCacheService)
                .topicOutbound(outboundTopic)
                .topicReport(topicReport)
                .build()
                .process();
    }
}

