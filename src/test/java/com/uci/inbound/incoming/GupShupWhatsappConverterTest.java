package com.uci.inbound.incoming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.gs.whatsapp.GSWhatsAppMessage;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreMessageFormat;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreWhatsAppMessage;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.inbound.InboundTestConfig;
import com.uci.inbound.netcore.NetcoreWhatsappConverter;
import com.uci.utils.BotService;
import com.uci.utils.azure.AzureBlobService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = InboundTestConfig.class)
@ExtendWith(MockitoExtension.class)
class GupShupWhatsappConverterTest {

    @Autowired
    GupShupWhatsappConverter gupShupWhatsappConverter;

    @MockBean
    public XMessageRepository xmsgRepo;

    @MockBean
    SimpleProducer kafkaProducer;

    @Spy
    @Autowired
    BotService botService;

    @Spy
    @Autowired
    RedisCacheService redisCacheService;

    @Autowired
    NetcoreWhatsappConverter netcoreWhatsappConverter;

    @Autowired
    AzureBlobService azureBlobService;

    @Test
    void gupshupWhatsApp() throws JsonProcessingException, JAXBException {
        Mockito
                .when(xmsgRepo.insert(Mockito.any(XMessageDAO.class)))
                .thenReturn(Mono.just(XMessageDAO.builder().build()));

//        Mockito
//                .doReturn(XMessageDAO.builder().build())
//                .when(redisCacheService).getXMessageDaoCache(any());
//
//        Mockito
//                .doReturn(Mono.just(new ObjectMapper().valueToTree(Pair.of(true, Pair.of(false, "anything")))))
//                .when(botService).getBotFromStartingMessage(Mockito.anyString());
//
//        Mockito
//                .doReturn(Mono.just(new ObjectMapper().valueToTree(Pair.of(true, "anything"))))
//                .when(botService).getBotFromName(Mockito.any());

        String simplePayload = "{\"waNumber\":\"919311415686\",\"mobile\":\"919415787824\",\"replyId\":null,\"messageId\":null,\"timestamp\":1616952476000,\"name\":\"chaks\",\"version\":0,\"type\":\"text\",\"text\":\"*\",\"image\":null,\"document\":null,\"voice\":null,\"audio\":null,\"video\":null,\"location\":null,\"response\":null,\"extra\":null,\"app\":null}";
        ObjectMapper objectMapper = new ObjectMapper();
        GSWhatsAppMessage message = objectMapper.readValue(simplePayload, GSWhatsAppMessage.class);

        gupShupWhatsappConverter.gupshupWhatsApp(message);
    }
}