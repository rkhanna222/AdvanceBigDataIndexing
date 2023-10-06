package com.neu.info7255.raghav.assignment.demo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.io.Serializable;


@Data
public class PlanCostShares implements Serializable {

    @Id
    private String objectId;

    private int deductible;

    private String _org;

    private int copay;

    private String objectType;


}
