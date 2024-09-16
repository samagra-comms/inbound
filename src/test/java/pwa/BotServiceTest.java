package pwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.uci.utils.BotService;
import com.uci.utils.dto.BotServiceParams;
import io.fusionauth.client.FusionAuthClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class BotServiceTest extends BotService {

    public BotServiceTest(WebClient webClient, FusionAuthClient fusionAuthClient, Cache<Object, Object> cache, BotServiceParams botServiceParams) {
        super(webClient, fusionAuthClient, cache, botServiceParams);
    }

    public Mono<JsonNode> getBotNodeFromId(String botId) {
        String cacheKey = "bot-node-by-id:" + botId;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = null;
        try {
            root = mapper.readTree("{\"apiId\":\"api.bot.5768d2b7-15c5-4ab2-bab1-e7ba2c638ec2\",\"path\":\"/admin/bot/5768d2b7-15c5-4ab2-bab1-e7ba2c638ec2\",\"apiVersion\":\"v1\",\"msgid\":\"90216f57-700c-4c45-82ef-fb0d2ef532aa\",\"result\":{\"id\":\"5768d2b7-15c5-4ab2-bab1-e7ba2c638ec2\",\"createdAt\":\"2023-04-28T10:53:41.394Z\",\"updatedAt\":\"2023-04-28T10:53:41.395Z\",\"name\":\"UCI Demo\",\"startingMessage\":\"Hi UCI\",\"ownerID\":\"8f7ee860-0163-4229-9d2a-01cef53145ba\",\"ownerOrgID\":\"org01\",\"purpose\":\"For Internal Demo\",\"description\":\"For Internal Demo\",\"startDate\":\"2023-04-27T00:00:00.000Z\",\"endDate\":\"2025-12-01T00:00:00.000Z\",\"status\":\"ENABLED\",\"tags\":[],\"botImage\":null,\"users\":[],\"logicIDs\":[{\"id\":\"44fefff9-8719-4055-997e-be1225cf4731\",\"name\":\"UCI Demo\",\"createdAt\":\"2023-04-28T10:51:34.518Z\",\"updatedAt\":\"2023-04-28T10:51:34.518Z\",\"description\":null,\"adapterId\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"transformers\":[{\"id\":\"9c46aa04-b72e-423a-aab7-a1a05842d99b\",\"createdAt\":\"2023-04-28T10:51:34.501Z\",\"updatedAt\":\"2023-04-28T10:51:34.520Z\",\"meta\":{\"form\":\"https://hosted.my.form.here.com\",\"formID\":\"UCI-demo-1\"},\"transformerId\":\"bbf56981-b8c9-40e9-8067-468c2c753659\",\"conversationLogicId\":\"44fefff9-8719-4055-997e-be1225cf4731\"}],\"adapter\":{\"id\":\"44a9df72-3d7a-4ece-94c5-98cf26307324\",\"createdAt\":\"2023-04-28T06:02:41.824Z\",\"updatedAt\":\"2023-04-28T06:02:41.824Z\",\"channel\":\"WhatsApp\",\"provider\":\"gupshup\",\"config\":{\"2WAY\":\"2000193034\",\"phone\":\"9999999999\",\"HSM_ID\":\"2000193032\",\"credentials\":{\"vault\":\"samagra\",\"variable\":\"gupshupSamagraProd\"}},\"name\":\"SamagraProd\"}}]},\"startTime\":\"2023-09-28T13:01:52.055Z\",\"method\":\"GET\",\"endTime\":\"2023-09-28T13:01:52.080Z\"}");
            return Mono.just(root.path("result"));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
