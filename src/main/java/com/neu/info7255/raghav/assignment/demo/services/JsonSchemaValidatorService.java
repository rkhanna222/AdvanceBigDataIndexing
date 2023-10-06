package com.neu.info7255.raghav.assignment.demo.services;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class JsonSchemaValidatorService {

    private static final String SCHEMAPATH = "/schema/PlanSchema.json";
    private final Schema jsonSchema;

    public JsonSchemaValidatorService() {
        InputStream inputJsonStream = getClass().getResourceAsStream(SCHEMAPATH);
        JSONObject inputJsonObject = new JSONObject(new JSONTokener(inputJsonStream));
        jsonSchema = SchemaLoader.load(inputJsonObject);
    }

    public boolean validateJSONSchema(JSONObject data) {
        try {
            jsonSchema.validate(data);
            return true;
        } catch (ValidationException vex) {
            vex.printStackTrace();
            return false;
        }
    }
}

