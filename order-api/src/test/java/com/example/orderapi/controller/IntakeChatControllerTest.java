package com.example.orderapi.controller;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.IntakeChatRequest;
import com.example.orderapi.dto.IntakeChatResponse;
import com.example.orderapi.service.IntakeChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntakeChatControllerTest {

    @Test
    void chat_preservesTraceIdFromServiceUnavailableException() {
        IntakeChatService intakeChatService = mock(IntakeChatService.class);
        IntakeChatController controller = new IntakeChatController(intakeChatService);

        when(intakeChatService.chat(anyString(), any(IntakeChatRequest.class)))
                .thenThrow(new IntakeChatService.IntakeConfigurationException("missing api key", "trace-custom-123"));

        IntakeChatRequest request = sampleRequest("trace-from-request");
        ResponseEntity<IntakeChatResponse> response = controller.chat(request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("trace-custom-123", response.getBody().getTraceId());
    }

    @Test
    void chat_usesRequestTraceIdWhenFallbackResponseMissingTraceId() {
        IntakeChatService intakeChatService = mock(IntakeChatService.class);
        IntakeChatController controller = new IntakeChatController(intakeChatService);

        IntakeChatResponse fallback = new IntakeChatResponse();
        fallback.setReply("fallback");
        fallback.setIntakeComplete(false);
        fallback.setRequestId("req-fallback");
        fallback.setStructuredData(null);

        when(intakeChatService.chat(anyString(), any(IntakeChatRequest.class)))
                .thenThrow(new IntakeChatService.IntakeProcessingException(fallback));

        IntakeChatRequest request = sampleRequest("trace-from-request");
        ResponseEntity<IntakeChatResponse> response = controller.chat(request);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("trace-from-request", response.getBody().getTraceId());
        assertTrue(response.getBody().getStructuredData() != null);
    }

    private IntakeChatRequest sampleRequest(String traceId) {
        IntakeChatRequest request = new IntakeChatRequest();
        request.setTraceId(traceId);
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent("help");
        request.setMessages(List.of(message));
        return request;
    }
}
