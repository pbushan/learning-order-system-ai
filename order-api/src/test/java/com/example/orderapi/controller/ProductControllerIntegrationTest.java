package com.example.orderapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createProduct_shouldPersistWeightUsingMappedColumn() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "WM-12346",
                                  "name": "Wireless Mouse - Mapping Check",
                                  "description": "Ergonomic Bluetooth mouse",
                                  "category": "Electronics",
                                  "price": {
                                    "amount": 29.99,
                                    "currency": "USD"
                                  },
                                  "physical": {
                                    "weight": {
                                      "value": 0.2,
                                      "unit": "kg"
                                    },
                                    "dimensions": {
                                      "length": 10,
                                      "width": 6,
                                      "height": 4,
                                      "unit": "cm"
                                    }
                                  },
                                  "shipping": {
                                    "fragile": false,
                                    "hazmat": false,
                                    "requiresCooling": false,
                                    "maxStackable": 10
                                  },
                                  "status": {
                                    "active": true,
                                    "shippable": true
                                  },
                                  "tags": ["mouse", "wireless"]
                                }
                                """))
                .andExpect(status().isCreated());

        BigDecimal persistedWeight = jdbcTemplate.queryForObject(
                "SELECT weight_value FROM products WHERE sku = ?",
                BigDecimal.class,
                "WM-12346"
        );

        assertNotNull(persistedWeight);
        assertEquals(0, persistedWeight.compareTo(new BigDecimal("0.2")));
    }

    @Test
    void createProduct_shouldReturnCreatedProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "WM-12345",
                                  "name": "Wireless Mouse",
                                  "description": "Ergonomic Bluetooth mouse",
                                  "category": "Electronics",
                                  "price": {
                                    "amount": 29.99,
                                    "currency": "USD"
                                  },
                                  "physical": {
                                    "weight": {
                                      "value": 0.2,
                                      "unit": "kg"
                                    },
                                    "dimensions": {
                                      "length": 10,
                                      "width": 6,
                                      "height": 4,
                                      "unit": "cm"
                                    }
                                  },
                                  "shipping": {
                                    "fragile": false,
                                    "hazmat": false,
                                    "requiresCooling": false,
                                    "maxStackable": 10
                                  },
                                  "status": {
                                    "active": true,
                                    "shippable": true
                                  },
                                  "tags": ["mouse", "wireless"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is("WM-12345")))
                .andExpect(jsonPath("$.price.amount", is(29.99)))
                .andExpect(jsonPath("$.shipping.maxStackable", is(10)))
                .andExpect(jsonPath("$.status.active", is(true)))
                .andExpect(jsonPath("$.tags[0]", is("mouse")));
    }

    @Test
    void createProduct_shouldReturnBadRequest_whenMissingRequiredField() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "WM-12345",
                                  "name": "Wireless Mouse",
                                  "description": "Ergonomic Bluetooth mouse",
                                  "category": "Electronics",
                                  "price": {
                                    "amount": 29.99,
                                    "currency": "USD"
                                  },
                                  "physical": {
                                    "weight": {
                                      "value": 0.2,
                                      "unit": "kg"
                                    },
                                    "dimensions": {
                                      "length": 10,
                                      "width": 6,
                                      "height": 4,
                                      "unit": "cm"
                                    }
                                  },
                                  "status": {
                                    "active": true,
                                    "shippable": true
                                  },
                                  "tags": ["mouse", "wireless"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("shipping: shipping is required")));
    }
}
