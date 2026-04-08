package com.example.orderapi.dto;

public class IntakeChatResponse {
    private String reply;
    private boolean intakeComplete;
    private StructuredIntakeData structuredData;
    private String requestId;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public boolean isIntakeComplete() {
        return intakeComplete;
    }

    public void setIntakeComplete(boolean intakeComplete) {
        this.intakeComplete = intakeComplete;
    }

    public StructuredIntakeData getStructuredData() {
        return structuredData;
    }

    public void setStructuredData(StructuredIntakeData structuredData) {
        this.structuredData = structuredData;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
