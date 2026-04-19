package com.example.orderapi.controller;

import com.example.orderapi.dto.DecisionTraceResponse;
import com.example.orderapi.dto.IntakeChatRequest;
import com.example.orderapi.dto.IntakeChatResponse;
import com.example.orderapi.dto.StructuredIntakeData;
import com.example.orderapi.service.IntakeChatService;
import com.example.orderapi.service.IntakeTraceabilityAgent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/api/intake")
public class IntakeChatController {

    private final IntakeChatService intakeChatService;
    private final IntakeTraceabilityAgent intakeTraceabilityAgent;

    public IntakeChatController(IntakeChatService intakeChatService,
                                IntakeTraceabilityAgent intakeTraceabilityAgent) {
        this.intakeChatService = intakeChatService;
        this.intakeTraceabilityAgent = intakeTraceabilityAgent;
    }

    @PostMapping("/chat")
    public ResponseEntity<IntakeChatResponse> chat(@Valid @RequestBody IntakeChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        try {
            IntakeChatResponse response = intakeChatService.chat(requestId, request);
            return ResponseEntity.ok(response);
        } catch (IntakeChatService.IntakeConfigurationException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(serviceUnavailableResponse(requestId, resolveTraceId(request, requestId, ex.getTraceId())));
        } catch (IntakeChatService.IntakeProcessingException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(normalizeErrorResponse(ex.getFallbackResponse(), requestId, resolveTraceId(request, requestId, null)));
        }
    }

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<DecisionTraceResponse> trace(@PathVariable String traceId) {
        String normalizedTraceId = traceId != null ? traceId.trim() : "";
        if (!StringUtils.hasText(normalizedTraceId)) {
            DecisionTraceResponse response = new DecisionTraceResponse();
            response.setTraceId("");
            response.setEvents(Collections.emptyList());
            return ResponseEntity.badRequest().body(response);
        }
        DecisionTraceResponse response = new DecisionTraceResponse();
        response.setTraceId(normalizedTraceId);
        response.setEvents(intakeTraceabilityAgent.readTraceEvents(normalizedTraceId));
        return ResponseEntity.ok(response);
    }

    private IntakeChatResponse serviceUnavailableResponse(String requestId, String traceId) {
        IntakeChatResponse response = new IntakeChatResponse();
        response.setReply("Service unavailable. Please try again shortly.");
        response.setIntakeComplete(false);
        StructuredIntakeData data = new StructuredIntakeData();
        data.setTitle("");
        data.setDescription("");
        data.setStepsToReproduce("");
        data.setExpectedBehavior("");
        data.setAffectedComponents(Collections.emptyList());
        response.setStructuredData(data);
        response.setRequestId(requestId);
        response.setTraceId(traceId);
        return response;
    }

    private IntakeChatResponse normalizeErrorResponse(IntakeChatResponse fallback, String requestId, String defaultTraceId) {
        IntakeChatResponse response = fallback != null ? fallback : serviceUnavailableResponse(requestId, defaultTraceId);
        if (response.getRequestId() == null || response.getRequestId().isBlank()) {
            response.setRequestId(requestId);
        }
        if (response.getTraceId() == null || response.getTraceId().isBlank()) {
            response.setTraceId(defaultTraceId);
        }
        StructuredIntakeData fallbackData = serviceUnavailableResponse(requestId, defaultTraceId).getStructuredData();
        if (fallbackData == null) {
            fallbackData = new StructuredIntakeData();
            fallbackData.setTitle("");
            fallbackData.setDescription("");
            fallbackData.setStepsToReproduce("");
            fallbackData.setExpectedBehavior("");
            fallbackData.setAffectedComponents(Collections.emptyList());
        }
        StructuredIntakeData data = response.getStructuredData() != null ? response.getStructuredData() : fallbackData;
        if (data == null) {
            data = fallbackData;
        }
        if (data.getTitle() == null) {
            data.setTitle("");
        }
        if (data.getDescription() == null) {
            data.setDescription("");
        }
        if (data.getStepsToReproduce() == null) {
            data.setStepsToReproduce("");
        }
        if (data.getExpectedBehavior() == null) {
            data.setExpectedBehavior("");
        }
        if (data.getAffectedComponents() == null) {
            data.setAffectedComponents(Collections.emptyList());
        }
        response.setStructuredData(data);
        return response;
    }

    private String resolveTraceId(IntakeChatRequest request, String requestId, String preferredTraceId) {
        if (preferredTraceId != null && !preferredTraceId.isBlank()) {
            return preferredTraceId.trim();
        }
        String requestTraceId = request != null ? request.getTraceId() : null;
        if (requestTraceId != null && !requestTraceId.isBlank()) {
            return requestTraceId.trim();
        }
        return "trace-" + requestId;
    }
}
