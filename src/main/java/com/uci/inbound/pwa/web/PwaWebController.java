package com.uci.inbound.pwa.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.cdn.FileCdnFactory;
import com.uci.adapter.pwa.PwaWebPortalAdapter;
import com.uci.adapter.pwa.web.inbound.PwaWebMessage;
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
@RequestMapping(value = "/pwa")
public class PwaWebController {

    @Value("${inboundProcessed}")
    private String inboundProcessed;

    public static ObjectMapper mapper = new ObjectMapper();

    @Value("${inbound-error}")
    private String inboundError;

    private PwaWebPortalAdapter pwaWebPortalAdapter;

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

    @Autowired
    public FileCdnFactory fileCdnFactory;

    @RequestMapping(value = "/web", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public void dikshaWeb(@RequestBody PwaWebMessage message) throws JsonProcessingException, JAXBException {

        log.info("PwaWebController:dikshaWeb:: Request: " + mapper.writeValueAsString(message));

        pwaWebPortalAdapter = PwaWebPortalAdapter.builder()
                .fileCdnProvider(fileCdnFactory.getFileCdnProvider())
                .build();

        XMsgProcessingUtil.builder()
                .adapter(pwaWebPortalAdapter)
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
