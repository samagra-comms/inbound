package com.uci.inbound.cdn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.utils.cdn.samagra.MinioClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@Slf4j
@RestController
@RequestMapping(value = "/cdn/")
public class FileCdnController {
    @Autowired
    private MinioClientService minioClientService;

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
                String minioFileName = minioClientService.uploadFileFromInputStream(file.getInputStream(), mimeType, null);
                if (minioFileName != null) {
                    String signedUrl = minioClientService.getFileSignedUrl(minioFileName);
                    if (signedUrl != null) {
                        result = mapper.createObjectNode();
                        result.put("url", signedUrl);
                        result.put("mimeType", mimeType);
                        result.put("fileName", minioFileName);
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
                result = mapper.createObjectNode();
                result.put("message", "File not found in request");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
