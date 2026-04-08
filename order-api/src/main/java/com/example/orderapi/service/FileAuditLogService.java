package com.example.orderapi.service;

import com.example.orderapi.dto.ChatMessage;
import com.example.orderapi.dto.StructuredIntakeData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(FileAuditLogService.class);

    private final ObjectMapper objectMapper;
    private final String auditLogPath;
    private final Path allowedAuditBasePath;
    private final Path defaultAuditFile;

    public FileAuditLogService(ObjectMapper objectMapper,
                               @Value("${app.intake.audit-log-path:order-api/audit/intake-chat.jsonl}") String auditLogPath,
                               @Value("${app.intake.audit-base-path:order-api/audit}") String auditBasePath) {
        this.objectMapper = objectMapper;
        this.auditLogPath = auditLogPath;
        this.allowedAuditBasePath = Paths.get(auditBasePath).toAbsolutePath().normalize();
        this.defaultAuditFile = this.allowedAuditBasePath.resolve("intake-chat.jsonl").normalize();
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
            entry.put("intakeComplete", intakeComplete != null ? intakeComplete : null);
            entry.put("structuredData", toStructuredDataMap(structuredData));
            entry.put("error", safeString(error));

            String jsonLine = objectMapper.writeValueAsString(entry) + System.lineSeparator();
            byte[] bytes = jsonLine.getBytes();
            try (FileChannel channel = FileChannel.open(path,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.APPEND);
                 FileLock ignored = channel.lock()) {
                channel.position(channel.size());
                channel.write(ByteBuffer.wrap(bytes));
            }
        } catch (Exception ex) {
            log.warn("Failed to write intake audit log entry for requestId={}", safeString(requestId), ex);
        }
    }

    private Path resolveAuditPath() {
        Path configured = Paths.get(auditLogPath);
        Path normalized = configured.isAbsolute()
                ? configured.normalize()
                : Paths.get("").toAbsolutePath().resolve(configured).normalize();
        if (!normalized.startsWith(allowedAuditBasePath)) {
            log.warn("Rejected audit log path outside allowed directory: {}", auditLogPath);
            return defaultAuditFile;
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
