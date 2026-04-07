package com.example.orderapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createCustomer_shouldReturnCreatedCustomer() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": {
                                    "firstName": "Priya",
                                    "lastName": "Sharma"
                                  },
                                  "email":"priya@example.com",
                                  "phone":"+1-416-555-0147",
                                  "addresses":[
                                    {
                                      "type":"SHIPPING",
                                      "line1":"123 Main St",
                                      "line2":"Unit 5",
                                      "city":"Toronto",
                                      "state":"ON",
                                      "postalCode":"L6P 1A1",
                                      "country":"CA",
                                      "isDefault":true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name.firstName", is("Priya")))
                .andExpect(jsonPath("$.name.lastName", is("Sharma")))
                .andExpect(jsonPath("$.email", is("priya@example.com")))
                .andExpect(jsonPath("$.phone", is("+1-416-555-0147")))
                .andExpect(jsonPath("$.addresses[0].type", is("SHIPPING")))
                .andExpect(jsonPath("$.addresses[0].country", is("CA")));
    }

    @Test
    void createCustomer_shouldReturnBadRequest_whenMandatoryFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": {
                                    "firstName": "Priya",
                                    "lastName": "Sharma"
                                  },
                                  "email":"priya@example.com",
                                  "addresses":[
                                    {
                                      "type":"SHIPPING",
                                      "line1":"123 Main St",
                                      "city":"Toronto",
                                      "state":"ON",
                                      "postalCode":"L6P 1A1",
                                      "country":"CA",
                                      "isDefault":true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("phone: phone is required")));
    }
}
