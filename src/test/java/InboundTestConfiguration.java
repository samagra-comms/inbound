import com.uci.adapter.cdn.service.AzureBlobService;
import com.uci.adapter.cdn.service.MinioClientService;
import com.uci.adapter.cdn.service.SunbirdCloudMediaService;
import com.uci.utils.cache.service.RedisCacheService;

import org.springframework.context.annotation.Bean;

public class InboundTestConfiguration {

	@Bean
	public RedisCacheService redisCacheService() {
		return new RedisCacheService();
	}

	@Bean
	public MinioClientService minioClientService() {
		return new MinioClientService();
	}

	@Bean
	public AzureBlobService azureBlobService() {
		return new AzureBlobService();
	}

	@Bean
	public SunbirdCloudMediaService sunbirdCloudMediaService() {
		return new SunbirdCloudMediaService();
	}
}
