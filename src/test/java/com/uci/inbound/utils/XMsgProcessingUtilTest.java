package com.uci.inbound.utils;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.adapter.netcore.whatsapp.NetcoreWhatsappAdapter;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreMessageFormat;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreWhatsAppMessage;
import com.uci.dao.repository.XMessageRepository;
import com.uci.inbound.netcore.NetcoreWhatsappConverter;
import com.uci.utils.BotService;
import com.uci.utils.kafka.SimpleProducer;

import io.fusionauth.client.FusionAuthClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(SpringRunner.class)
@ExtendWith(MockitoExtension.class)
public class XMsgProcessingUtilTest {

    private NetcoreWhatsappAdapter netcoreWhatsappAdapter;
    
//    @Mock
//    public ProducerFactory producerFactory;
    
    @Mock 
    public WebClient webClient;

    @Mock
    public XMessageRepository xmsgRepo;
    
    @Autowired
    private KafkaProducer producer;
    
//    @Value("${spring.kafka.bootstrap-servers}")
//    private String BOOTSTRAP_SERVERS;    

	@Test
	public void processTest() throws JsonProcessingException, JAXBException {
		String simplePayload = "{\"message_id\": \"ABEGkZlgQyWAAgo-sDVSUOa9jH0z\",\"from\": \"919960432580\",\"received_at\": \"1567090835\",\"context\": {\"ncmessage_id\": null,\"message_id\": null},\"message_type\": \"TEXT\",\"text_type\": {\"text\": \"Hi UCI\"}}";
		ObjectMapper objectMapper = new ObjectMapper();
		NetcoreWhatsAppMessage message = objectMapper.readValue(simplePayload, NetcoreWhatsAppMessage.class);
		NetcoreWhatsAppMessage[] messages = {message};
		
		NetcoreMessageFormat netcoreMessage = new NetcoreMessageFormat();
		netcoreMessage.setMessages(messages);
		
		 Map<String, Object> configuration = new HashMap<>();
	        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "165.232.182.146:9094");
	        configuration.put(ProducerConfig.CLIENT_ID_CONFIG, "sample-producer");
	        configuration.put(ProducerConfig.ACKS_CONFIG, "all");
	        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
	        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
	        
		ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<String, String>(configuration);

		MockProducer mockProducer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
		
		KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<String, String>(producerFactory);
		
		SimpleProducer kafkaProducer = new SimpleProducer(kafkaTemplate);
		
		FusionAuthClient fusionAuthClient = new FusionAuthClient("c0VY85LRCYnsk64xrjdXNVFFJ3ziTJ91r08Cm0Pcjbc", "http://134.209.150.161:9011");
		
		BotService botService = new BotService(webClient, fusionAuthClient, null);
		
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
                .build();
	}
}
