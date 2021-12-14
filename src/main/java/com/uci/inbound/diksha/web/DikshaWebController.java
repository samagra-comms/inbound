package com.uci.inbound.diksha.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.sunbird.web.SunbirdWebPortalAdapter;
import com.uci.adapter.sunbird.web.inbound.SunbirdWebMessage;
import com.uci.inbound.utils.XMsgProcessingUtil;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.BotService;
import com.uci.utils.kafka.SimpleProducer;

import io.opentelemetry.api.trace.Tracer;
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
@RequestMapping(value = "/diksha")
public class DikshaWebController {

    @Value("${inboundProcessed}")
    private String inboundProcessed;

    public static ObjectMapper mapper = new ObjectMapper();

    @Value("${inbound-error}")
    private String inboundError;

    private SunbirdWebPortalAdapter sunbirdWebPortalAdapter;

    @Autowired
    public SimpleProducer kafkaProducer;

    @Autowired
    public XMessageRepository xmsgRepo;

    @Autowired
    public BotService botService;
    
    @Autowired
    public Tracer tracer;

    @RequestMapping(value = "/web", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public void dikshaWeb(@RequestBody SunbirdWebMessage message) throws JsonProcessingException, JAXBException {

        System.out.println(mapper.writeValueAsString(message));

        sunbirdWebPortalAdapter = SunbirdWebPortalAdapter.builder()
                .build();

        XMsgProcessingUtil.builder()
                .adapter(sunbirdWebPortalAdapter)
                .xMsgRepo(xmsgRepo)
                .inboundMessage(message)
                .topicFailure(inboundError)
                .topicSuccess(inboundProcessed)
                .kafkaProducer(kafkaProducer)
                .botService(botService)
                .tracer(tracer)
                .build()
                .process();
    }
}
