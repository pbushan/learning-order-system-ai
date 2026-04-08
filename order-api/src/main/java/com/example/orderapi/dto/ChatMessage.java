package com.example.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ChatMessage {
    @JsonProperty("role")
    @NotBlank(message = "role is required")
    @Pattern(regexp = "^(user|assistant)$", message = "role must be user or assistant")
    private String role;

    @JsonProperty("content")
    @NotBlank(message = "content is required")
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
