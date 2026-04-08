package com.example.orderapi.service;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.StructuredIntakeData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(FileAuditLogService.class);
    private static final Path ALLOWED_AUDIT_DIR = Paths.get("order-api", "audit").toAbsolutePath().normalize();
    private static final Path DEFAULT_AUDIT_FILE = ALLOWED_AUDIT_DIR.resolve("intake-chat.jsonl");

    private final ObjectMapper objectMapper;
    private final String auditLogPath;

    public FileAuditLogService(ObjectMapper objectMapper,
                               @Value("${app.intake.audit-log-path:order-api/audit/intake-chat.jsonl}") String auditLogPath) {
        this.objectMapper = objectMapper;
        this.auditLogPath = auditLogPath;
    }

    public synchronized void logEntry(String requestId,
                                      List<ChatMessage> messages,
                                      String model,
                                      String reply,
                                      Boolean intakeComplete,
                                      StructuredIntakeData structuredData,
                                      String error) {
        try {
            Path path = resolveAuditPath();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            entry.put("requestId", safeString(requestId));
            entry.put("messages", messages != null ? messages : Collections.emptyList());
            entry.put("model", safeString(model));
            entry.put("reply", safeString(reply));
            entry.put("intakeComplete", intakeComplete != null ? intakeComplete : Boolean.FALSE);
            entry.put("structuredData", toStructuredDataMap(structuredData));
            entry.put("error", safeString(error));

            String jsonLine = objectMapper.writeValueAsString(entry) + System.lineSeparator();
            Files.writeString(path, jsonLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.warn("Failed to write intake audit log entry", ex);
        }
    }

    private Path resolveAuditPath() {
        Path configured = Paths.get(auditLogPath);
        Path normalized = configured.isAbsolute()
                ? configured.normalize()
                : Paths.get("").toAbsolutePath().resolve(configured).normalize();
        if (!normalized.startsWith(ALLOWED_AUDIT_DIR)) {
            log.warn("Rejected audit log path outside allowed directory: {}", auditLogPath);
            return DEFAULT_AUDIT_FILE;
        }
        return normalized;
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private Map<String, Object> toStructuredDataMap(StructuredIntakeData structuredData) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("type", structuredData != null ? structuredData.getType() : null);
        structured.put("title", structuredData != null ? structuredData.getTitle() : null);
        structured.put("description", structuredData != null ? structuredData.getDescription() : null);
        structured.put("stepsToReproduce", structuredData != null ? structuredData.getStepsToReproduce() : null);
        structured.put("expectedBehavior", structuredData != null ? structuredData.getExpectedBehavior() : null);
        structured.put("priority", structuredData != null ? structuredData.getPriority() : null);
        structured.put("affectedComponents",
                structuredData != null && structuredData.getAffectedComponents() != null
                        ? structuredData.getAffectedComponents()
                        : Collections.emptyList());
        return structured;
    }
}
