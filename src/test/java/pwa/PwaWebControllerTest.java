package pwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.uci.adapter.cdn.FileCdnFactory;
import com.uci.adapter.pwa.PwaWebPortalAdapter;
import com.uci.adapter.pwa.web.inbound.PwaWebMessage;
import com.uci.dao.repository.XMessageRepository;
import com.uci.inbound.pwa.web.PwaWebController;
import com.uci.utils.cache.service.RedisCacheService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.xml.bind.JAXBException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {PwaWebController.class, UtilsTestConfig.class})
//@AutoConfigureMockMvc
public class PwaWebControllerTest {
    private PwaWebMessage pwaWebMessage;
    @Autowired
    private PwaWebController pwaWebController;
    @MockBean
    public XMessageRepository xMessageRepository;
    @MockBean
    public RedisCacheService redisCacheService;
    @MockBean
    public FileCdnFactory fileCdnFactory;
    @MockBean
    public PwaWebPortalAdapter pwaWebPortalAdapter;



    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        pwaWebMessage = new PwaWebMessage();
        pwaWebMessage.setMessageId("f3af59b6-57c3-4238-93d4-06bad3d79398");
        pwaWebMessage.setText("Hi UCI");
        pwaWebMessage.setUserId("67630c26-e29e-46ae-acb6-756704362d84");
        pwaWebMessage.setAppId("chatbot");
        pwaWebMessage.setFrom("ucipwa:9999999999");
        pwaWebMessage.setTo("5768d2b7-15c5-4ab2-bab1-e7ba2c638ec2");
    }

    @Test
    public void testPwaWebController() throws JAXBException, JsonProcessingException {
        pwaWebController.dikshaWeb(pwaWebMessage);
        assertNotNull(SimpleProducerTest.kafkaDataMap.get("${KAFKA_INBOUND_PROCESSED_TOPIC}"));
        assertEquals(SimpleProducerTest.kafkaData, SimpleProducerTest.kafkaDataMap.get("${KAFKA_INBOUND_PROCESSED_TOPIC}"));
    }
}
