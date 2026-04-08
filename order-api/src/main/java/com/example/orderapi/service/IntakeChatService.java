package com.example.orderapi.service;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.IntakeChatRequest;
import com.example.orderapi.dto.IntakeChatResponse;
import com.example.orderapi.dto.StructuredIntakeData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class IntakeChatService {

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
            fileAuditLogService.logEntry(requestId, messages, model, reply, intakeComplete, structuredData, error);
        } catch (Exception ignored) {
            // Do not fail intake responses due to audit logging.
        }
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
