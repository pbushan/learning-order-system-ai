package com.example.orderapi.controller;

import com.example.orderapi.dto.DecisionTraceEventResponse;
import com.example.orderapi.dto.DecompositionRequest;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.dto.GitHubIssueSummary;
import com.example.orderapi.dto.IntakeChatRequest;
import com.example.orderapi.dto.IntakeChatResponse;
import com.example.orderapi.dto.StructuredIntakeData;
import com.example.orderapi.service.DecompositionService;
import com.example.orderapi.service.FileAuditLogService;
import com.example.orderapi.service.GitHubIssueCreationService;
import com.example.orderapi.service.IntakeChatService;
import com.example.orderapi.service.IntakeTraceabilityAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({IntakeChatController.class, DecompositionController.class})
class IntakeFlowIntegrationTest {
    private static final String INTAKE_NO_ACTIONABLE_MESSAGE =
            "Intake completed without actionable bug/feature details, so no GitHub issues were created.";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IntakeChatService intakeChatService;

    @MockBean
    private IntakeTraceabilityAgent intakeTraceabilityAgent;

    @MockBean
    private DecompositionService decompositionService;

    @MockBean
    private GitHubIssueCreationService gitHubIssueCreationService;

    @MockBean
    private FileAuditLogService fileAuditLogService;

