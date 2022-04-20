package com.uci.inbound.netcore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreMessageFormat;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreWhatsAppMessage;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.inbound.InboundTestConfig;
import com.uci.utils.BotService;
import com.uci.utils.azure.AzureBlobService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.CustomKafkaAppender;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = InboundTestConfig.class)
@ExtendWith(MockitoExtension.class)
class NetcoreWhatsappConverterTest {

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
    void netcoreWhatsApp() throws JAXBException, JsonProcessingException {
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

        String simplePayload = "{\"message_id\": \"ABEGkZlgQyWAAgo-sDVSUOa9jH0z\",\"from\": \"919960432580\",\"received_at\": \"1567090835\",\"context\": {\"ncmessage_id\": null,\"message_id\": null},\"message_type\": \"TEXT\",\"text_type\": {\"text\": \"Hi UCI\"}}";
        ObjectMapper objectMapper = new ObjectMapper();
        NetcoreWhatsAppMessage message = objectMapper.readValue(simplePayload, NetcoreWhatsAppMessage.class);
        NetcoreWhatsAppMessage[] messages = {message};

        NetcoreMessageFormat netcoreMessage = new NetcoreMessageFormat();
        netcoreMessage.setMessages(messages);

        netcoreWhatsappConverter.netcoreWhatsApp(netcoreMessage);

    }
}