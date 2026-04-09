package com.example.orderapi.controller;

import com.example.orderapi.dto.DecompositionRequest;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.service.DecompositionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/intake")
public class DecompositionController {

    private final DecompositionService decompositionService;

    public DecompositionController(DecompositionService decompositionService) {
        this.decompositionService = decompositionService;
    }

    @PostMapping("/decompose")
    public ResponseEntity<?> decompose(@RequestBody DecompositionRequest request) {
        try {
            return ResponseEntity.ok(decompositionService.decompose(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
