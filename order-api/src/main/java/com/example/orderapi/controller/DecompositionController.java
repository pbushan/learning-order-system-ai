package com.example.orderapi.controller;

import com.example.orderapi.dto.DecompositionRequest;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.service.DecompositionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/intake")
public class DecompositionController {

    private final DecompositionService decompositionService;

    public DecompositionController(DecompositionService decompositionService) {
        this.decompositionService = decompositionService;
    }

    @PostMapping("/decompose")
    public ResponseEntity<DecompositionResponse> decompose(@RequestBody DecompositionRequest request) {
        try {
            return ResponseEntity.ok(decompositionService.decompose(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(validationFallback(request));
        }
    }

    private DecompositionResponse validationFallback(DecompositionRequest request) {
        DecompositionResponse response = new DecompositionResponse();
        String requestId = request != null ? request.getRequestId() : null;
        response.setRequestId((requestId != null && !requestId.isBlank()) ? requestId.trim() : "unknown-request");
        response.setDecompositionComplete(false);
        response.setStories(Collections.emptyList());
        return response;
    }
}
