package com.example.orderapi.controller;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.DecisionTraceEventResponse;
import com.example.orderapi.dto.DecisionTraceResponse;
import com.example.orderapi.dto.IntakeChatRequest;
import com.example.orderapi.dto.IntakeChatResponse;
import com.example.orderapi.service.IntakeChatService;
import com.example.orderapi.service.IntakeTraceabilityAgent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeChatControllerTest {

    @Test
    void chat_preservesTraceIdFromServiceUnavailableException() {
        IntakeChatService intakeChatService = mock(IntakeChatService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        IntakeChatController controller = new IntakeChatController(intakeChatService, intakeTraceabilityAgent);

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
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        IntakeChatController controller = new IntakeChatController(intakeChatService, intakeTraceabilityAgent);

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

    @Test
    void trace_returnsTraceEventsForGivenTraceId() {
        IntakeChatService intakeChatService = mock(IntakeChatService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        IntakeChatController controller = new IntakeChatController(intakeChatService, intakeTraceabilityAgent);

        DecisionTraceEventResponse event = new DecisionTraceEventResponse();
        event.setTraceId("trace-123");
        event.setEventType("intake.session.started");
        event.setSummary("Started intake session capture.");
        when(intakeTraceabilityAgent.readTraceEvents("trace-123")).thenReturn(List.of(event));

        ResponseEntity<DecisionTraceResponse> response = controller.trace("trace-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("trace-123", response.getBody().getTraceId());
        assertEquals(1, response.getBody().getEvents().size());
        assertEquals("intake.session.started", response.getBody().getEvents().get(0).getEventType());
    }

    @Test
    void trace_trimsTraceIdBeforeReadingEvents() {
        IntakeChatService intakeChatService = mock(IntakeChatService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        IntakeChatController controller = new IntakeChatController(intakeChatService, intakeTraceabilityAgent);

        when(intakeTraceabilityAgent.readTraceEvents("trace-xyz")).thenReturn(List.of());

        ResponseEntity<DecisionTraceResponse> response = controller.trace("  trace-xyz  ");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("trace-xyz", response.getBody().getTraceId());
        assertTrue(response.getBody().getEvents().isEmpty());
        verify(intakeTraceabilityAgent).readTraceEvents("trace-xyz");
    }

    @Test
    void trace_withWhitespaceOnlyTraceId_returnsBadRequest() {
        IntakeChatService intakeChatService = mock(IntakeChatService.class);
        IntakeTraceabilityAgent intakeTraceabilityAgent = mock(IntakeTraceabilityAgent.class);
        IntakeChatController controller = new IntakeChatController(intakeChatService, intakeTraceabilityAgent);

        ResponseEntity<DecisionTraceResponse> response = controller.trace("   ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(intakeTraceabilityAgent, never()).readTraceEvents(anyString());
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
