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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


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
                        byte[] inputBytes = FileUtil.getInputBytesFromInputStream(file.getInputStream());
                        if(inputBytes != null) {
                            /* Unique File Name */
                            String name = FileUtil.getUploadedFileName(mimeType, "");
                            String filePath = FileUtil.fileToLocalFromBytes(inputBytes, mimeType, name);
                            String minioFileName = minioClientService.uploadFileFromPath(filePath, name);
                            if (minioFileName != null) {
//                                String signedUrl = minioClientService.getFileSignedUrl(minioFileName);
                                String signedUrl = minioClientService.getFileSignedUrl(name);
                                if (signedUrl != null) {
                                    result = mapper.createObjectNode();
                                    result.put("url", signedUrl);
                                    result.put("mimeType", mimeType);
                                    result.put("fileName", name);
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


    @RequestMapping(value = "/minio/getSignedUrl", method = RequestMethod.GET)
    public String getMinioFile() {
        return minioClientService.getFileSignedUrl("4229789621234812100-568466840572272579.jpeg");
    }

    @RequestMapping(value = "/azure/getSignedUrl", method = RequestMethod.GET)
    public String getAzureFile() {
        return azureBlobService.getFileSignedUrl("testing-1.jpg");
    }

    @RequestMapping(value = "/sunbird/getSignedUrl", method = RequestMethod.GET)
    public String getSunbirdFile() {
        return sunbirdCloudMediaService.getFileSignedUrl("4229789621234812100-568466840572272579.jpeg");
    }

    @RequestMapping(value = "/azure/container-sas", method = RequestMethod.GET)
    public void generateAzureContainerSASToken() {
        log.info(azureBlobService.generateContainerSASToken());
    }
}
