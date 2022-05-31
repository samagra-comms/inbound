package com.uci.inbound.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

@Slf4j
@SpringBootTest(classes = InboundTestConfig.class)
@ExtendWith(MockitoExtension.class)
public class XMsgProcessingUtilTest {

	private GupShupWhatsappAdapter gupShupWhatsappAdapter;
    private NetcoreWhatsappAdapter netcoreWhatsappAdapter;
	private ObjectMapper objectMapper;
    @MockBean
    public XMessageRepository xmsgRepo;

	@MockBean
	SimpleProducer kafkaProducer;

	@MockBean
	BotService botService;

	@MockBean
	RedisCacheService redisCacheService;

//	    @Value("${spring.kafka.bootstrap-servers}")
//    private String BOOTSTRAP_SERVERS;    

	@BeforeEach
	public void setup() throws JsonProcessingException {
		objectMapper = new ObjectMapper();
		Mockito
				.when(xmsgRepo.insert(Mockito.any(XMessageDAO.class)))
				.thenReturn(Mono.just(XMessageDAO.builder().build()));

	}

	@Test
	public void simplePayload() throws JsonProcessingException {
		String simplePayload = "{\"message_id\": \"ABEGkZlgQyWAAgo-sDVSUOa9jH0z\",\"from\": \"919960432580\",\"received_at\": \"1567090835\",\"context\": {\"ncmessage_id\": null,\"message_id\": null},\"message_type\": \"TEXT\",\"text_type\": {\"text\": \"Hi UCI\"}}";
		JsonNode botNode = objectMapper.readTree("{\"id\":\"api.bot.getByParam\",\"ver\":\"1.0\",\"ts\":\"2022-04-21T19:16:52.914Z\",\"params\":{\"resmsgid\":\"9a01a120-c1a7-11ec-afae-4f4769c7c758\",\"msgid\":\"9a00b6c0-c1a7-11ec-afae-4f4769c7c758\",\"status\":\"successful\",\"err\":null,\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"data\":{\"id\":\"d655cf03-1f6f-4510-acf6-d3f51b488a5e\",\"name\":\"UCI Demo\",\"startingMessage\":\"Hi UCI\",\"users\":[],\"logicIDs\":[\"e96b0865-5a76-4566-8694-c09361b8ae32\"],\"owners\":null,\"created_at\":\"2021-07-08T18:48:37.740Z\",\"updated_at\":\"2022-02-11T14:09:53.570Z\",\"status\":\"enabled\",\"description\":\"For Internal Demo\",\"startDate\":\"2022-02-01T00:00:00.000Z\",\"endDate\":null,\"purpose\":\"For Internal Demo\",\"ownerOrgID\":\"ORG_001\",\"ownerID\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"logic\":[{\"id\":\"e96b0865-5a76-4566-8694-c09361b8ae32\",\"transformers\":[{\"id\":\"bbf56981-b8c9-40e9-8067-468c2c753659\",\"meta\":{\"form\":\"https://hosted.my.form.here.com\",\"formID\":\"UCI-demo-1\"}}],\"adapter\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"name\":\"UCI Demo\",\"created_at\":\"2021-07-08T18:47:44.925Z\",\"updated_at\":\"2022-02-03T12:29:32.959Z\",\"description\":null}]}}}\n");
		Mockito.
				when(botService.getBotFromStartingMessage("Hi UCI"))
				.thenReturn(Mono.just(botNode));

		NetcoreWhatsAppMessage message = objectMapper.readValue(simplePayload, NetcoreWhatsAppMessage.class);
		NetcoreWhatsAppMessage[] messages = {message};
		NetcoreMessageFormat netcoreMessage = new NetcoreMessageFormat();
		netcoreMessage.setMessages(messages);

		processTest(netcoreMessage);
	}