    @Test
    void intakeChatToCompleteToGitHubToTraceRead_flowIsDeterministic() throws Exception {
        StructuredIntakeData structured = new StructuredIntakeData();
        structured.setType("bug");
        structured.setPriority("high");
        structured.setTitle("Checkout fails on submit");
        structured.setDescription("Users see timeout when placing an order");
        structured.setStepsToReproduce("Open checkout and submit cart");
        structured.setExpectedBehavior("Order submits without timeout");
        structured.setAffectedComponents(List.of("order-ui", "order-api"));

        IntakeChatResponse chatResponse = new IntakeChatResponse();
        chatResponse.setReply("Thanks, I captured enough detail to proceed.");
        chatResponse.setIntakeComplete(true);
        chatResponse.setStructuredData(structured);
        chatResponse.setRequestId("req-flow-1");
        chatResponse.setTraceId("trace-flow-1");
        when(intakeChatService.chat(any(String.class), any(IntakeChatRequest.class))).thenReturn(chatResponse);

        DecompositionStory story = new DecompositionStory();
        story.setStoryId("story-checkout-timeout");
        story.setTitle("Fix checkout timeout handling");
        story.setDescription("Stabilize checkout request path and timeout handling.");

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-flow-1");
        decompositionResponse.setTraceId("trace-flow-1");
        decompositionResponse.setDecompositionComplete(true);
        decompositionResponse.setStories(List.of(story));
        when(decompositionService.decompose(any(DecompositionRequest.class))).thenReturn(decompositionResponse);

        GitHubIssueSummary issue = new GitHubIssueSummary();
        issue.setIssueNumber(501L);
        issue.setIssueUrl("https://example.test/issues/501");
        issue.setTitle("Fix checkout timeout handling");

        GitHubIssueCreateResponse issueResponse = new GitHubIssueCreateResponse();
        issueResponse.setRequestId("req-flow-1");
        issueResponse.setTraceId("trace-flow-1");
        issueResponse.setIssuesCreated(true);
        issueResponse.setIssues(List.of(issue));
        when(gitHubIssueCreationService.createFromDecomposition(any(GitHubIssueCreateRequest.class))).thenReturn(issueResponse);

        DecisionTraceEventResponse eventA = new DecisionTraceEventResponse();
        eventA.setTraceId("trace-flow-1");
        eventA.setEventType("intake.session.started");
        DecisionTraceEventResponse eventB = new DecisionTraceEventResponse();
        eventB.setTraceId("trace-flow-1");
        eventB.setEventType("intake.decomposition.completed");
        DecisionTraceEventResponse eventC = new DecisionTraceEventResponse();
        eventC.setTraceId("trace-flow-1");
        eventC.setEventType("intake.github.issue-creation.completed");
        when(intakeTraceabilityAgent.readTraceEvents(eq("trace-flow-1"))).thenReturn(List.of(eventA, eventB, eventC));

        mockMvc.perform(post("/api/intake/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messages": [
                                    { "role": "user", "content": "Checkout fails when I place an order." }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intakeComplete").value(true))
                .andExpect(jsonPath("$.structuredData.type").value("bug"))
                .andExpect(jsonPath("$.traceId", not(blankString())));

        mockMvc.perform(post("/api/intake/complete-to-github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "req-flow-1",
                                  "traceId": "trace-flow-1",
                                  "structuredData": {
                                    "type": "bug",
                                    "priority": "high",
                                    "title": "Checkout fails on submit",
                                    "description": "Users see timeout when placing an order",
                                    "stepsToReproduce": "Open checkout and submit cart",
                                    "expectedBehavior": "Order submits without timeout",
                                    "affectedComponents": ["order-ui", "order-api"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuesCreated").value(true))
                .andExpect(jsonPath("$.issues[0].issueNumber").value(501));

        mockMvc.perform(get("/api/intake/trace/{traceId}", "trace-flow-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("trace-flow-1"))
                .andExpect(jsonPath("$.events[*].eventType", hasItem("intake.session.started")))
                .andExpect(jsonPath("$.events[*].eventType", hasItem("intake.decomposition.completed")))
                .andExpect(jsonPath("$.events[*].eventType", hasItem("intake.github.issue-creation.completed")));

        ArgumentCaptor<DecompositionRequest> decompositionRequestCaptor = ArgumentCaptor.forClass(DecompositionRequest.class);
        verify(decompositionService).decompose(decompositionRequestCaptor.capture());
        DecompositionRequest capturedDecompositionRequest = decompositionRequestCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("req-flow-1", capturedDecompositionRequest.getRequestId());
        org.junit.jupiter.api.Assertions.assertEquals("trace-flow-1", capturedDecompositionRequest.getTraceId());
        org.junit.jupiter.api.Assertions.assertEquals("bug", capturedDecompositionRequest.getStructuredData().getType());

        ArgumentCaptor<String> intakeRequestIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IntakeChatRequest> intakeChatRequestCaptor = ArgumentCaptor.forClass(IntakeChatRequest.class);
        verify(intakeChatService).chat(intakeRequestIdCaptor.capture(), intakeChatRequestCaptor.capture());
        org.junit.jupiter.api.Assertions.assertFalse(intakeRequestIdCaptor.getValue().isBlank());
        IntakeChatRequest capturedChatRequest = intakeChatRequestCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(1, capturedChatRequest.getMessages().size());
        org.junit.jupiter.api.Assertions.assertEquals("user", capturedChatRequest.getMessages().get(0).getRole());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Checkout fails when I place an order.",
                capturedChatRequest.getMessages().get(0).getContent()
        );

        ArgumentCaptor<GitHubIssueCreateRequest> issueRequestCaptor = ArgumentCaptor.forClass(GitHubIssueCreateRequest.class);
        verify(gitHubIssueCreationService).createFromDecomposition(issueRequestCaptor.capture());
        GitHubIssueCreateRequest capturedIssueRequest = issueRequestCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("req-flow-1", capturedIssueRequest.getRequestId());
        org.junit.jupiter.api.Assertions.assertEquals("trace-flow-1", capturedIssueRequest.getTraceId());
        org.junit.jupiter.api.Assertions.assertEquals("bug", capturedIssueRequest.getSourceType());
        org.junit.jupiter.api.Assertions.assertEquals(1, capturedIssueRequest.getStories().size());

        verify(intakeTraceabilityAgent).readTraceEvents("trace-flow-1");
    }

    @Test
    void completeToGitHub_returnsBadRequestWhenStructuredTypeMissing_andDoesNotInvokeIssueCreation() throws Exception {
        DecompositionStory story = new DecompositionStory();
        story.setStoryId("story-negative");
        story.setTitle("Fallback");
        story.setDescription("Fallback");

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-negative");
        decompositionResponse.setTraceId("trace-negative");
        decompositionResponse.setDecompositionComplete(true);
        decompositionResponse.setStories(List.of(story));
        when(decompositionService.decompose(any(DecompositionRequest.class))).thenReturn(decompositionResponse);

        mockMvc.perform(post("/api/intake/complete-to-github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "req-negative",
                                  "traceId": "trace-negative",
                                  "structuredData": {
                                    "description": "Type is intentionally missing for validation path coverage."
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Error-Message", INTAKE_NO_ACTIONABLE_MESSAGE))
                .andExpect(jsonPath("$.requestId").value("req-negative"))
                .andExpect(jsonPath("$.traceId").value("trace-negative"))
                .andExpect(jsonPath("$.issuesCreated").value(false))
                .andExpect(jsonPath("$.error").value(INTAKE_NO_ACTIONABLE_MESSAGE));

        verify(decompositionService).decompose(any(DecompositionRequest.class));
        verify(gitHubIssueCreationService, never()).createFromDecomposition(any(GitHubIssueCreateRequest.class));
    }
}
