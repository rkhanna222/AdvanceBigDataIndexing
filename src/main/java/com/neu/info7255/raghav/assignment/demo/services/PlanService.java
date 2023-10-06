package com.neu.info7255.raghav.assignment.demo.services;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlanService {


    private Jedis jedis;
    private ETagService eTagService;

    public PlanService(Jedis jedis, ETagService eTagService) {
        this.jedis = jedis;
        this.eTagService = eTagService;
    }

    public String createPlan(JSONObject jsonObjectPlan, String objectKey) {
        mapMapper(jsonObjectPlan);
        return this.updateEtag(objectKey, jsonObjectPlan);
    }

    public String updateEtag(String eTagKey, JSONObject eTagValue) {
        String eTag = eTagService.getETag(eTagValue);
        jedis.hset(eTagKey, "eTag", eTag);
        return eTag;
    }

    public boolean ifKeyExists(String objectKey) {
        return jedis.exists(objectKey);
    }

    /**
     * Maps a JSONObject to a Map structure and updates Redis accordingly.
     *
     * @param jsonObject The JSONObject to be mapped.
     * @return A map representation of the given JSONObject.
     * @throws JSONException If there's an error during JSON processing.
     */
    public Map<String, Map<String, Object>> mapMapper(JSONObject jsonObject) throws JSONException {

        Map<String, Map<String, Object>> objectMap = new HashMap<>();
        Map<String, Object> jsonValueMap = new HashMap<>();
        Iterator<String> jsonObjectKeyIterator = jsonObject.keys();

        String redisKey = jsonObject.get("objectType") + ":" + jsonObject.get("objectId");

        try {
            while (jsonObjectKeyIterator.hasNext()) {
                String objectKey = jsonObjectKeyIterator.next();
                Object objectValue = jsonObject.get(objectKey);

                if (objectValue instanceof JSONObject) {
                    Map<String, Map<String, Object>> objectValueMap = mapMapper((JSONObject) objectValue);
                    String nextKey = objectValueMap.keySet().iterator().next();
                    jedis.sadd(redisKey + ":" + objectKey, nextKey);
                    System.out.println("Inside Object, redisKey: " + redisKey + ":" + objectKey + ":" + nextKey);

                } else if (objectValue instanceof JSONArray) {
                    List<Map<String, Map<String, Object>>> listValue = listMapper((JSONArray) objectValue);
                    for (Map<String, Map<String, Object>> entryMap : listValue) {
                        for (String listKeyValue : entryMap.keySet()) {
                            jedis.sadd(redisKey + ":" + objectKey, listKeyValue);
                            System.out.println("Inside Array, redisKey: " + redisKey + ":" + objectKey + ":" + listKeyValue);
                        }
                    }

                } else {
                    jedis.hset(redisKey, objectKey, objectValue.toString());
                    System.out.println("RedisKey: " + redisKey + ", objKey: " + objectKey + ", val: " + objectValue);
                    jsonValueMap.put(objectKey, objectValue);
                    objectMap.put(redisKey, jsonValueMap);
                }
            }
        } finally {
            jedis.close();
        }

        return objectMap;
    }

    /**
     * Maps a JSONArray to a List structure.
     *
     * @param jsonArray The JSONArray to be mapped.
     * @return A list representation of the given JSONArray.
     * @throws JSONException If there's an error during JSON processing.
     */
    private List<Map<String, Map<String, Object>>> listMapper(JSONArray jsonArray) throws JSONException {
        List<Map<String, Map<String, Object>>> resultList = new ArrayList<>();

        for (Object objectValue : jsonArray) {
            if (objectValue instanceof JSONArray) {
                resultList.add(listMapper((JSONArray) objectValue).get(0)); // Assuming each JSONArray contains one item
            } else if (objectValue instanceof JSONObject) {
                resultList.add(mapMapper((JSONObject) objectValue));
            }
        }

        return resultList;
    }

    public String accessEtag(String eTagKey) {
        return jedis.hget(eTagKey, "eTag");
    }

    public Map<String, Object> getPlanById(String keyId) {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        accessOrPurgeData(keyId, resultMap, false);
        return resultMap;
    }

    private Map<String, Object> accessOrPurgeData(String redisKeyValue, Map<String, Object> resultMap, boolean isDeleteFlag) {
        Set<String> keySet = jedis.keys(redisKeyValue + ":*");
        keySet.add(redisKeyValue);

        try {
            for (String keyVal : keySet) {
                if (keyVal.equals(redisKeyValue)) {
                    handleRootKey(keyVal, resultMap, isDeleteFlag);
                } else {
                    handleNestedKey(redisKeyValue, keyVal, isDeleteFlag, resultMap);
                }
            }
        } finally {
            jedis.close();
        }

        return resultMap;
    }

    private void handleRootKey(String keyVal, Map<String, Object> resultMap, boolean isDeleteFlag) {
        if (isDeleteFlag) {
            jedis.del(keyVal);
        } else {
            Map<String, String> keyMap = jedis.hgetAll(keyVal);
            for (Map.Entry<String, String> entry : keyMap.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase("eTag")) {
                    resultMap.put(entry.getKey(), ifStringInteger(entry.getValue()) ? Integer.parseInt(entry.getValue()) : entry.getValue());
                }
            }
        }
    }

    private void handleNestedKey(String redisKeyValue, String keyVal, boolean isDeleteFlag, Map<String, Object> resultMap) {
        String updatedKey = keyVal.substring((redisKeyValue + ":").length());
        Set<String> keySetMembers = jedis.smembers(keyVal);

        if (keySetMembers.size() > 1 || updatedKey.equals("linkedPlanServices")) {
            handleMultipleKeys(keyVal, updatedKey, isDeleteFlag, resultMap);
        } else {
            handleSingleKey(keyVal, keySetMembers, updatedKey, isDeleteFlag, resultMap);
        }
    }

    private void handleMultipleKeys(String keyVal, String updatedKey, boolean isDeleteFlag, Map<String, Object> resultMap) {
        List<Object> resultList = new ArrayList<>();
        for (String keyMember : jedis.smembers(keyVal)) {
            if (isDeleteFlag) {
                accessOrPurgeData(keyMember, null, true);
            } else {
                Map<String, Object> objectMap = new HashMap<>();
                resultList.add(accessOrPurgeData(keyMember, objectMap, false));
            }
        }
        if (isDeleteFlag) {
            jedis.del(keyVal);
        } else {
            resultMap.put(updatedKey, resultList);
        }
    }

    private void handleSingleKey(String keyVal, Set<String> keySetMembers, String updatedKey, boolean isDeleteFlag, Map<String, Object> resultMap) {
        if (isDeleteFlag) {
            jedis.del(keySetMembers.iterator().next(), keyVal);
        } else {
            Map<String, String> val = jedis.hgetAll(keySetMembers.iterator().next());
            Map<String, Object> newMap = new HashMap<>();
            for (Map.Entry<String, String> entry : val.entrySet()) {
                newMap.put(entry.getKey(), ifStringInteger(entry.getValue()) ? Integer.parseInt(entry.getValue()) : entry.getValue());
            }
            resultMap.put(updatedKey, newMap);
        }
    }

    private boolean ifStringInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    public void deletePlan(String key) {
        accessOrPurgeData(key, null, true);
    }

    public List<Map<String, Object>> getAllPlans() {
        Set<String> keys = jedis.keys("plan:*").stream()
                .filter(s -> s.lastIndexOf(":") == s.indexOf(":"))
                .collect(Collectors.toSet());

        return keys.stream()
                .map(this::retrievePlanData)
                .collect(Collectors.toList());
    }

    private Map<String, Object> retrievePlanData(String key) {
        Map<String, Object> outputMap = new HashMap<>();
        accessOrPurgeData(key, outputMap, false);
        return outputMap;
    }

}
