package com.neu.info7255.raghav.assignment.demo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.neu.info7255.raghav.assignment.demo.config.RabbitMQConfiguration;
import com.neu.info7255.raghav.assignment.demo.model.ResponseObject;
import com.neu.info7255.raghav.assignment.demo.services.JsonSchemaValidatorService;
import com.neu.info7255.raghav.assignment.demo.services.PlanService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
public class PlanController {

    private final PlanService planService;
    private final JsonSchemaValidatorService jsonSchemaValidatorService;

    private final RabbitTemplate template;

    @PostMapping(value = "/plan")
    public ResponseEntity<ResponseObject> createPlan(@RequestBody String request, @AuthenticationPrincipal Jwt jwt) {
        System.out.println("createPlan method hit");

        try {
            JSONObject jsonObjectPlan = new JSONObject(request);

            // Validate JSON schema
            if (!jsonSchemaValidatorService.validateJSONSchema(jsonObjectPlan)) {
                System.out.println("Json Error");
                return createErrorResponse("Please check Input Type", HttpStatus.BAD_REQUEST);
            }

            String objectKey = jsonObjectPlan.get("objectType") + ":" + jsonObjectPlan.get("objectId");

            // Check if plan already exists
            if (planService.ifKeyExists(objectKey)) {
                return createErrorResponse("Already existing plan", HttpStatus.CONFLICT);
            }

            // Save the new plan and log the action
            String eTagGeneratedAfterSave = planService.createPlan(jsonObjectPlan, objectKey);
            logAction(request, "SAVE");

            publishPlanCreatedEvent(jsonObjectPlan, objectKey);

            ResponseObject responseObject = new ResponseObject("Plan Added!", HttpStatus.CREATED.value(), jsonObjectPlan.get("objectId"));
            return ResponseEntity.created(new URI("/plan/" + objectKey))
                    .eTag(eTagGeneratedAfterSave)
                    .body(responseObject);

        } catch (Exception e) {
            return createErrorResponse(e.toString(), HttpStatus.BAD_REQUEST);
        }
    }

    private void publishPlanCreatedEvent(JSONObject planData, String objectKey) {
        // Convert plan data to a suitable format for messaging
        Map<String, Object> messageContent = new HashMap<>();

        messageContent.put("operation", "SAVE");
        messageContent.put("body", planData.toString());
        System.out.println("Sending message: " + messageContent);

        // Publish message to the queue
        template.convertAndSend(RabbitMQConfiguration.topicExchangeName, RabbitMQConfiguration.queueName, messageContent);
        System.out.println("Published plan creation event to RabbitMQ");
    }

    private ResponseEntity<ResponseObject> createErrorResponse(String message, HttpStatus status) {
        ResponseObject responseObject = new ResponseObject(message, status.value(), new ArrayList<>());
        return new ResponseEntity<>(responseObject, status);
    }

    private void logAction(String request, String operation) {
        Map<String, String> actionMap = new HashMap<>();
        actionMap.put("operation", operation);
        actionMap.put("body", request);
        System.out.println("Sending message: " + actionMap);
    }

    @GetMapping(value = "/plan")
    public ResponseEntity<ResponseObject> getAllPlans(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> plans = planService.getAllPlans();

        if (plans.isEmpty()) {
            return createErrorResponse("No Plans Found", HttpStatus.NOT_FOUND);
        }

        ResponseObject responseObject = new ResponseObject("Success", HttpStatus.OK.value(), plans);
        return ResponseEntity.ok(responseObject);
    }


    @GetMapping(value = "/plan/{id}")
    public ResponseEntity<String> getPlan(@PathVariable String id,
                                          @RequestHeader HttpHeaders requestHeaders) {

        String key = "plan:" + id; // Constructing the key using the "plan" prefix and the provided id
        Map<String, Object> plan = planService.getPlanById(key);

        if (plan == null || plan.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject(new ResponseObject("Provided Wrong Plan Id!", HttpStatus.NOT_FOUND.value(), new ArrayList<>())).toString());
        }

        String etagFromCache = planService.accessEtag(key);
        String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");
        String ifMatchHeader = requestHeaders.getFirst("If-Match");

        if (ifMatchHeader != null && !ifMatchHeader.equals(etagFromCache)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }

        if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("ETag", etagFromCache);
        responseHeaders.add("Accept", "application/json");
        responseHeaders.add("Content-Type", "application/json");

        return ResponseEntity.ok().headers(responseHeaders).body(new JSONObject(plan).toString());
    }

