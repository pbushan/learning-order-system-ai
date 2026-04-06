package com.example.orderapi.service;

import com.example.orderapi.dto.ShippingDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

import java.util.Map;

@Service
public class LambdaShippingService {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final String functionName;

    public LambdaShippingService(LambdaClient lambdaClient,
                                 ObjectMapper objectMapper,
                                 @Value("${app.lambda.function-name}") String functionName) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = objectMapper;
        this.functionName = functionName;
    }

    public ShippingDecision decideShipping(Long orderId, String productName, int quantity, String totalAmount) {
        try {
            Map<String, Object> payload = Map.of(
                    "orderId", orderId,
                    "productName", productName,
                    "quantity", quantity,
                    "totalAmount", totalAmount
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);

            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName)
                    .payload(SdkBytes.fromUtf8String(jsonPayload))
                    .build();

            String responseJson = lambdaClient.invoke(request).payload().asUtf8String();
            return objectMapper.readValue(responseJson, ShippingDecision.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke Lambda shipping function", ex);
        }
    }
}
