package com.neu.info7255.raghav.assignment.demo.services;


import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ETagService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ETagService.class);
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private final MessageDigest md;

    public ETagService() {
        MessageDigest tempMd = null;
        try {
            tempMd = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException noSuchEx) {
            LOGGER.error("Error initializing SHA-256 MessageDigest", noSuchEx);
        }
        this.md = tempMd;
    }

    /**
     * Generates an ETag for a given JSONObject.
     *
     * @param json The JSONObject for which the ETag needs to be generated.
     * @return The generated ETag.
     */
    public String getETag(JSONObject json) {
        byte[] hash = md.digest(json.toString().getBytes(CHARSET));
        String encoded = Base64.getEncoder().encodeToString(hash);
        return "\"" + encoded + "\"";
    }
}

