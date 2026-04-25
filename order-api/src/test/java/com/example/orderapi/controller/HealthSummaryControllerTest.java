package com.example.orderapi.controller;

import com.example.orderapi.service.HealthSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthSummaryController.class)
class HealthSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthSummaryService healthSummaryService;

    @Test
    void getSummaryReturnsCounts() throws Exception {
        when(healthSummaryService.getSummary())
                .thenReturn(new com.example.orderapi.dto.HealthSummaryResponse(3L, 5L, 8L));

        mockMvc.perform(get("/api/health-summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(3))
                .andExpect(jsonPath("$.totalProducts").value(5))
                .andExpect(jsonPath("$.totalCustomers").value(8));
    }
}
