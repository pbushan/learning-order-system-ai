package com.example.orderapi.controller;

import com.example.orderapi.dto.*;
import com.example.orderapi.service.DecompositionService;
import com.example.orderapi.service.GitHubIssueCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DecompositionControllerTest {

    @Test
    void completeToGitHub_createsIssuesWhenDecompositionHasStories() {
        DecompositionService decompositionService = mock(DecompositionService.class);
        GitHubIssueCreationService issueCreationService = mock(GitHubIssueCreationService.class);
        DecompositionController controller = new DecompositionController(decompositionService, issueCreationService);

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-1");
        decompositionResponse.setDecompositionComplete(true);
        decompositionResponse.setStories(List.of(sampleStory()));
        when(decompositionService.decompose(any(DecompositionRequest.class))).thenReturn(decompositionResponse);

        GitHubIssueCreateResponse createResponse = new GitHubIssueCreateResponse();
        createResponse.setRequestId("req-1");
        createResponse.setIssuesCreated(true);
        createResponse.setIssues(List.of(sampleIssue()));
        when(issueCreationService.createFromDecomposition(any(GitHubIssueCreateRequest.class))).thenReturn(createResponse);

        ResponseEntity<GitHubIssueCreateResponse> response = controller.completeToGitHub(sampleDecompositionRequest("req-1", "feature"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("req-1", response.getBody().getRequestId());
        assertEquals(1, response.getBody().getIssues().size());
        verify(issueCreationService, times(1)).createFromDecomposition(any(GitHubIssueCreateRequest.class));
    }

    @Test
    void completeToGitHub_returnsBadRequestWhenSourceTypeMissing() {
        DecompositionService decompositionService = mock(DecompositionService.class);
        GitHubIssueCreationService issueCreationService = mock(GitHubIssueCreationService.class);
        DecompositionController controller = new DecompositionController(decompositionService, issueCreationService);

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-2");
        decompositionResponse.setDecompositionComplete(true);
        decompositionResponse.setStories(List.of(sampleStory()));
        when(decompositionService.decompose(any(DecompositionRequest.class))).thenReturn(decompositionResponse);

        ResponseEntity<GitHubIssueCreateResponse> response = controller.completeToGitHub(sampleDecompositionRequest("req-2", ""));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("req-2", response.getBody().getRequestId());
        assertFalse(response.getBody().isIssuesCreated());
        verify(issueCreationService, never()).createFromDecomposition(any(GitHubIssueCreateRequest.class));
    }

    @Test
    void completeToGitHub_returnsBadGatewayWhenNoStoriesReturned() {
        DecompositionService decompositionService = mock(DecompositionService.class);
        GitHubIssueCreationService issueCreationService = mock(GitHubIssueCreationService.class);
        DecompositionController controller = new DecompositionController(decompositionService, issueCreationService);

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-3");
        decompositionResponse.setDecompositionComplete(false);
        decompositionResponse.setStories(List.of());
        when(decompositionService.decompose(any(DecompositionRequest.class))).thenReturn(decompositionResponse);

        ResponseEntity<GitHubIssueCreateResponse> response = controller.completeToGitHub(sampleDecompositionRequest("req-3", "bug"));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("req-3", response.getBody().getRequestId());
        assertFalse(response.getBody().isIssuesCreated());
        verify(issueCreationService, never()).createFromDecomposition(any(GitHubIssueCreateRequest.class));
    }

    @Test
    void completeToGitHub_returnsBadGatewayWhenIssueServiceReturnsNull() {
        DecompositionService decompositionService = mock(DecompositionService.class);
        GitHubIssueCreationService issueCreationService = mock(GitHubIssueCreationService.class);
        DecompositionController controller = new DecompositionController(decompositionService, issueCreationService);

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-4");
        decompositionResponse.setDecompositionComplete(true);
        decompositionResponse.setStories(List.of(sampleStory()));
        when(decompositionService.decompose(any(DecompositionRequest.class))).thenReturn(decompositionResponse);
        when(issueCreationService.createFromDecomposition(any(GitHubIssueCreateRequest.class))).thenReturn(null);

        ResponseEntity<GitHubIssueCreateResponse> response = controller.completeToGitHub(sampleDecompositionRequest("req-4", "feature"));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("req-4", response.getBody().getRequestId());
        assertFalse(response.getBody().isIssuesCreated());
    }

    @Test
    void completeToGitHub_returnsInternalServerErrorWhenIssueServiceThrows() {
        DecompositionService decompositionService = mock(DecompositionService.class);
        GitHubIssueCreationService issueCreationService = mock(GitHubIssueCreationService.class);
        DecompositionController controller = new DecompositionController(decompositionService, issueCreationService);

        DecompositionResponse decompositionResponse = new DecompositionResponse();
        decompositionResponse.setRequestId("req-5");
        decompositionResponse.setDecompositionComplete(true);
        decompositionResponse.setStories(List.of(sampleStory()));
        when(decompositionService.decompose(any(DecompositionRequest.class))).thenReturn(decompositionResponse);
        when(issueCreationService.createFromDecomposition(any(GitHubIssueCreateRequest.class)))
                .thenThrow(new IllegalStateException("token missing"));

        ResponseEntity<GitHubIssueCreateResponse> response = controller.completeToGitHub(sampleDecompositionRequest("req-5", "feature"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("req-5", response.getBody().getRequestId());
        assertFalse(response.getBody().isIssuesCreated());
    }

    private DecompositionRequest sampleDecompositionRequest(String requestId, String type) {
        StructuredIntakeData data = new StructuredIntakeData();
        data.setType(type);
        data.setTitle("Sample request");
        data.setDescription("Sample description");
        data.setAffectedComponents(List.of("order-ui"));

        DecompositionRequest request = new DecompositionRequest();
        request.setRequestId(requestId);
        request.setStructuredData(data);
        return request;
    }

    private DecompositionStory sampleStory() {
        DecompositionStory story = new DecompositionStory();
        story.setStoryId("story-1");
        story.setTitle("Update tab label");
        story.setDescription("Rename product tab label.");
        story.setAcceptanceCriteria(List.of("Label is updated"));
        story.setAffectedComponents(List.of("order-ui"));
        story.setEstimatedSize("small");
        return story;
    }

    private GitHubIssueSummary sampleIssue() {
        GitHubIssueSummary summary = new GitHubIssueSummary();
        summary.setStoryId("story-1");
        summary.setIssueNumber(99);
        summary.setIssueUrl("https://example.test/issues/99");
        summary.setTitle("Update tab label");
        summary.setLabels(List.of("feature"));
        return summary;
    }
}
