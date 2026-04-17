package com.example.orderapi.service;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.DecompositionRequest;
import com.example.orderapi.dto.DecompositionResponse;
import com.example.orderapi.dto.DecompositionStory;
import com.example.orderapi.dto.GitHubIssueCreateRequest;
import com.example.orderapi.dto.GitHubIssueCreateResponse;
import com.example.orderapi.dto.GitHubIssueSummary;
import com.example.orderapi.dto.IntakeChatRequest;
import com.example.orderapi.dto.IntakeChatResponse;
import com.example.orderapi.dto.StructuredIntakeData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeTraceabilityAgentIntegrationTest {

    @Test
    void emitsTraceEventsAcrossRealisticIntakeLifecycle() throws Exception {
        Path traceLogPath = Files.createTempFile("decision-trace", ".jsonl");
        ObjectMapper objectMapper = new ObjectMapper();
        IntakeTraceabilityAgent traceabilityAgent = new IntakeTraceabilityAgent(objectMapper, traceLogPath.toString());

        IntakeOpenAiClient intakeOpenAiClient = mock(IntakeOpenAiClient.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);

        IntakeChatService intakeChatService = new IntakeChatService(
                intakeOpenAiClient,
                fileAuditLogService,
                traceabilityAgent,
                "gpt-4.1-mini"
        );
        DecompositionService decompositionService = new DecompositionService(
                intakeOpenAiClient,
                fileAuditLogService,
                traceabilityAgent,
                "gpt-4.1-mini"
        );
        GitHubIssueCreationService issueCreationService = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                traceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        StructuredIntakeData structuredData = structuredData("bug", "Checkout fails", "Checkout request times out");
        when(intakeOpenAiClient.collectIntake(any()))
                .thenReturn(new IntakeOpenAiClient.NormalizedIntakeResult("Captured intake", true, structuredData));

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-123");
        decompositionResponse.setDecompositionComplete(true);
        decompositionResponse.setStories(List.of(story("story-1")));
        when(intakeOpenAiClient.decompose(eq("req-123"), any(StructuredIntakeData.class)))
                .thenReturn(decompositionResponse);

        GitHubIssueSummary issueSummary = new GitHubIssueSummary();
        issueSummary.setIssueNumber(321L);
        issueSummary.setIssueUrl("https://example.test/issues/321");
        issueSummary.setTitle("Fix checkout timeout");
        when(gitHubIssueClientService.createIssueForStory(eq("bug"), any(DecompositionStory.class)))
                .thenReturn(issueSummary);

        IntakeChatRequest chatRequest = new IntakeChatRequest();
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole("user");
        chatMessage.setContent("Checkout fails intermittently");
        chatRequest.setMessages(List.of(chatMessage));

        IntakeChatResponse chatResponse = intakeChatService.chat("req-123", chatRequest);
        assertNotNull(chatResponse.getTraceId());
        assertFalse(chatResponse.getTraceId().isBlank());

        DecompositionRequest decompositionRequest = new DecompositionRequest();
        decompositionRequest.setRequestId("req-123");
        decompositionRequest.setTraceId(chatResponse.getTraceId());
        decompositionRequest.setStructuredData(chatResponse.getStructuredData());
        DecompositionResponse decomposeResult = decompositionService.decompose(decompositionRequest);

        GitHubIssueCreateRequest issueRequest = new GitHubIssueCreateRequest();
        issueRequest.setRequestId("req-123");
        issueRequest.setTraceId(decomposeResult.getTraceId());
        issueRequest.setSourceType("bug");
        issueRequest.setStories(decomposeResult.getStories());
        GitHubIssueCreateResponse issueResponse = issueCreationService.createFromDecomposition(issueRequest);

        assertEquals(chatResponse.getTraceId(), decomposeResult.getTraceId());
        assertEquals(chatResponse.getTraceId(), issueResponse.getTraceId());
        assertTrue(issueResponse.isIssuesCreated());

        List<JsonNode> events = readEvents(traceLogPath, objectMapper);
        Set<String> eventTypes = events.stream()
                .map(event -> event.path("eventType").asText(""))
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(eventTypes.contains("intake.session.started"));
        assertTrue(eventTypes.contains("intake.classification.completed"));
        assertTrue(eventTypes.contains("intake.structured-data.captured"));
        assertTrue(eventTypes.contains("intake.decomposition.completed"));
        assertTrue(eventTypes.contains("intake.github.payload.prepared"));
        assertTrue(eventTypes.contains("intake.github.issue-creation.completed"));
        assertTrue(eventTypes.contains("intake.github.summary-comment.completed"));
        assertTrue(events.stream().allMatch(event -> chatResponse.getTraceId().equals(event.path("traceId").asText(""))));
        verify(gitHubIssueClientService, times(1))
                .addIssueComment(eq(321L), org.mockito.ArgumentMatchers.contains("Generated via agent-assisted intake."));
    }

    @Test
    void emitsFailedIssueCreationEventWhenGitHubCallThrows() throws Exception {
        Path traceLogPath = Files.createTempFile("decision-trace-failure", ".jsonl");
        ObjectMapper objectMapper = new ObjectMapper();
        IntakeTraceabilityAgent traceabilityAgent = new IntakeTraceabilityAgent(objectMapper, traceLogPath.toString());

        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        GitHubIssueCreationService issueCreationService = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                traceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        when(gitHubIssueClientService.createIssueForStory(eq("feature"), any(DecompositionStory.class)))
                .thenThrow(new IllegalStateException("token missing"));

        GitHubIssueCreateRequest request = new GitHubIssueCreateRequest();
        request.setRequestId("req-failure");
        request.setTraceId("trace-req-failure");
        request.setSourceType("feature");
        request.setStories(List.of(story("story-failure")));

        assertThrows(IllegalStateException.class, () -> issueCreationService.createFromDecomposition(request));

        List<JsonNode> events = readEvents(traceLogPath, objectMapper);
        Set<String> eventTypes = events.stream()
                .map(event -> event.path("eventType").asText(""))
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(eventTypes.contains("intake.github.payload.prepared"));
        assertTrue(eventTypes.contains("intake.github.issue-creation.failed"));
    }

    @Test
    void emitsCommentsForEachIssueInDecomposedMultiIssueFlow() throws Exception {
        Path traceLogPath = Files.createTempFile("decision-trace-multi", ".jsonl");
        ObjectMapper objectMapper = new ObjectMapper();
        IntakeTraceabilityAgent traceabilityAgent = new IntakeTraceabilityAgent(objectMapper, traceLogPath.toString());

        IntakeOpenAiClient intakeOpenAiClient = mock(IntakeOpenAiClient.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);

        IntakeChatService intakeChatService = new IntakeChatService(
                intakeOpenAiClient,
                fileAuditLogService,
                traceabilityAgent,
                "gpt-4.1-mini"
        );
        DecompositionService decompositionService = new DecompositionService(
                intakeOpenAiClient,
                fileAuditLogService,
                traceabilityAgent,
                "gpt-4.1-mini"
        );
        GitHubIssueCreationService issueCreationService = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                traceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        StructuredIntakeData structuredData = structuredData("feature", "Order filters", "Add advanced order filters");
        when(intakeOpenAiClient.collectIntake(any()))
                .thenReturn(new IntakeOpenAiClient.NormalizedIntakeResult("Captured intake", true, structuredData));

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-multi");
        decompositionResponse.setDecompositionComplete(true);
        decompositionResponse.setStories(List.of(story("story-a"), story("story-b")));
        when(intakeOpenAiClient.decompose(eq("req-multi"), any(StructuredIntakeData.class)))
                .thenReturn(decompositionResponse);

        GitHubIssueSummary issueA = new GitHubIssueSummary();
        issueA.setIssueNumber(901L);
        issueA.setIssueUrl("https://example.test/issues/901");
        issueA.setTitle("Implement order filter API");
        GitHubIssueSummary issueB = new GitHubIssueSummary();
        issueB.setIssueNumber(902L);
        issueB.setIssueUrl("https://example.test/issues/902");
        issueB.setTitle("Add order filter UI");
        when(gitHubIssueClientService.createIssueForStory(eq("feature"), any(DecompositionStory.class)))
                .thenReturn(issueA, issueB);

        IntakeChatRequest chatRequest = new IntakeChatRequest();
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole("user");
        chatMessage.setContent("Need advanced filtering for orders");
        chatRequest.setMessages(List.of(chatMessage));

        IntakeChatResponse chatResponse = intakeChatService.chat("req-multi", chatRequest);

        DecompositionRequest decompositionRequest = new DecompositionRequest();
        decompositionRequest.setRequestId("req-multi");
        decompositionRequest.setTraceId(chatResponse.getTraceId());
        decompositionRequest.setStructuredData(chatResponse.getStructuredData());
        DecompositionResponse decomposeResult = decompositionService.decompose(decompositionRequest);

        GitHubIssueCreateRequest issueRequest = new GitHubIssueCreateRequest();
        issueRequest.setRequestId("req-multi");
        issueRequest.setTraceId(decomposeResult.getTraceId());
        issueRequest.setSourceType("feature");
        issueRequest.setStories(decomposeResult.getStories());
        GitHubIssueCreateResponse issueResponse = issueCreationService.createFromDecomposition(issueRequest);

        assertTrue(issueResponse.isIssuesCreated());
        assertEquals(2, issueResponse.getIssues().size());
        verify(gitHubIssueClientService, times(2)).addIssueComment(anyLong(), org.mockito.ArgumentMatchers.contains("- Decomposed multi-issue set: yes (2 issues)"));

        List<JsonNode> events = readEvents(traceLogPath, objectMapper);
        Set<String> eventTypes = events.stream()
                .map(event -> event.path("eventType").asText(""))
                .collect(Collectors.toSet());
        assertTrue(eventTypes.contains("intake.github.summary-comment.completed"));
    }

    @Test
    void emitsSummaryCommentFailureEventWhenCommentPostingFailsForOneIssue() throws Exception {
        Path traceLogPath = Files.createTempFile("decision-trace-comment-failure", ".jsonl");
        ObjectMapper objectMapper = new ObjectMapper();
        IntakeTraceabilityAgent traceabilityAgent = new IntakeTraceabilityAgent(objectMapper, traceLogPath.toString());

        GitHubIssueClientService gitHubIssueClientService = mock(GitHubIssueClientService.class);
        FileAuditLogService fileAuditLogService = mock(FileAuditLogService.class);
        GitHubIssueCreationService issueCreationService = new GitHubIssueCreationService(
                gitHubIssueClientService,
                fileAuditLogService,
                traceabilityAgent,
                new TraceabilityGitHubSummaryCommentBuilder()
        );

        when(gitHubIssueClientService.createIssueForStory(eq("feature"), any(DecompositionStory.class)))
                .thenReturn(issue(1101L), issue(1102L));
        doThrow(new IllegalStateException("comment endpoint unavailable"))
                .when(gitHubIssueClientService)
                .addIssueComment(eq(1101L), any());

        GitHubIssueCreateRequest request = new GitHubIssueCreateRequest();
        request.setRequestId("req-comment-failure");
        request.setTraceId("trace-comment-failure");
        request.setSourceType("feature");
        request.setStories(List.of(story("story-a"), story("story-b")));

        GitHubIssueCreateResponse issueResponse = issueCreationService.createFromDecomposition(request);
        assertTrue(issueResponse.isIssuesCreated());
        assertEquals(2, issueResponse.getIssues().size());

        List<JsonNode> events = readEvents(traceLogPath, objectMapper);
        JsonNode summaryCommentFailure = events.stream()
                .filter(event -> "intake.github.summary-comment.failed".equals(event.path("eventType").asText("")))
                .findFirst()
                .orElseThrow();
        assertEquals(2, summaryCommentFailure.path("artifactSummary").path("issueCount").asInt());
        assertEquals(1, summaryCommentFailure.path("artifactSummary").path("commentedIssueCount").asInt());
        assertEquals(1, summaryCommentFailure.path("artifactSummary").path("failedIssueCount").asInt());
    }

    private static StructuredIntakeData structuredData(String type, String title, String description) {
        StructuredIntakeData data = new StructuredIntakeData();
        data.setType(type);
        data.setTitle(title);
        data.setDescription(description);
        data.setPriority("high");
        data.setAffectedComponents(List.of("order-api"));
        return data;
    }

    private static DecompositionStory story(String storyId) {
        DecompositionStory story = new DecompositionStory();
        story.setStoryId(storyId);
        story.setTitle("Fix checkout timeout");
        story.setDescription("Investigate timeout and add guardrails");
        story.setAcceptanceCriteria(List.of("Checkout responds under 2s"));
        story.setAffectedComponents(List.of("order-api"));
        story.setEstimatedSize("small");
        return story;
    }

    private static GitHubIssueSummary issue(long issueNumber) {
        GitHubIssueSummary summary = new GitHubIssueSummary();
        summary.setIssueNumber(issueNumber);
        summary.setIssueUrl("https://example.test/issues/" + issueNumber);
        summary.setTitle("Issue " + issueNumber);
        return summary;
    }

    private static List<JsonNode> readEvents(Path traceLogPath, ObjectMapper objectMapper) throws Exception {
        List<String> lines = Files.readAllLines(traceLogPath);
        return lines.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(line -> {
                    try {
                        return objectMapper.readTree(line);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .toList();
    }
}
