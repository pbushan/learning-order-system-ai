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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(FileAuditLogService.class);

    private final ObjectMapper objectMapper;
    private final String auditLogPath;

    public FileAuditLogService(ObjectMapper objectMapper,
                               @Value("${app.intake.audit-log-path:order-api/audit/intake-chat.jsonl}") String auditLogPath) {
        this.objectMapper = objectMapper;
        this.auditLogPath = auditLogPath;
    }

    public void logEntry(String requestId,
                         List<ChatMessage> messages,
                         String model,
                         String reply,
                         Boolean intakeComplete,
                         StructuredIntakeData structuredData,
                         String error) {
        try {
            Path path = Paths.get(auditLogPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            entry.put("requestId", requestId);
            entry.put("messages", messages);
            entry.put("model", model);
            entry.put("reply", reply);
            entry.put("intakeComplete", intakeComplete);
            entry.put("structuredData", structuredData);
            entry.put("error", error);

            String jsonLine = objectMapper.writeValueAsString(entry) + System.lineSeparator();
            Files.writeString(path, jsonLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.warn("Failed to write intake audit log entry", ex);
        }
    }
}
