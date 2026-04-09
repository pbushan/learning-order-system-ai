package com.example.orderapi.service;

import com.example.orderapi.dto.DecompositionRequest;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.dto.StructuredIntakeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Service
public class DecompositionService {

    private static final Logger log = LoggerFactory.getLogger(DecompositionService.class);

    private final IntakeOpenAiClient intakeOpenAiClient;
    private final FileAuditLogService fileAuditLogService;
    private final String decompositionModel;

    public DecompositionService(IntakeOpenAiClient intakeOpenAiClient,
                                FileAuditLogService fileAuditLogService,
                                @Value("${app.intake.decomposition.model:${app.intake.model:gpt-4.1-mini}}") String decompositionModel) {
        this.intakeOpenAiClient = intakeOpenAiClient;
        this.fileAuditLogService = fileAuditLogService;
        this.decompositionModel = decompositionModel;
    }

    public DecompositionResponse decompose(DecompositionRequest request) {
        validateRequest(request);
        String requestId = request.getRequestId().trim();
        StructuredIntakeData structuredData = request.getStructuredData();
        try {
            DecompositionResponse response = intakeOpenAiClient.decompose(requestId, structuredData);
            DecompositionResponse normalized = normalizeResponse(response, requestId);
            safeAuditLog(requestId, structuredData, normalized, null);
            return normalized;
        } catch (Exception ex) {
            log.warn("Decomposition call failed for requestId={}: {}", requestId, ex.getClass().getSimpleName());
            DecompositionResponse fallback = fallback(requestId);
            safeAuditLog(requestId, structuredData, fallback, ex.getClass().getSimpleName());
            return fallback;
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

    private void safeAuditLog(String requestId,
                              StructuredIntakeData structuredData,
                              DecompositionResponse response,
                              String error) {
        try {
            if (fileAuditLogService == null) {
                return;
            }
            fileAuditLogService.logDecompositionEntry(
                    requestId,
                    structuredData,
                    decompositionModel,
                    response != null ? response.isDecompositionComplete() : false,
                    response != null ? response.getStories() : Collections.emptyList(),
                    error
            );
        } catch (Exception ignored) {
            // Logging failures must never fail decomposition responses.
        }
    }
}
