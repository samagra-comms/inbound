package com.uci.inbound.cdn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.adapter.cdn.service.AzureBlobService;
import com.uci.adapter.cdn.service.MinioClientService;
import com.uci.adapter.cdn.service.SunbirdCloudMediaService;
import com.uci.utils.bot.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;


@Slf4j
@RestController
@RequestMapping(value = "/cdn/")
public class FileCdnController {
    @Autowired
    private MinioClientService minioClientService;

    @Autowired
    private AzureBlobService azureBlobService;

    @Autowired
    private SunbirdCloudMediaService sunbirdCloudMediaService;

    @Autowired
    @Qualifier("rest")
    private RestTemplate restTemplate;

    @RequestMapping(value = "/minioSignedUrl", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = {"application/json", "text/json"})
    public ResponseEntity<JsonNode> minioRequest(@RequestParam MultipartFile file) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode result = null;
            if (file != null && file.getInputStream() != null) {
                log.info("File name '%s' " + file.getOriginalFilename());
//                String mimeType = URLConnection.guessContentTypeFromName(file.getOriginalFilename());
                String mimeType = file.getContentType();
                log.info("MimeType : " + mimeType);
                if(FileUtil.isValidFileType(mimeType)) {
                    try {
                        if (fileExistsWithSameName(file.getOriginalFilename())) {
                            return new ResponseEntity<>(
                                    mapper.createObjectNode().put("Error", "File with same name already exists!"),
                                    HttpStatus.CONFLICT
                            );
                        }
                        byte[] inputBytes = FileUtil.getInputBytesFromInputStream(file.getInputStream());
                        if(inputBytes != null) {
                            /* Unique File Name */
                            String name = FileUtil.getUploadedFileName(mimeType, "");
                            String filePath = FileUtil.fileToLocalFromBytes(inputBytes, mimeType, name);
                            String minioFileName = minioClientService.uploadFileFromPath(filePath, file.getOriginalFilename());
                            if (minioFileName != null) {
//                                String signedUrl = minioClientService.getFileSignedUrl(minioFileName);
                                String signedUrl = minioClientService.getFileSignedUrl(file.getOriginalFilename());
                                if (signedUrl != null) {
                                    result = mapper.createObjectNode();
                                    result.put("url", signedUrl);
                                    result.put("mimeType", mimeType);
                                    result.put("fileName", file.getOriginalFilename());
                                    return ResponseEntity.ok(result);
                                } else {
                                    result = mapper.createObjectNode();
                                    result.put("message", "Signed url not found with this name :" + minioFileName);
                                    return ResponseEntity.badRequest().body(result);
                                }
                            } else {
                                result = mapper.createObjectNode();
                                result.put("message", "File upload failed");
                                return ResponseEntity.badRequest().body(result);
                            }
                        } else {
                            return ResponseEntity.internalServerError().build();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return ResponseEntity.internalServerError().build();
                    }
                } else {
                    result = mapper.createObjectNode();
                    result.put("message", "Invalid file type");
                    return ResponseEntity.badRequest().body(result);
                }
            } else {
                result = mapper.createObjectNode();
                result.put("message", "File not found in request");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }


    @RequestMapping(value = "/minio/getSignedUrlForFileName", method = RequestMethod.GET)
    public ResponseEntity<String> getMinioFile(@RequestParam String fileName) {
		String fileUrl = minioClientService.getFileSignedUrl(fileName);
		if (fileUrl != null) {
			return ResponseEntity.ok(fileUrl);
		}
		else {
			return ResponseEntity.notFound().build();
		}
    }

    @RequestMapping(value = "/azure/getSignedUrlForFileName", method = RequestMethod.GET)
    public ResponseEntity<String> getAzureFile(@RequestParam String fileName) {
		String fileUrl = azureBlobService.getFileSignedUrl(fileName);
		if (fileUrl != null) {
			return ResponseEntity.ok(fileUrl);
		}
		else {
			return ResponseEntity.notFound().build();
		}
    }

    @RequestMapping(value = "/sunbird/getSignedUrlForFileName", method = RequestMethod.GET)
    public ResponseEntity<String> getSunbirdFile(@RequestParam String fileName) {
		String fileUrl = sunbirdCloudMediaService.getFileSignedUrl(fileName);
		if (fileUrl != null) {
			return ResponseEntity.ok(fileUrl);
		}
		else {
			return ResponseEntity.notFound().build();
		}
    }

    @RequestMapping(value = "/azure/container-sas", method = RequestMethod.GET)
    public void generateAzureContainerSASToken() {
        log.info(azureBlobService.generateContainerSASToken());
    }

    private boolean fileExistsWithSameName(String name) {
        String signedUrl = minioClientService.getFileSignedUrl(name);
        try {
            ResponseEntity<Object> response = restTemplate.exchange(
                    URI.create(signedUrl),
                    HttpMethod.HEAD,
                    HttpEntity.EMPTY,
                    Object.class
            );
            return true;
        }
        catch (HttpClientErrorException e) {
            return e.getStatusCode() != HttpStatus.NOT_FOUND;
        }
    }
}
