package com.neu.info7255.raghav.assignment.demo.services;

/**
 * @author Raghav Khanna
 */

import org.apache.http.HttpHost;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;


import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class IndexingListener {

    private static RestHighLevelClient client = new RestHighLevelClient(
            RestClient.builder(new HttpHost("localhost", 9200, "http")));
    private static final String IndexName = "indexplan";


    public void receiveMessage(Map<String, String> message) throws IOException {
        System.out.println("Message received: " + message);

        if (message == null || !message.containsKey("operation") || !message.containsKey("body")) {
            System.out.println("Invalid message format: " + message);
            return; // Skip processing if the message format is invalid
        }

        String operation = message.get("operation");
        if (operation == null) {
            System.out.println("Operation is null in the message: " + message);
            return; // Skip processing if operation is null
        }

        System.out.println("Operation to be performed: " + operation);
        String body = message.get("body");

        try {
            JSONObject jsonBody = new JSONObject(body); // This can throw JSONException if body is null or not a valid JSON

            switch (operation) {
                case "SAVE":
                case "UPDATE":
                    indexDocument(jsonBody);
                    break;
                case "DELETE":
                    deleteDocument(jsonBody);
                    break;
                default:
                    System.out.println("Unsupported operation: " + operation);
            }
        } catch (Exception e) {
            System.out.println("Error processing message: " + message);
            e.printStackTrace();
        }
    }


    public static void indexDocument(JSONObject plan) throws IOException {
        try{
            if (!indexExists()) {
                createElasticIndex();
            }

            Map<String, Map<String, Object>> MapOfDocuments = new HashMap<>();
            convertMapToDocumentIndex(plan, "", "plan", MapOfDocuments);
            //        System.out.println("------------------MapOfDocuments-------------------------");
            //        System.out.println(MapOfDocuments.toString());
            //        System.out.println("------------------newMap-------------------------");
            //        System.out.println(newMap.toString());
            for (Map.Entry<String, Map<String, Object>> entry : MapOfDocuments.entrySet()) {
//                System.out.println("------------------entry-------------------------");
//                System.out.println(entry);

                String parentId = entry.getKey().split(":")[0];
                String objectId = entry.getKey().split(":")[1];
                IndexRequest request = new IndexRequest(IndexName);
                request.id(objectId);
                request.source(entry.getValue());
                request.routing(parentId);
                request.setRefreshPolicy("wait_for");
//                System.out.println("------------------request-------------------------");
//                System.out.println(request);
                IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT);
                System.out.println("response id: " + indexResponse.getId() + " parent id: " + parentId);

            }
        }catch (Exception e){
            System.out.println("Post operation failed");
            System.out.println(e.getMessage());
        }
    }

    private static void deleteDocument(JSONObject jsonObject) throws IOException {

        ArrayList<String> listOfKeys = new ArrayList<>();
        convertToKeys(jsonObject, listOfKeys);
        for (String key : listOfKeys) {
            DeleteRequest request = new DeleteRequest(IndexName, key);
            DeleteResponse deleteResponse = client.delete(
                    request, RequestOptions.DEFAULT);
            if (deleteResponse.getResult() == DocWriteResponse.Result.NOT_FOUND) {
                System.out.println("Document " + key + " Not Found!!");
            }
        }
    }

    private static Map<String, Map<String, Object>> convertToKeys(JSONObject jsonObject, ArrayList listOfKeys) {

        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();
        Iterator<String> iterator = jsonObject.keys();
        while (iterator.hasNext()) {

            String key = iterator.next();
            String redisKey = jsonObject.get("objectId").toString();
            Object value = jsonObject.get(key);

            if (value instanceof JSONObject) {

                convertToKeys((JSONObject) value, listOfKeys);

            } else if (value instanceof JSONArray) {

                convertToKeysList((JSONArray) value, listOfKeys);

            } else {
                valueMap.put(key, value);
                map.put(redisKey, valueMap);
            }
        }

        listOfKeys.add(jsonObject.get("objectId").toString());
        return map;

    }

    private static List<Object> convertToKeysList(JSONArray array, ArrayList listOfKeys) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = convertToKeysList((JSONArray) value, listOfKeys);
            } else if (value instanceof JSONObject) {
                value = convertToKeys((JSONObject) value, listOfKeys);
            }
            list.add(value);
        }
        return list;
    }


    private static Map<String, Map<String, Object>> convertMapToDocumentIndex(JSONObject jsonObject,
                                                                              String parentId, String objectName, Map<String, Map<String, Object>> MapOfDocuments) {

        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();
        Iterator<String> iterator = jsonObject.keys();

        while (iterator.hasNext()) {

            String key = iterator.next();
            String redisKey = jsonObject.get("objectType") + ":" + parentId;
            Object value = jsonObject.get(key);

            if (value instanceof JSONObject) {

                convertMapToDocumentIndex((JSONObject) value, jsonObject.get("objectId").toString(), key.toString(), MapOfDocuments);

            } else if (value instanceof JSONArray) {

                convertToList((JSONArray) value, jsonObject.get("objectId").toString(), key.toString(), MapOfDocuments);

            } else {
                valueMap.put(key, value);
                map.put(redisKey, valueMap);
            }
        }

        Map<String, Object> temp = new HashMap<>();
        if (objectName == "plan") {
            valueMap.put("plan_join", objectName);
        } else {
            temp.put("name", objectName);
            temp.put("parent", parentId);
            valueMap.put("plan_join", temp);
        }

        String id = parentId + ":" + jsonObject.get("objectId").toString();
//        System.out.println("11111================================================");
        MapOfDocuments.put(id, valueMap);
//        System.out.println(MapOfDocuments);
//        System.out.println("11111======map==========================================");
//        System.out.println(map);
        return MapOfDocuments;
    }

    private static List<Object> convertToList(JSONArray array, String parentId, String objectName, Map<String, Map<String, Object>> MapOfDocuments) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = convertToList((JSONArray) value, parentId, objectName, MapOfDocuments);
            } else if (value instanceof JSONObject) {
                value = convertMapToDocumentIndex((JSONObject) value, parentId, objectName, MapOfDocuments);
            }
            list.add(value);
        }
        return list;
    }

    private static boolean indexExists() throws IOException {
        GetIndexRequest request = new GetIndexRequest(IndexName);
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        return exists;
    }


    private static void createElasticIndex() throws IOException {
        CreateIndexRequest request = new CreateIndexRequest(IndexName);
        request.settings(Settings.builder()
                .put("index.number_of_shards", 1)
                .put("index.number_of_replicas", 1));

        XContentBuilder mapping = getMapping();
        String mappingJson = Strings.toString(mapping); // Convert XContentBuilder to JSON String

        request.mapping(mappingJson, XContentType.JSON); // Use the JSON string for mapping
        CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);

        boolean acknowledged = createIndexResponse.isAcknowledged();
        System.out.println("Index Creation: " + acknowledged);
    }




    private static XContentBuilder getMapping() throws IOException {

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        {
            builder.startObject("properties");
            {
                builder.startObject("plan");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("planType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("creationDate");
                        {
                            builder.field("type", "date");
                            builder.field("format", "MM-dd-yyyy");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("planCostShares");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("copay");
                        {
                            builder.field("type", "long");
                        }
                        builder.endObject();
                        builder.startObject("deductible");
                        {
                            builder.field("type", "long");
                        }
                        builder.endObject();
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("linkedPlanServices");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("linkedService");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("name");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("planserviceCostShares");
                {
                    builder.startObject("properties");
                    {
                        builder.startObject("copay");
                        {
                            builder.field("type", "long");
                        }
                        builder.endObject();
                        builder.startObject("deductible");
                        {
                            builder.field("type", "long");
                        }
                        builder.endObject();
                        builder.startObject("_org");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                        builder.startObject("objectId");
                        {
                            builder.field("type", "keyword");
                        }
                        builder.endObject();
                        builder.startObject("objectType");
                        {
                            builder.field("type", "text");
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                }
                builder.endObject();
                builder.startObject("plan_join");
                {
                    builder.field("type", "join");
                    builder.field("eager_global_ordinals", "true");
                    builder.startObject("relations");
                    {
                        builder.array("plan", "planCostShares", "linkedPlanServices");
                        builder.array("linkedPlanServices", "linkedService", "planserviceCostShares");
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            builder.endObject();
        }
        builder.endObject();

        return builder;

    }

}
