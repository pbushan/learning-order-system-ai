package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DecompositionRequest {
    public DecompositionRequest() {
    }

    @JsonProperty("requestId")
    @NotBlank(message = "requestId is required")
    private String requestId;

    @JsonProperty("structuredData")
    @NotNull(message = "structuredData is required")
    @Valid
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
