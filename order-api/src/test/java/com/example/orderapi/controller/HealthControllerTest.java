package com.example.orderapi.controller;

import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.OrderRepository;
import com.example.orderapi.repository.ProductRepository;
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

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerRepository customerRepository;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private OrderRepository orderRepository;

    @Test
    void healthSummaryReturnsServiceNameTimestampAndCounts() throws Exception {
        when(customerRepository.count()).thenReturn(3L);
        when(productRepository.count()).thenReturn(7L);
        when(orderRepository.count()).thenReturn(11L);

        mockMvc.perform(get("/health-summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("order-api"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.customerCount").value(3))
                .andExpect(jsonPath("$.productCount").value(7))
                .andExpect(jsonPath("$.orderCount").value(11));
    }
}