	@Test
	public void simplePayloadInvalidBot() throws JsonProcessingException {
		String simplePayload = "{\"message_id\": \"ABEGkZlgQyWAAgo-sDVSUOa9jH0z\",\"from\": \"919960432580\",\"received_at\": \"1567090835\",\"context\": {\"ncmessage_id\": null,\"message_id\": null},\"message_type\": \"TEXT\",\"text_type\": {\"text\": \"Hello UCI\"}}";
		JsonNode invalidBotNode = objectMapper.readTree("{\"id\":\"api.bot.getByParam\",\"ver\":\"1.0\",\"ts\":\"2022-04-21T19:16:52.914Z\",\"params\":{\"resmsgid\":\"9a01a120-c1a7-11ec-afae-4f4769c7c758\",\"msgid\":\"9a00b6c0-c1a7-11ec-afae-4f4769c7c758\",\"status\":\"successful\",\"err\":null,\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"data\":{\"id\":\"d655cf03-1f6f-4510-acf6-d3f51b488a5e\",\"name\":\"UCI Demo\",\"startingMessage\":\"Hi UCI\",\"users\":[],\"logicIDs\":[\"e96b0865-5a76-4566-8694-c09361b8ae32\"],\"owners\":null,\"created_at\":\"2021-07-08T18:48:37.740Z\",\"updated_at\":\"2022-02-11T14:09:53.570Z\",\"status\":\"disabled\",\"description\":\"For Internal Demo\",\"startDate\":\"2022-02-01T00:00:00.000Z\",\"endDate\":null,\"purpose\":\"For Internal Demo\",\"ownerOrgID\":\"ORG_001\",\"ownerID\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"logic\":[{\"id\":\"e96b0865-5a76-4566-8694-c09361b8ae32\",\"transformers\":[{\"id\":\"bbf56981-b8c9-40e9-8067-468c2c753659\",\"meta\":{\"form\":\"https://hosted.my.form.here.com\",\"formID\":\"UCI-demo-1\"}}],\"adapter\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"name\":\"UCI Demo\",\"created_at\":\"2021-07-08T18:47:44.925Z\",\"updated_at\":\"2022-02-03T12:29:32.959Z\",\"description\":null}]}}}\n");
		Mockito.
				when(botService.getBotFromStartingMessage("Hello UCI"))
				.thenReturn(Mono.just(invalidBotNode));

		NetcoreWhatsAppMessage message = objectMapper.readValue(simplePayload, NetcoreWhatsAppMessage.class);
		NetcoreWhatsAppMessage[] messages = {message};
		NetcoreMessageFormat netcoreMessage = new NetcoreMessageFormat();
		netcoreMessage.setMessages(messages);

		processTest(netcoreMessage);
	}

