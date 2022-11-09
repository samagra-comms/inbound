package com.uci.inbound.incoming;

import javax.validation.Valid;
import javax.xml.bind.JAXBException;

import com.uci.adapter.gs.whatsapp.GupShupWhatsappAdapter;
import com.uci.adapter.service.media.SunbirdCloudMediaService;
import com.uci.adapter.utils.MediaSizeLimit;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.BotService;
import com.uci.utils.azure.AzureBlobService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.inbound.utils.XMsgProcessingUtil;
import com.uci.utils.cdn.FileCdnFactory;
import com.uci.utils.cdn.samagra.MinioClientService;
import com.uci.utils.kafka.SimpleProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.uci.adapter.gs.whatsapp.GSWhatsAppMessage;

import lombok.extern.slf4j.Slf4j;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import scala.Option;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping(value = "/gupshup")
public class GupShupWhatsappConverter {

    @Value("${inboundProcessed}")
    private String inboundProcessed;

    @Value("${gupshup-opted-out}")
    private String optedOut;

    @Value("${inbound-error}")
    private String inboundError;

    private GupShupWhatsappAdapter gupShupWhatsappAdapter;

    @Autowired
    public SimpleProducer kafkaProducer;

    @Autowired
    public XMessageRepository xmsgRepository;

    @Autowired
    public BotService botService; 
    
    @Autowired
    public RedisCacheService redisCacheService;
    
    @Value("${outbound}")
    public String outboundTopic;

    @Value("${messageReport}")
    public String topicReport;

    @Autowired
    public MediaSizeLimit mediaSizeLimit;

    @Autowired
    public FileCdnFactory fileCdnFactory;

    @Value("${sunbird.cloud.media.storage.type}")
    private String mediaStorageType;

    @Value("${sunbird.cloud.media.storage.key}")
    private String mediaStorageKey;

    @Value("${sunbird.cloud.media.storage.secret}")
    private String mediaStorageSecret;

    @Value("${sunbird.cloud.media.storage.url}")
    private String mediaStorageUrl;

    @Value("${sunbird.cloud.media.storage.container}")
    private String mediaStorageContainer;

    @RequestMapping(value = "/whatsApp", method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void gupshupWhatsApp(@Valid GSWhatsAppMessage message) throws JsonProcessingException, JAXBException {
//        testMedia();

        SunbirdCloudMediaService mediaService = new SunbirdCloudMediaService(mediaStorageType, mediaStorageKey, mediaStorageSecret, mediaStorageUrl, mediaStorageContainer);

        log.info("message:" +message);
    	
        gupShupWhatsappAdapter = GupShupWhatsappAdapter.builder()
                .botservice(botService)
                .xmsgRepo(xmsgRepository)
                .fileCdnProvider(fileCdnFactory.getFileCdnProvider())
                .mediaSizeLimit(mediaSizeLimit)
                .mediaService(mediaService)
                .build();

        XMsgProcessingUtil.builder()
                .adapter(gupShupWhatsappAdapter)
                .xMsgRepo(xmsgRepository)
                .inboundMessage(message)
                .topicFailure(inboundError)
                .topicSuccess(inboundProcessed)
                .kafkaProducer(kafkaProducer)
                .botService(botService)
                .redisCacheService(redisCacheService)
                .topicOutbound(outboundTopic)
                .topicReport(topicReport)
                .build()
                .process();
    }

    private void testMedia() {
        Option<Object> isDirectory = new Option<Object>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Object get() {
                return false;
            }

            @Override
            public Object productElement(int n) {
                return null;
            }

            @Override
            public int productArity() {
                return 0;
            }

            @Override
            public boolean canEqual(Object that) {
                return false;
            }
        };

        Option<Object> attempt = new Option<Object>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Object get() {
                return 1;
            }

            @Override
            public Object productElement(int n) {
                return null;
            }

            @Override
            public int productArity() {
                return 0;
            }

            @Override
            public boolean canEqual(Object that) {
                return false;
            }
        };
        Option<Object> retry = new Option<Object>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Object get() {
                return 3;
            }

            @Override
            public Object productElement(int n) {
                return null;
            }

            @Override
            public int productArity() {
                return 0;
            }

            @Override
            public boolean canEqual(Object that) {
                return false;
            }
        };
        Option<Object> ttl = new Option<Object>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Object get() {
                return 1000;
            }

            @Override
            public Object productElement(int n) {
                return null;
            }

            @Override
            public int productArity() {
                return 0;
            }

            @Override
            public boolean canEqual(Object that) {
                return false;
            }
        };
        Option<String> url = new Option<String>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public String get() {
                return "https://cdn.samagra.io/";
            }

            @Override
            public Object productElement(int n) {
                return null;
            }

            @Override
            public int productArity() {
                return 0;
            }

            @Override
            public boolean canEqual(Object that) {
                return false;
            }
        };

        StorageConfig config = new StorageConfig("aws", "AKIAXIN3MSQ374UEFRKX", "MjYFTpbLqqVu43zGeJyEdoPxymqNI7Bzl9no0rqb", url);
        BaseStorageService service = StorageServiceFactory.getStorageService(config);
        String file = service.upload("auriga-uci", "/home/auriga/Pictures/testing-1.jpg",
                LocalDateTime.now().toString(), isDirectory, attempt, retry, ttl
        );
        System.out.println("Auriga url: "+file);
    }
}
