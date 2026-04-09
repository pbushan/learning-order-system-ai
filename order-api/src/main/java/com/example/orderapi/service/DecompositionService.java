package com.example.orderapi.service;

import com.example.orderapi.dto.DecompositionRequest;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.dto.StructuredIntakeData;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Service
public class DecompositionService {

    private final IntakeOpenAiClient intakeOpenAiClient;

    public DecompositionService(IntakeOpenAiClient intakeOpenAiClient) {
        this.intakeOpenAiClient = intakeOpenAiClient;
    }

    public DecompositionResponse decompose(DecompositionRequest request) {
        validateRequest(request);
        String requestId = request.getRequestId().trim();
        StructuredIntakeData structuredData = request.getStructuredData();
        try {
            DecompositionResponse response = intakeOpenAiClient.decompose(requestId, structuredData);
            return normalizeResponse(response, requestId);
        } catch (Exception ex) {
            return fallback(requestId);
        }
    }

    private void validateRequest(DecompositionRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestId())) {
            throw new IllegalArgumentException("requestId is required");
        }
        StructuredIntakeData structuredData = request.getStructuredData();
        if (structuredData == null) {
            throw new IllegalArgumentException("structuredData is required");
        }
        if (!StringUtils.hasText(structuredData.getTitle())) {
            throw new IllegalArgumentException("structuredData.title is required");
        }
        if (!StringUtils.hasText(structuredData.getDescription())) {
            throw new IllegalArgumentException("structuredData.description is required");
        }
    }

    private DecompositionResponse normalizeResponse(DecompositionResponse response, String requestId) {
        if (response == null) {
            return fallback(requestId);
        }
        if (!StringUtils.hasText(response.getRequestId())) {
            response.setRequestId(requestId);
        }
        if (response.getStories() == null) {
            response.setStories(Collections.emptyList());
        }
        return response;
    }

    private DecompositionResponse fallback(String requestId) {
        DecompositionResponse fallback = new DecompositionResponse();
        fallback.setRequestId(requestId);
        fallback.setDecompositionComplete(false);
        fallback.setStories(Collections.emptyList());
        return fallback;
    }
}