	@Test
	public void simplePayloadNotStartingMessage() throws JsonProcessingException {
		String simplePayload = "{\"message_id\": \"ABEGkZlgQyWAAgo-sDVSUOa9jH0z\",\"from\": \"919960432580\",\"received_at\": \"1567090835\",\"context\": {\"ncmessage_id\": null,\"message_id\": null},\"message_type\": \"TEXT\",\"text_type\": {\"text\": \"1\"}}";
		JsonNode botNode = objectMapper.readTree("{\"id\":\"api.bot.getByParam\",\"ver\":\"1.0\",\"ts\":\"2022-04-21T19:16:52.914Z\",\"params\":{\"resmsgid\":\"9a01a120-c1a7-11ec-afae-4f4769c7c758\",\"msgid\":\"9a00b6c0-c1a7-11ec-afae-4f4769c7c758\",\"status\":\"successful\",\"err\":null,\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"data\":[{\"id\":\"d655cf03-1f6f-4510-acf6-d3f51b488a5e\",\"name\":\"UCI Demo\",\"startingMessage\":\"Hi UCI\",\"users\":[],\"logicIDs\":[\"e96b0865-5a76-4566-8694-c09361b8ae32\"],\"owners\":null,\"created_at\":\"2021-07-08T18:48:37.740Z\",\"updated_at\":\"2022-02-11T14:09:53.570Z\",\"status\":\"enabled\",\"description\":\"For Internal Demo\",\"startDate\":\"2022-02-01T00:00:00.000Z\",\"endDate\":null,\"purpose\":\"For Internal Demo\",\"ownerOrgID\":\"ORG_001\",\"ownerID\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"logic\":[{\"id\":\"e96b0865-5a76-4566-8694-c09361b8ae32\",\"transformers\":[{\"id\":\"bbf56981-b8c9-40e9-8067-468c2c753659\",\"meta\":{\"form\":\"https://hosted.my.form.here.com\",\"formID\":\"UCI-demo-1\"}}],\"adapter\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"name\":\"UCI Demo\",\"created_at\":\"2021-07-08T18:47:44.925Z\",\"updated_at\":\"2022-02-03T12:29:32.959Z\",\"description\":null}]}]}}\n");
		Mockito
				.when(redisCacheService.getXMessageDaoCache(any()))
				.thenReturn(XMessageDAO
						.builder()
						.id(UUID.fromString("f82f65f0-c401-11ec-a342-8720380590a2"))
						.userId("admin")
						.fromId("9960432580")
						.channel("WhatsApp")
						.provider("Netcore")
						.timestamp(LocalDateTime.now())
						.messageState("REPLIED")
						.app("UCI Demo")
						.causeId("ABEGkZlgQyWAAgo")
						.xMessage("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
								"<xMessage>\n" +
								"    <app>UCI Demo</app>\n" +
								"    <channel>WhatsApp</channel>\n" +
								"    <channelURI>WhatsApp</channelURI>\n" +
								"    <from>\n" +
								"        <bot>false</bot>\n" +
								"        <broadcast>false</broadcast>\n" +
								"        <deviceType>PHONE</deviceType>\n" +
								"        <userID>9960432580</userID>\n" +
								"    </from>\n" +
								"    <messageId>\n" +
								"        <channelMessageId>ABEGkZlgQyWAAgo-sDVSUOa9jH0z</channelMessageId>\n" +
								"    </messageId>\n" +
								"    <messageState>REPLIED</messageState>\n" +
								"    <messageType>TEXT</messageType>\n" +
								"    <payload>\n" +
								"        <text>Hi UCI</text>\n" +
								"    </payload>\n" +
								"    <provider>Netcore</provider>\n" +
								"    <providerURI>Netcore</providerURI>\n" +
								"    <timestamp>1567090835000</timestamp>\n" +
								"    <to>\n" +
								"        <bot>false</bot>\n" +
								"        <broadcast>false</broadcast>\n" +
								"        <userID>admin</userID>\n" +
								"    </to>\n" +
								"</xMessage>\n")
						.build()
				);
		Mockito
				.when(botService.getBotFromName("UCI Demo"))
				.thenReturn(Mono.just(botNode));

		NetcoreWhatsAppMessage message = objectMapper.readValue(simplePayload, NetcoreWhatsAppMessage.class);
		NetcoreWhatsAppMessage[] messages = {message};
		NetcoreMessageFormat netcoreMessage = new NetcoreMessageFormat();
		netcoreMessage.setMessages(messages);

		processTest(netcoreMessage);
	}

