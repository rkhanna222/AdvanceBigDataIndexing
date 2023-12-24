package com.neu.info7255.raghav.assignment.demo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.io.Serializable;


@Data
public class LinkedPlanService implements Serializable {

    @Id
    private String objectId;

    LinkedService linkedService;

    PlanCostShares planserviceCostShares;

    private String _org;

    private String objectType;


}