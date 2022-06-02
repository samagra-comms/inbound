package com.uci.inbound.fusionauth.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inversoft.error.Errors;
import com.inversoft.rest.ClientResponse;
import com.uci.utils.encryption.AESWrapper;
import com.uci.utils.model.FAUser;
import com.uci.utils.model.FAUserSegment;
import com.uci.utils.service.UserService;
import io.fusionauth.client.FusionAuthClient;
import io.fusionauth.domain.User;
import io.fusionauth.domain.api.user.SearchRequest;
import io.fusionauth.domain.api.user.SearchResponse;
import io.fusionauth.domain.search.UserSearchCriteria;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.DeviceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.uci.utils.encryption.AESWrapper.encodeKey;

@Slf4j
@RestController
@RequestMapping("/fusionAuth")
public class FusionAuthController {
    @Autowired
    public FusionAuthClient fusionAuthClient;

    @Autowired
    public UserService userService;

    public static final String applicationName = "FCMRegistration";
    public static final UUID applicationID = UUID.fromString("6c4ec900-e250-11ec-8fea-0242ac120002");

    @RequestMapping(value = "/registerUserFcmToken", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = {"application/json", "text/json"})
    public ResponseEntity<Object> registerUserFCMToken(@RequestBody FAUserSegment userSegment){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        try {
            if(userSegment.users != null && userSegment.users.get(0) != null && userSegment.users.get(0).getMobilePhone() != null) {
                Boolean applicationCreated = userService.createApplicationIfNotExists(applicationID, applicationName);
                if(applicationCreated) {
                    FAUser userT = userSegment.users.get(0);

                    String deviceString = DeviceType.FCM.name() + ":" + userT.getMobilePhone();
                    String encodedBase64Key = encodeKey("A%C*F-JaNdRgUkXp");
                    String username = AESWrapper.encrypt(deviceString, encodedBase64Key);

                    ObjectNode result = userService.registerUpdateFAUser(username, applicationID, userSegment);
                    if (result != null && result.findValue("success") != null
                            && result.findValue("success").asText().equals("true")) {
                        response.put("message", result.findValue("message"));
                        return ResponseEntity.ok(response);
                    } else if (result != null && result.findValue("success") != null
                            && result.findValue("success").asText().equals("false")
                            && result.findValue("errors") != null) {
                        response.put("errors", result.findValue("errors"));
                        return ResponseEntity.badRequest().body(response);
                    } else {
                        response.put("errorMessage", "User not registered");
                        return ResponseEntity.badRequest().body(response);
                    }
                } else {
                    response.put("errorMessage", "Error in registering application, Please contact the administrator.");
                    return ResponseEntity.badRequest().body(response);
                }
            } else {
                response.put("errorMessage", "User mobilePhone is required.");
                return ResponseEntity.internalServerError().body(response);
            }
        } catch (JsonProcessingException e) {
            response.put("errorMessage", "JsonProcessingException: "+e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            response.put("errorMessage", "Exception: "+e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }


    @RequestMapping(value = "/registerFcmTokenViaApis", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = {"application/json", "text/json"})
    public ResponseEntity<Object> registerFCMTokenViaApis(@RequestBody FAUserSegment userSegment){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode response = mapper.createObjectNode();
        try {
            if(userSegment.users != null && userSegment.users.get(0) != null && userSegment.users.get(0).getMobilePhone() != null) {
                Boolean applicationCreated = userService.createApplicationIfNotExists(applicationID, applicationName);
                if(applicationCreated) {
                    FAUser userT = userSegment.users.get(0);

                    String deviceString = DeviceType.FCM.name() + ":" + userT.getMobilePhone();
                    String encodedBase64Key = encodeKey("A%C*F-JaNdRgUkXp");
                    String username = AESWrapper.encrypt(deviceString, encodedBase64Key);

                    User user = userService.findFAUserByUsername(username);

                    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();

                    /* Request Node */
                    ObjectNode requestNode = mapper.createObjectNode();
                    ObjectNode userNode = mapper.createObjectNode();
                    ObjectNode registrationNode = mapper.createObjectNode();
                    ObjectNode dataNode = mapper.createObjectNode();
                    ArrayNode dataUsersNode = mapper.createArrayNode();
                    ObjectNode dataUserNode = mapper.createObjectNode();
                    ObjectNode dataDeviceNode = mapper.createObjectNode();

                    /* Existing user data node */
                    if (user != null) {
                        String faUserData = ow.writeValueAsString(user.data);
                        dataNode = (ObjectNode) mapper.readTree(faUserData);
                    }

                    /* User data device node  */
                    dataDeviceNode.put("id", userSegment.getDevice().getId());
                    dataDeviceNode.put("type", userSegment.getDevice().getType());

                    /* User data - users node */
                    dataUserNode.put("registrationChannel", userT.getRegistrationChannel());
                    dataUserNode.put("id", userT.getId() != null ? userT.getId() : "");
                    dataUserNode.put("type", userT.getType() != null ? userT.getType() : "");
                    dataUserNode.put("username", userT.getUsername() != null ? userT.getUsername() : "");
                    dataUserNode.put("mobilePhone", userT.getMobilePhone());
                    dataUsersNode.add(dataUserNode);

                    dataNode.put("users", dataUsersNode);
                    dataNode.put("device", dataDeviceNode);

                    /* User node */
                    userNode.put("username", username);
                    userNode.put("password", "dummyPassword");
                    userNode.put("active", true);
                    userNode.put("fullName", "");
                    userNode.put("data", dataNode);

                    /* Registration node */
                    registrationNode.put("username", username);
                    registrationNode.put("applicationId", applicationID.toString());

                    /* Request node */
                    requestNode.put("user", userNode);
                    requestNode.put("registration", registrationNode);

                    ObjectNode result = null;
                    if (user != null) {
                        result = userService.updateFAUser(user.id.toString(), requestNode);
                    } else {
                        result = userService.createFAUser(requestNode);
                    }

                    if (result != null && result.findValue("success") != null
                            && result.findValue("success").asText().equals("true")) {
                        response.put("data", result.findValue("data"));
                        return ResponseEntity.ok(response);
                    } else if (result != null && result.findValue("success") != null
                            && result.findValue("success").asText().equals("false")
                            && result.findValue("errors") != null) {
                        response.put("errors", result.findValue("errors"));
                        return ResponseEntity.badRequest().body(response);
                    } else {
                        response.put("errorMessage", "User not registered");
                        return ResponseEntity.badRequest().body(response);
                    }
                } else {
                    response.put("errorMessage", "Error in registering application, Please contact the administrator.");
                    return ResponseEntity.badRequest().body(response);
                }
            } else {
                response.put("errorMessage", "User mobilePhone is required.");
                return ResponseEntity.internalServerError().body(response);
            }
        } catch (JsonProcessingException e) {
            response.put("errorMessage", "JsonProcessingException: "+e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            response.put("errorMessage", "Exception: "+e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @RequestMapping(value = "/fetchFcmTokens", method = RequestMethod.GET, produces = {"application/json", "text/json"})
    public ResponseEntity<Object> fetchFCMTokens(){
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode responseNode = mapper.createObjectNode();
        ArrayNode tokens = mapper.createArrayNode();

        try {
            UserSearchCriteria criteria = new UserSearchCriteria()
//                    .with(cr -> cr.ids= List.of(UUID.fromString("ee098cb8-bfc3-45bd-9bad-893cfaabf104")))
                    .with(cr -> cr.queryString = "registrations.applicationId: "+applicationID.toString());
            ClientResponse<SearchResponse, Errors> result = fusionAuthClient.searchUsersByQueryString(new SearchRequest(criteria));
            if(result.wasSuccessful() && result.successResponse != null
                    && result.successResponse.users != null
                    && result.successResponse.users.size() > 0) {
                result.successResponse.users.forEach(user -> {
                    if(user.data != null && user.data.get("device") != null) {
                        try{
                            ObjectNode token = mapper.createObjectNode();
                            Map<String, String> device = (HashMap) user.data.get("device");
                            ArrayList users = (ArrayList) user.data.get("users");
                            if (device.get("id") != null
                                    && device.get("type") != null
                                    && device.get("type").equalsIgnoreCase(DeviceType.FCM.name())
                                    && users.get(0) != null) {
                                Map<String, String> userMap = (HashMap) users.get(0);
                                if(userMap != null && userMap.get("mobilePhone") != null) {
                                    token.put("fcmToken", device.get("id"));
                                    token.put("phoneNo", userMap.get("mobilePhone"));
                                    token.put("name", userMap.get("username"));
                                    tokens.add(token);
                                }
                            }
                        } catch (Exception ex) {
                            log.error("Exception in fetchFcmTokens: "+ ex.getMessage());
                        }
                    }

                });
                responseNode.put("data", tokens);

                return ResponseEntity.ok(responseNode);
            } else {
                responseNode.put("errorMessage", result.errorResponse.toString());
                return ResponseEntity.badRequest().body(responseNode);
            }

        } catch(Exception e){
            responseNode.put("errorMessage", "Exception: "+e.getMessage());
            return ResponseEntity.internalServerError().body(responseNode);
        }
    }
}
