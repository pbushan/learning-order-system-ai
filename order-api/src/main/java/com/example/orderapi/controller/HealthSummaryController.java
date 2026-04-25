package com.example.orderapi.controller;

import com.example.orderapi.dto.HealthSummaryResponse;
import com.example.orderapi.service.HealthSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health-summary")
public class HealthSummaryController {

    private final HealthSummaryService healthSummaryService;

    public HealthSummaryController(HealthSummaryService healthSummaryService) {
        this.healthSummaryService = healthSummaryService;
    }

    @GetMapping
    public HealthSummaryResponse getSummary() {
        return healthSummaryService.getSummary();
    }
}
