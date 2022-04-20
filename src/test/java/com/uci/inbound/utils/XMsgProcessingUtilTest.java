package com.uci.inbound.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.gs.whatsapp.GSWhatsAppMessage;
import com.uci.adapter.gs.whatsapp.GupShupWhatsappAdapter;
import com.uci.adapter.netcore.whatsapp.NetcoreWhatsappAdapter;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreMessageFormat;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreWhatsAppMessage;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.inbound.InboundTestConfig;
import com.uci.utils.BotService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;

import static org.mockito.ArgumentMatchers.any;

@Slf4j
@SpringBootTest(classes = InboundTestConfig.class)
@ExtendWith(MockitoExtension.class)
public class XMsgProcessingUtilTest {

	private GupShupWhatsappAdapter gupShupWhatsappAdapter;
    private NetcoreWhatsappAdapter netcoreWhatsappAdapter;

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

//	    @Value("${spring.kafka.bootstrap-servers}")
//    private String BOOTSTRAP_SERVERS;    

	@Before
	public void setup(){
		Mockito
				.when(xmsgRepo.insert(Mockito.any(XMessageDAO.class)))
				.thenReturn(Mono.just(XMessageDAO.builder().build()));

		Mockito
				.doReturn(XMessageDAO.builder().build())
				.when(redisCacheService).getXMessageDaoCache(any());

		Mockito
				.doReturn(Mono.just(new ObjectMapper().valueToTree(Pair.of(true, Pair.of(false, "anything")))))
				.when(botService).getBotFromStartingMessage(Mockito.anyString());

		Mockito
				.doReturn(Mono.just(new ObjectMapper().valueToTree(Pair.of(true, "anything"))))
				.when(botService).getBotFromName(Mockito.any());

	}

	@Test
	public void processTest() throws JsonProcessingException, JAXBException {

		String simplePayload = "{\"message_id\": \"ABEGkZlgQyWAAgo-sDVSUOa9jH0z\",\"from\": \"919960432580\",\"received_at\": \"1567090835\",\"context\": {\"ncmessage_id\": null,\"message_id\": null},\"message_type\": \"TEXT\",\"text_type\": {\"text\": \"Hi UCI\"}}";
		ObjectMapper objectMapper = new ObjectMapper();
		NetcoreWhatsAppMessage message = objectMapper.readValue(simplePayload, NetcoreWhatsAppMessage.class);

		NetcoreWhatsAppMessage[] messages = {message};
		
		NetcoreMessageFormat netcoreMessage = new NetcoreMessageFormat();
		netcoreMessage.setMessages(messages);

		netcoreWhatsappAdapter = NetcoreWhatsappAdapter.builder()
                .botservice(botService)
                .build();
		
		XMsgProcessingUtil.builder()
				.adapter(netcoreWhatsappAdapter)
                .xMsgRepo(xmsgRepo)
                .inboundMessage(netcoreMessage.getMessages()[0])
                .topicFailure("test-inbound-error")
                .topicSuccess("test-inbound-processed")
                .kafkaProducer(kafkaProducer)
                .botService(botService)
				.redisCacheService(redisCacheService)
                .build()
				.process();
	}

	@Test
	public void processTestGS() throws JsonProcessingException {
		String simplePayload = "{\"waNumber\":\"919311415686\",\"mobile\":\"919415787824\",\"replyId\":null,\"messageId\":null,\"timestamp\":1616952476000,\"name\":\"chaks\",\"version\":0,\"type\":\"text\",\"text\":\"*\",\"image\":null,\"document\":null,\"voice\":null,\"audio\":null,\"video\":null,\"location\":null,\"response\":null,\"extra\":null,\"app\":null}";
		ObjectMapper objectMapper = new ObjectMapper();
		GSWhatsAppMessage message = objectMapper.readValue(simplePayload, GSWhatsAppMessage.class);

		gupShupWhatsappAdapter = GupShupWhatsappAdapter.builder()
															.botservice(botService)
															.build();

		XMsgProcessingUtil.builder()
				.adapter(gupShupWhatsappAdapter)
				.xMsgRepo(xmsgRepo)
				.inboundMessage(message)
				.topicFailure("test-inbound-error")
				.topicSuccess("test-inbound-processed")
				.kafkaProducer(kafkaProducer)
				.botService(botService)
				.redisCacheService(redisCacheService)
				.build()
				.process();

	}
}
