package com.neu.info7255.raghav.assignment.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseObject {

    @NonNull
    private String message;

    @NonNull
    private int statusCode;

    private Object data;

    public ResponseObject(@NonNull String message, @NonNull int statusCode) {
        this.message = message;
        this.statusCode = statusCode;
    }

    @Override
    public String toString() {
        return "ResponseObject{" +
                " message: '" + message + '\'' +
                ", status_code: " + statusCode +
                ", Data: " + data +
                '}';
    }
}

