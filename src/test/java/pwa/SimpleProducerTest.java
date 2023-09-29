package pwa;

import com.uci.utils.kafka.SimpleProducer;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

public class SimpleProducerTest extends SimpleProducer {
    public static String kafkaData = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<xMessage>\n" +
            "    <adapterId>44a9df72-3d7a-4ece-94c5-98cf26307324</adapterId>\n" +
            "    <app>UCI Demo</app>\n" +
            "    <botId>5768d2b7-15c5-4ab2-bab1-e7ba2c638ec2</botId>\n" +
            "    <channel>web</channel>\n" +
            "    <channelURI>web</channelURI>\n" +
            "    <from>\n" +
            "        <bot>false</bot>\n" +
            "        <broadcast>false</broadcast>\n" +
            "        <deviceType>PHONE_PWA</deviceType>\n" +
            "        <userID>ucipwa:9999999999</userID>\n" +
            "    </from>\n" +
            "    <messageId>\n" +
            "        <channelMessageId>f3af59b6-57c3-4238-93d4-06bad3d79398</channelMessageId>\n" +
            "        <replyId>ucipwa:9999999999</replyId>\n" +
            "    </messageId>\n" +
            "    <messageState>REPLIED</messageState>\n" +
            "    <messageType>TEXT</messageType>\n" +
            "    <ownerId>8f7ee860-0163-4229-9d2a-01cef53145ba</ownerId>\n" +
            "    <ownerOrgId>org01</ownerOrgId>\n" +
            "    <payload>\n" +
            "        <text>Hi UCI</text>\n" +
            "    </payload>\n" +
            "    <provider>pwa</provider>\n" +
            "    <providerURI>pwa</providerURI>\n" +
            "    <sessionId>eb566186-b2d1-4c9d-92b1-8d845f1708ab</sessionId>\n" +
            "    <timestamp>1695981278680</timestamp>\n" +
            "    <to>\n" +
            "        <bot>false</bot>\n" +
            "        <broadcast>false</broadcast>\n" +
            "        <userID>admin</userID>\n" +
            "    </to>\n" +
            "</xMessage>";
    public static Map<String, String> kafkaDataMap = new HashMap<>();

    public SimpleProducerTest(KafkaTemplate<String, String> simpleProducer1) {
        super(simpleProducer1);
    }

    public void send(String topic, String message) {
        kafkaDataMap.put("inbound-processed", kafkaData);
    }
}
