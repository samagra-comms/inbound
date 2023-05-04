import com.uci.adapter.cdn.service.AzureBlobService;
import com.uci.adapter.cdn.service.MinioClientService;
import com.uci.adapter.cdn.service.SunbirdCloudMediaService;
import com.uci.inbound.cdn.FileCdnController;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ContextConfiguration(classes = InboundTestConfiguration.class)
@WebMvcTest(FileCdnController.class)
@Import(FileCdnController.class)
public class FileCdnControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private MinioClientService minioClientService;

	@MockBean
	private AzureBlobService azureBlobService;

	@MockBean
	private SunbirdCloudMediaService sunbirdCloudMediaService;

	@Test
	public void getFileSignedUrlMinio() throws Exception {
		when(minioClientService.getFileSignedUrl("testFile")).thenReturn("testUrl");
		mockMvc.perform(MockMvcRequestBuilders
				.get("/cdn/minio/getSignedUrlForFileName")
				.queryParam("fileName", "testFile"))
				.andExpect(status().isOk())
				.andExpect(content().string("testUrl"));
	}

	@Test
	public void getFileSignedUrlAzure() throws Exception {
		when(azureBlobService.getFileSignedUrl("testFile")).thenReturn("testUrl");
		mockMvc.perform(MockMvcRequestBuilders
						.get("/cdn/azure/getSignedUrlForFileName")
						.queryParam("fileName", "testFile"))
				.andExpect(status().isOk())
				.andExpect(content().string("testUrl"));
	}

	@Test
	public void getFileSignedUrlSunbird() throws Exception {
		when(sunbirdCloudMediaService.getFileSignedUrl("testFile")).thenReturn("testUrl");
		mockMvc.perform(MockMvcRequestBuilders
						.get("/cdn/sunbird/getSignedUrlForFileName")
						.queryParam("fileName", "testFile"))
				.andExpect(status().isOk())
				.andExpect(content().string("testUrl"));
	}
}
