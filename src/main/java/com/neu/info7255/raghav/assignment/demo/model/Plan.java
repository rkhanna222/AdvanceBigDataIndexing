package com.neu.info7255.raghav.assignment.demo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;

@Data
public class Plan implements Serializable {

    @Id
    @Indexed
    private String objectId;

    private PlanCostShares planCostShares;

    private LinkedPlanService[] linkedPlanServices;

    private String planType;

    private String creationDate;

    private String _org;

    private String objectType;


}