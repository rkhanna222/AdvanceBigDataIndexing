package com.neu.info7255.raghav.assignment.demo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.io.Serializable;

@Data
public class LinkedService implements Serializable {

    @Id
    private String objectId;

    private String _org;

    private String name;

    private String objectType;


}

