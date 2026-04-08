package com.example.orderapi.service;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.IntakeChatRequest;
import com.example.orderapi.dto.IntakeChatResponse;
import com.example.orderapi.dto.StructuredIntakeData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class IntakeChatService {

    private static final int MAX_AUDIT_MESSAGES = 20;
    private static final int MAX_AUDIT_CONTENT_CHARS = 1000;
    private static final int MAX_AUDIT_ERROR_CHARS = 500;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");
    private static final Pattern SECRET_PATTERN = Pattern.compile("\\bsk-[a-zA-Z0-9_-]{10,}\\b");

    private final IntakeOpenAiClient intakeOpenAiClient;
    private final FileAuditLogService fileAuditLogService;
    private final String model;

    public IntakeChatService(IntakeOpenAiClient intakeOpenAiClient,
                             FileAuditLogService fileAuditLogService,
                             @Value("${app.intake.model:gpt-4.1-mini}") String model) {
        this.intakeOpenAiClient = intakeOpenAiClient;
        this.fileAuditLogService = fileAuditLogService;
        this.model = model;
    }

    public IntakeChatResponse chat(String requestId, IntakeChatRequest request) {
        List<ChatMessage> messages = request.getMessages() != null ? request.getMessages() : Collections.emptyList();
        try {
            IntakeOpenAiClient.NormalizedIntakeResult result = intakeOpenAiClient.collectIntake(messages);
            IntakeChatResponse response = new IntakeChatResponse();
            response.setReply(result.getReply());
            response.setIntakeComplete(result.isIntakeComplete());
            response.setStructuredData(result.getStructuredData());
            response.setRequestId(requestId);

            safeAuditLog(
                    requestId,
                    messages,
                    model,
                    response.getReply(),
                    response.isIntakeComplete(),
                    response.getStructuredData(),
                    null
            );
            return response;
        } catch (IllegalStateException ex) {
            safeAuditLog(
                    requestId,
                    messages,
                    model,
                    "",
                    false,
                    emptyStructuredData(),
                    ex.getMessage()
            );
            throw new IntakeConfigurationException("Intake service is not configured. Please set OPENAI_API_KEY.");
        } catch (Exception ex) {
            IntakeChatResponse fallback = fallbackResponse(requestId);
            safeAuditLog(
                    requestId,
                    messages,
                    model,
                    fallback.getReply(),
                    fallback.isIntakeComplete(),
                    fallback.getStructuredData(),
                    ex.getMessage()
            );
            throw new IntakeProcessingException(fallback);
        }
    }

    private IntakeChatResponse fallbackResponse(String requestId) {
        IntakeChatResponse response = new IntakeChatResponse();
        response.setReply("I need a bit more detail to capture this intake. Please share whether this is a bug or feature, plus title and description.");
        response.setIntakeComplete(false);
        response.setStructuredData(emptyStructuredData());
        response.setRequestId(requestId);
        return response;
    }

    private void safeAuditLog(String requestId,
                              List<ChatMessage> messages,
                              String model,
                              String reply,
                              Boolean intakeComplete,
                              StructuredIntakeData structuredData,
                              String error) {
        try {
            fileAuditLogService.logEntry(
                    requestId,
                    sanitizeMessages(messages),
                    model,
                    truncateForAudit(redactSensitive(reply), MAX_AUDIT_CONTENT_CHARS),
                    intakeComplete,
                    sanitizeStructuredData(structuredData),
                    truncateForAudit(redactSensitive(error), MAX_AUDIT_ERROR_CHARS)
            );
        } catch (Exception ignored) {
            // Do not fail intake responses due to audit logging.
        }
    }

    private List<ChatMessage> sanitizeMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        int start = Math.max(0, messages.size() - MAX_AUDIT_MESSAGES);
        List<ChatMessage> safe = new ArrayList<>();
        for (int i = start; i < messages.size(); i++) {
            ChatMessage source = messages.get(i);
            if (source == null) {
                continue;
            }
            ChatMessage target = new ChatMessage();
            target.setRole(source.getRole());
            String safeContent = truncateForAudit(redactSensitive(source.getContent()), MAX_AUDIT_CONTENT_CHARS);
            target.setContent(safeContent);
            safe.add(target);
        }
        return safe;
    }

    private StructuredIntakeData sanitizeStructuredData(StructuredIntakeData source) {
        if (source == null) {
            return emptyStructuredData();
        }
        StructuredIntakeData safe = new StructuredIntakeData();
        safe.setType(source.getType());
        safe.setPriority(source.getPriority());
        safe.setTitle(truncateForAudit(redactSensitive(source.getTitle()), MAX_AUDIT_CONTENT_CHARS));
        safe.setDescription(truncateForAudit(redactSensitive(source.getDescription()), MAX_AUDIT_CONTENT_CHARS));
        safe.setStepsToReproduce(truncateForAudit(redactSensitive(source.getStepsToReproduce()), MAX_AUDIT_CONTENT_CHARS));
        safe.setExpectedBehavior(truncateForAudit(redactSensitive(source.getExpectedBehavior()), MAX_AUDIT_CONTENT_CHARS));
        List<String> components = source.getAffectedComponents() != null ? source.getAffectedComponents() : Collections.emptyList();
        List<String> safeComponents = new ArrayList<>();
        int start = Math.max(0, components.size() - MAX_AUDIT_MESSAGES);
        for (int i = start; i < components.size(); i++) {
            safeComponents.add(truncateForAudit(redactSensitive(components.get(i)), MAX_AUDIT_CONTENT_CHARS));
        }
        safe.setAffectedComponents(safeComponents);
        return safe;
    }

    private String truncateForAudit(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private String redactSensitive(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = EMAIL_PATTERN.matcher(value).replaceAll("[redacted-email]");
        return SECRET_PATTERN.matcher(redacted).replaceAll("[redacted-secret]");
    }

    private StructuredIntakeData emptyStructuredData() {
        StructuredIntakeData data = new StructuredIntakeData();
        data.setTitle("");
        data.setDescription("");
        data.setStepsToReproduce("");
        data.setExpectedBehavior("");
        data.setAffectedComponents(Collections.emptyList());
        return data;
    }

    public static class IntakeConfigurationException extends RuntimeException {
        public IntakeConfigurationException(String message) {
            super(message);
        }
    }

    public static class IntakeProcessingException extends RuntimeException {
        private final IntakeChatResponse fallbackResponse;

        public IntakeProcessingException(IntakeChatResponse fallbackResponse) {
            super("Intake processing failed");
            this.fallbackResponse = fallbackResponse;
        }

        public IntakeChatResponse getFallbackResponse() {
            return fallbackResponse;
        }
    }
}