//    @DeleteMapping(value = "/plan/{id}")
//    public ResponseEntity<ResponseObject> deletePlan(@PathVariable String id,
//                                                     @RequestHeader HttpHeaders requestHeaders) {
//        String key = "plan:" + id;
//
//        if (!planService.ifKeyExists(key)) {
//            return createErrorResponse("Plan Id not found", HttpStatus.NOT_FOUND);
//        }
//
//        String etagFromCache = planService.accessEtag(key);
//        String ifMatchHeader = requestHeaders.getFirst("If-Match");
//
//        if (ifMatchHeader != null && !ifMatchHeader.equals(etagFromCache)) {
//            return createErrorResponse("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED);
//        }
//
//        String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");
//        if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache)) {
//            return createErrorResponse("", HttpStatus.NOT_MODIFIED);
//        }
//
//        Map<String, Object> plan = planService.getPlanById(key);
//        logAction(new JSONObject(plan).toString(), "DELETE");
//        planService.deletePlan(key);
//
//        return createErrorResponse("Plan deleted Successfully", HttpStatus.NO_CONTENT);
//    }

    @DeleteMapping(value = "/plan/{id}")
    public ResponseEntity<ResponseObject> deletePlan(@PathVariable String id, @RequestHeader HttpHeaders requestHeaders) {
        String key = "plan:" + id;

        // Check if the plan exists
        if (!planService.ifKeyExists(key)) {
            return createErrorResponse("Plan Id not found", HttpStatus.NOT_FOUND);
        }

//        // ETag handling for concurrency control
//        String etagFromCache = planService.accessEtag(key);
//        String ifMatchHeader = requestHeaders.getFirst("If-Match");
//        if (ifMatchHeader != null && !ifMatchHeader.equals(etagFromCache)) {
//            return createErrorResponse("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED);
//        }
//        String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");
//        if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache)) {
//            return createErrorResponse("", HttpStatus.NOT_MODIFIED);
//        }

        String etagFromCache = planService.accessEtag(key);

        String ifMatchHeader = requestHeaders.getFirst("If-Match");
        String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");

// Check that one of If-Match or If-None-Match headers must be present and not empty
        if ((ifMatchHeader == null || ifMatchHeader.isEmpty()) && (ifNoneMatchHeader == null || ifNoneMatchHeader.isEmpty())) {
            return createErrorResponse("ETag must be provided in either If-Match or If-None-Match headers", HttpStatus.BAD_REQUEST);
        }



        if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache)) {
            return createErrorResponse("Etag has not changed", HttpStatus.NOT_MODIFIED);
        }

        if (ifMatchHeader != null && !ifMatchHeader.equals(etagFromCache)) {
            return createErrorResponse("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED);
        }

        try {
            // Fetch and log the plan before deletion
            Map<String, Object> plan = planService.getPlanById(key);
            logAction(new JSONObject(plan).toString(), "DELETE");

            // Perform the deletion
            planService.deletePlan(key);

            // Publish a message to update Elasticsearch index
            publishPlanDeletedEvent(new JSONObject(plan), key);

            // Return successful response
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            // Handle exceptions and return appropriate error response
            return createErrorResponse("Error during deletion: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void publishPlanDeletedEvent(JSONObject planData, String objectKey) {
        // Logic to publish a delete event to the message queue
        Map<String, String> messageContent = new HashMap<>();
        messageContent.put("operation", "DELETE");
        messageContent.put("body", planData.toString());
        template.convertAndSend(RabbitMQConfiguration.topicExchangeName, RabbitMQConfiguration.queueName, messageContent);
    }


    @PatchMapping(value = "/plan/{id}")
    public ResponseEntity<ResponseObject> updatePlan(
            @PathVariable String id,
            @RequestBody String requestBody,
            @RequestHeader HttpHeaders requestHeaders) {

        String key = "plan:" + id;

        if (!planService.ifKeyExists(key)) {
            return createErrorResponse("Plan Id not found", HttpStatus.NOT_FOUND);
        }

//        String etagFromCache = planService.accessEtag(key);
//        String ifMatchHeader = requestHeaders.getFirst("If-Match");
//
//        if (ifMatchHeader != null && !ifMatchHeader.equals(etagFromCache)) {
//            return createErrorResponse("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED);
//        }
//
//        String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");
//        if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache)) {
//            return createErrorResponse("", HttpStatus.NOT_MODIFIED);
//        }

        String etagFromCache = planService.accessEtag(key);
//String ifMatchHeader = requestHeaders.getFirst("If-Match");

        String ifMatchHeader = requestHeaders.getFirst("If-Match");
        String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");

// Check that one of If-Match or If-None-Match headers must be present and not empty
        if ((ifMatchHeader == null || ifMatchHeader.isEmpty()) && (ifNoneMatchHeader == null || ifNoneMatchHeader.isEmpty())) {
            return createErrorResponse("ETag must be provided in either If-Match or If-None-Match headers", HttpStatus.BAD_REQUEST);
        }

        if (ifMatchHeader != null && !ifMatchHeader.equals(etagFromCache)) {
            return createErrorResponse("Etag mismatch: Provided ETag does not match", HttpStatus.PRECONDITION_FAILED);
        }

        if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache)) {
            return createErrorResponse("Etag has not changed", HttpStatus.NOT_MODIFIED);
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode requestBodyJson = objectMapper.readTree(requestBody);
            Map<String, Object> existingPlanMap = planService.getPlanById(key);

            //...
            System.out.println("Request body: " + requestBody);

            System.out.println("Existing plan: " + existingPlanMap);


            // Extract existing linkedPlanServices, or initialize to an empty list if null
            List<Map<String, Object>> existingLinkedPlanServices = (List<Map<String, Object>>) existingPlanMap.getOrDefault("linkedPlanServices", new ArrayList<>());

            // Get the new linkedPlanServices array from the request body JSON
            ArrayNode newLinkedPlanServices = (ArrayNode) requestBodyJson.get("linkedPlanServices");

            // Iterate through the new linkedPlanServices array, converting each object to a map and adding it to the existingLinkedPlanServices list
            for (JsonNode newLinkedPlanService : newLinkedPlanServices) {
                Map<String, Object> newLinkedPlanServiceMap = objectMapper.convertValue(newLinkedPlanService, new TypeReference<Map<String, Object>>() {});
                existingLinkedPlanServices.add(newLinkedPlanServiceMap);
            }

            // Update the map with the merged linkedPlanServices list
            existingPlanMap.put("linkedPlanServices", existingLinkedPlanServices);

            // Convert the updated map back to a JSONObject
            JSONObject updatedPlanJson = new JSONObject(objectMapper.writeValueAsString(existingPlanMap));

            Map<String, String> messageContent = new HashMap<>();
            messageContent.put("operation", "UPDATE");
            messageContent.put("body", updatedPlanJson.toString());

            template.convertAndSend(RabbitMQConfiguration.topicExchangeName, RabbitMQConfiguration.queueName, messageContent);

            // Use createPlan to save the updated plan
            String newEtag = planService.createPlan(updatedPlanJson, key);
            logAction(objectMapper.writeValueAsString(existingPlanMap), "PATCH");

            ResponseObject responseObject = new ResponseObject("Plan updated Successfully", HttpStatus.OK.value(), existingPlanMap.get("objectId"));
            return ResponseEntity.ok().eTag(newEtag).body(responseObject);

        } catch (JsonProcessingException e) {
            return createErrorResponse("Invalid JSON input: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (RuntimeException e) {
            return createErrorResponse("Server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value = "/plan/{id}")
    public ResponseEntity<ResponseObject> replacePlan(
            @PathVariable String id,
            @RequestBody String requestBody,
            @RequestHeader HttpHeaders requestHeaders) {

        String key = "plan:" + id;

        if (!planService.ifKeyExists(key)) {
            return createErrorResponse("Plan Id not found", HttpStatus.NOT_FOUND);
        }

        String etagFromCache = planService.accessEtag(key);
        String ifMatchHeader = requestHeaders.getFirst("If-Match");

        if (ifMatchHeader != null && !ifMatchHeader.equals(etagFromCache)) {
            return createErrorResponse("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED);
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode requestBodyJson = objectMapper.readTree(requestBody);

            // Convert JsonNode to JSONObject
            JSONObject requestBodyJSONObject = new JSONObject(objectMapper.writeValueAsString(requestBodyJson));


            // Now pass the JSONObject to the validateJSONSchema method
            if (!jsonSchemaValidatorService.validateJSONSchema(requestBodyJSONObject)) {
                return createErrorResponse("Invalid JSON schema", HttpStatus.BAD_REQUEST);
            }

            // Convert the requestBodyJson to a JSONObject
            JSONObject newPlanJson = new JSONObject(objectMapper.writeValueAsString(requestBodyJson));

            // Use createPlan to save the updated plan (assuming createPlan saves or updates the plan)
            String newEtag = planService.createPlan(newPlanJson, key);
            logAction(objectMapper.writeValueAsString(newPlanJson), "PUT");

            ResponseObject responseObject = new ResponseObject("Plan replaced Successfully", HttpStatus.OK.value(), newPlanJson.get("objectId"));
            return ResponseEntity.ok().eTag(newEtag).body(responseObject);

        } catch (JsonProcessingException e) {
            return createErrorResponse("Invalid JSON input: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (RuntimeException e) {
            return createErrorResponse("Server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



}

