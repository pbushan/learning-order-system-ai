package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class IntakeChatRequest {
    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("messages")
    @NotNull(message = "messages is required")
    @Size(min = 1, message = "messages must contain at least one item")
    @Valid
    private List<ChatMessage> messages;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
