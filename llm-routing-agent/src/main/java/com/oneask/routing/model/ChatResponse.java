package com.oneask.routing.model;

public class ChatResponse {

    private String sessionId;
    private String reply;
    private String routedTo;
    private boolean success;

    public ChatResponse() {
    }

    public ChatResponse(String sessionId, String reply, String routedTo, boolean success) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.routedTo = routedTo;
        this.success = success;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getRoutedTo() {
        return routedTo;
    }

    public void setRoutedTo(String routedTo) {
        this.routedTo = routedTo;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
