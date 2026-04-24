package com.example.orderapi.controller;

import com.example.orderapi.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return HealthResponse.ok("order-api");
    }
}
