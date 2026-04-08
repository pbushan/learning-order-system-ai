package com.example.orderapi.controller;

import com.example.orderapi.dto.IntakeChatRequest;
import com.example.orderapi.dto.IntakeChatResponse;
import com.example.orderapi.dto.StructuredIntakeData;
import com.example.orderapi.service.IntakeChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/api/intake")
public class IntakeChatController {

    private final IntakeChatService intakeChatService;

    public IntakeChatController(IntakeChatService intakeChatService) {
        this.intakeChatService = intakeChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<IntakeChatResponse> chat(@Valid @RequestBody IntakeChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        try {
            IntakeChatResponse response = intakeChatService.chat(requestId, request);
            return ResponseEntity.ok(response);
        } catch (IntakeChatService.IntakeConfigurationException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(serviceUnavailableResponse(requestId));
        } catch (IntakeChatService.IntakeProcessingException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(normalizeErrorResponse(ex.getFallbackResponse(), requestId));
        }
    }

    private IntakeChatResponse serviceUnavailableResponse(String requestId) {
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
        return response;
    }

    private IntakeChatResponse normalizeErrorResponse(IntakeChatResponse fallback, String requestId) {
        IntakeChatResponse response = fallback != null ? fallback : serviceUnavailableResponse(requestId);
        if (response.getRequestId() == null || response.getRequestId().isBlank()) {
            response.setRequestId(requestId);
        }
        StructuredIntakeData fallbackData = serviceUnavailableResponse(requestId).getStructuredData();
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
}
