package com.oneask.routing.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RoutingDecision {
    
    private String agent;
    
    private String reason;

    public RoutingDecision() {
    }

    public RoutingDecision(String agent, String reason) {
        this.agent = agent;
        this.reason = reason;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "RoutingDecision{" +
                "agent='" + agent + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
