package com.uci.inbound.netcore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreMessageFormat;
import com.uci.adapter.netcore.whatsapp.NetcoreWhatsappAdapter;
import com.uci.inbound.utils.XMsgProcessingUtil;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.kafka.SimpleProducer;
import com.uci.utils.kafka.RecordProducer;

import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import com.uci.utils.BotService;
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
@RequestMapping(value = "/netcore")
public class NetcoreWhatsappConverter {

    @Value("${inboundProcessed}")
    private String inboundProcessed;

    @Value("${gupshup-opted-out}")
    private String optedOut;

    @Value("${inbound-error}")
    private String inboundError;

    private NetcoreWhatsappAdapter netcoreWhatsappAdapter;

    @Autowired
    public RecordProducer kafkaProducer;

    @Autowired
    public XMessageRepository xmsgRepo;

    @Autowired
    public BotService botService;
    
    @Autowired
    public Tracer tracer;

    @RequestMapping(value = "/whatsApp", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public void netcoreWhatsApp(@RequestBody NetcoreMessageFormat message) throws JsonProcessingException, JAXBException {
    	netcoreWhatsappAdapter = NetcoreWhatsappAdapter.builder()
                .botservice(botService)
                .build();

        XMsgProcessingUtil.builder()
                .adapter(netcoreWhatsappAdapter)
                .xMsgRepo(xmsgRepo)
                .inboundMessage(message.getMessages()[0])
                .topicFailure(inboundError)
                .topicSuccess(inboundProcessed)
                .kafkaProducer(kafkaProducer)
                .botService(botService)
                .tracer(tracer)
                .build()
                .process();
    }
}
