package com.neu.info7255.raghav.assignment.demo.controller;

import com.neu.info7255.raghav.assignment.demo.model.ResponseObject;
import com.neu.info7255.raghav.assignment.demo.services.JsonSchemaValidatorService;
import com.neu.info7255.raghav.assignment.demo.services.PlanService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping(value = "/plan")
    public ResponseEntity<ResponseObject> createPlan(@RequestBody String request) {
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

            ResponseObject responseObject = new ResponseObject("Plan Added!", HttpStatus.CREATED.value(), jsonObjectPlan.get("objectId"));
            return ResponseEntity.created(new URI("/plan/" + objectKey))
                    .eTag(eTagGeneratedAfterSave)
                    .body(responseObject);

        } catch (Exception e) {
            return createErrorResponse(e.toString(), HttpStatus.BAD_REQUEST);
        }
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
    public ResponseEntity<ResponseObject> getAllPlans() {
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

    @DeleteMapping(value = "/plan/{id}")
    public ResponseEntity<ResponseObject> deletePlan(@PathVariable String id,
                                                     @RequestHeader HttpHeaders requestHeaders) {
        String key = "plan:" + id; // Constructing the key using the "plan" prefix and the provided id

        if (!planService.ifKeyExists(key)) {
            return createErrorResponse("Plan Id not found", HttpStatus.NOT_FOUND);
        }

        String etagFromCache = planService.accessEtag(key);
        String ifMatchHeader = requestHeaders.getFirst("If-Match");

        if (ifMatchHeader != null && !ifMatchHeader.equals(etagFromCache)) {
            return createErrorResponse("Pre Condition Failed", HttpStatus.PRECONDITION_FAILED);
        }

        String ifNoneMatchHeader = requestHeaders.getFirst("If-None-Match");
        if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(etagFromCache)) {
            return createErrorResponse("", HttpStatus.NOT_MODIFIED);
        }

        Map<String, Object> plan = planService.getPlanById(key);
        logAction(new JSONObject(plan).toString(), "DELETE");
        planService.deletePlan(key);

        return createErrorResponse("Plan deleted Successfully", HttpStatus.NO_CONTENT);
    }

}