	private void processTest(NetcoreMessageFormat netcoreMessage) throws JsonProcessingException {
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




	// For Gupshup

	@Test
	public void simplePayloadGS() throws JsonProcessingException {
		String simplePayload = "{\"waNumber\":\"919311415686\",\"mobile\":\"919415787824\",\"replyId\":null,\"messageId\":null,\"timestamp\":1616952476000,\"name\":\"chaks\",\"version\":0,\"type\":\"text\",\"text\":\"Hi UCI\",\"image\":null,\"document\":null,\"voice\":null,\"audio\":null,\"video\":null,\"location\":null,\"response\":null,\"extra\":null,\"app\":null}";
		JsonNode botNode = objectMapper.readTree("{\"id\":\"api.bot.getByParam\",\"ver\":\"1.0\",\"ts\":\"2022-04-21T19:16:52.914Z\",\"params\":{\"resmsgid\":\"9a01a120-c1a7-11ec-afae-4f4769c7c758\",\"msgid\":\"9a00b6c0-c1a7-11ec-afae-4f4769c7c758\",\"status\":\"successful\",\"err\":null,\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"data\":{\"id\":\"d655cf03-1f6f-4510-acf6-d3f51b488a5e\",\"name\":\"UCI Demo\",\"startingMessage\":\"Hi UCI\",\"users\":[],\"logicIDs\":[\"e96b0865-5a76-4566-8694-c09361b8ae32\"],\"owners\":null,\"created_at\":\"2021-07-08T18:48:37.740Z\",\"updated_at\":\"2022-02-11T14:09:53.570Z\",\"status\":\"enabled\",\"description\":\"For Internal Demo\",\"startDate\":\"2022-02-01T00:00:00.000Z\",\"endDate\":null,\"purpose\":\"For Internal Demo\",\"ownerOrgID\":\"ORG_001\",\"ownerID\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"logic\":[{\"id\":\"e96b0865-5a76-4566-8694-c09361b8ae32\",\"transformers\":[{\"id\":\"bbf56981-b8c9-40e9-8067-468c2c753659\",\"meta\":{\"form\":\"https://hosted.my.form.here.com\",\"formID\":\"UCI-demo-1\"}}],\"adapter\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"name\":\"UCI Demo\",\"created_at\":\"2021-07-08T18:47:44.925Z\",\"updated_at\":\"2022-02-03T12:29:32.959Z\",\"description\":null}]}}}\n");
		Mockito.
				when(botService.getBotFromStartingMessage("Hi UCI"))
				.thenReturn(Mono.just(botNode));
		GSWhatsAppMessage message = objectMapper.readValue(simplePayload, GSWhatsAppMessage.class);

		processTestGS(message);
	}

	@Test
	public void simplePayloadInvalidBotGS() throws JsonProcessingException {
		String simplePayload = "{\"waNumber\":\"919311415686\",\"mobile\":\"919415787824\",\"replyId\":null,\"messageId\":null,\"timestamp\":1616952476000,\"name\":\"chaks\",\"version\":0,\"type\":\"text\",\"text\":\"Hello UCI\",\"image\":null,\"document\":null,\"voice\":null,\"audio\":null,\"video\":null,\"location\":null,\"response\":null,\"extra\":null,\"app\":null}";
		JsonNode invalidBotNode = objectMapper.readTree("{\"id\":\"api.bot.getByParam\",\"ver\":\"1.0\",\"ts\":\"2022-04-21T19:16:52.914Z\",\"params\":{\"resmsgid\":\"9a01a120-c1a7-11ec-afae-4f4769c7c758\",\"msgid\":\"9a00b6c0-c1a7-11ec-afae-4f4769c7c758\",\"status\":\"successful\",\"err\":null,\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"data\":{\"id\":\"d655cf03-1f6f-4510-acf6-d3f51b488a5e\",\"name\":\"UCI Demo\",\"startingMessage\":\"Hi UCI\",\"users\":[],\"logicIDs\":[\"e96b0865-5a76-4566-8694-c09361b8ae32\"],\"owners\":null,\"created_at\":\"2021-07-08T18:48:37.740Z\",\"updated_at\":\"2022-02-11T14:09:53.570Z\",\"status\":\"disabled\",\"description\":\"For Internal Demo\",\"startDate\":\"2022-02-01T00:00:00.000Z\",\"endDate\":null,\"purpose\":\"For Internal Demo\",\"ownerOrgID\":\"ORG_001\",\"ownerID\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"logic\":[{\"id\":\"e96b0865-5a76-4566-8694-c09361b8ae32\",\"transformers\":[{\"id\":\"bbf56981-b8c9-40e9-8067-468c2c753659\",\"meta\":{\"form\":\"https://hosted.my.form.here.com\",\"formID\":\"UCI-demo-1\"}}],\"adapter\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"name\":\"UCI Demo\",\"created_at\":\"2021-07-08T18:47:44.925Z\",\"updated_at\":\"2022-02-03T12:29:32.959Z\",\"description\":null}]}}}\n");
		Mockito.
				when(botService.getBotFromStartingMessage("Hello UCI"))
				.thenReturn(Mono.just(invalidBotNode));

		GSWhatsAppMessage message = objectMapper.readValue(simplePayload, GSWhatsAppMessage.class);

		processTestGS(message);
	}

	@Test
	public void simplePayloadNotStartingMessageGS() throws JsonProcessingException {
		String simplePayload = "{\"waNumber\":\"919311415686\",\"mobile\":\"919415787824\",\"replyId\":null,\"messageId\":null,\"timestamp\":1616952476000,\"name\":\"chaks\",\"version\":0,\"type\":\"text\",\"text\":\"1\",\"image\":null,\"document\":null,\"voice\":null,\"audio\":null,\"video\":null,\"location\":null,\"response\":null,\"extra\":null,\"app\":null}";
		JsonNode botNode = objectMapper.readTree("{\"id\":\"api.bot.getByParam\",\"ver\":\"1.0\",\"ts\":\"2022-04-21T19:16:52.914Z\",\"params\":{\"resmsgid\":\"9a01a120-c1a7-11ec-afae-4f4769c7c758\",\"msgid\":\"9a00b6c0-c1a7-11ec-afae-4f4769c7c758\",\"status\":\"successful\",\"err\":null,\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"data\":[{\"id\":\"d655cf03-1f6f-4510-acf6-d3f51b488a5e\",\"name\":\"UCI Demo\",\"startingMessage\":\"Hi UCI\",\"users\":[],\"logicIDs\":[\"e96b0865-5a76-4566-8694-c09361b8ae32\"],\"owners\":null,\"created_at\":\"2021-07-08T18:48:37.740Z\",\"updated_at\":\"2022-02-11T14:09:53.570Z\",\"status\":\"enabled\",\"description\":\"For Internal Demo\",\"startDate\":\"2022-02-01T00:00:00.000Z\",\"endDate\":null,\"purpose\":\"For Internal Demo\",\"ownerOrgID\":\"ORG_001\",\"ownerID\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"logic\":[{\"id\":\"e96b0865-5a76-4566-8694-c09361b8ae32\",\"transformers\":[{\"id\":\"bbf56981-b8c9-40e9-8067-468c2c753659\",\"meta\":{\"form\":\"https://hosted.my.form.here.com\",\"formID\":\"UCI-demo-1\"}}],\"adapter\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"name\":\"UCI Demo\",\"created_at\":\"2021-07-08T18:47:44.925Z\",\"updated_at\":\"2022-02-03T12:29:32.959Z\",\"description\":null}]}]}}\n");
		Mockito
				.when(redisCacheService.getXMessageDaoCache(any()))
				.thenReturn(XMessageDAO
						.builder()
						.id(UUID.fromString("f82f65f0-c401-11ec-a342-8720380590a2"))
						.userId("admin")
						.channel("WhatsApp")
						.provider("GupShup")
						.timestamp(LocalDateTime.now())
						.messageState("REPLIED")
						.app("UCI Demo")
						.causeId("ABEGkZlgQyWAAgo")
						.xMessage("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
								"<xMessage>\n" +
								"    <app>UCI Demo</app>\n" +
								"    <channel>WhatsApp</channel>\n" +
								"    <channelURI>WhatsApp</channelURI>\n" +
								"    <from>\n" +
								"        <bot>false</bot>\n" +
								"        <broadcast>false</broadcast>\n" +
								"        <deviceType>PHONE</deviceType>\n" +
								"        <userID>9960432580</userID>\n" +
								"    </from>\n" +
								"    <messageId>\n" +
								"        <channelMessageId>ABEGkZlgQyWAAgo-sDVSUOa9jH0z</channelMessageId>\n" +
								"    </messageId>\n" +
								"    <messageState>REPLIED</messageState>\n" +
								"    <messageType>TEXT</messageType>\n" +
								"    <payload>\n" +
								"        <text>Hi UCI</text>\n" +
								"    </payload>\n" +
								"    <provider>Netcore</provider>\n" +
								"    <providerURI>Netcore</providerURI>\n" +
								"    <timestamp>1567090835000</timestamp>\n" +
								"    <to>\n" +
								"        <bot>false</bot>\n" +
								"        <broadcast>false</broadcast>\n" +
								"        <userID>admin</userID>\n" +
								"    </to>\n" +
								"</xMessage>\n")
						.build()
				);
		Mockito
				.when(botService.getBotFromName("UCI Demo"))
				.thenReturn(Mono.just(botNode));

		GSWhatsAppMessage message = objectMapper.readValue(simplePayload, GSWhatsAppMessage.class);

		processTestGS(message);
	}


	public void processTestGS(GSWhatsAppMessage message) throws JsonProcessingException {
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
