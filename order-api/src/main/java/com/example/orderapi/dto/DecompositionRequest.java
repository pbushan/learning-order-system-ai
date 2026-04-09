package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class DecompositionRequest {
    @JsonProperty("requestId")
    @NotNull(message = "requestId is required")
    private String requestId;

    @JsonProperty("structuredData")
    @NotNull(message = "structuredData is required")
    private StructuredIntakeData structuredData;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public StructuredIntakeData getStructuredData() {
        return structuredData;
    }

    public void setStructuredData(StructuredIntakeData structuredData) {
        this.structuredData = structuredData;
    }
}
