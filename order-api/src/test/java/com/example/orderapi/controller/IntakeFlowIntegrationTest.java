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

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({IntakeChatController.class, DecompositionController.class})
class IntakeFlowIntegrationTest {

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
    }
}
