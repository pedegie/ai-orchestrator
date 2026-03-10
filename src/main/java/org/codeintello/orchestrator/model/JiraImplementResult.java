package org.codeintello.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record JiraImplementResult(
        Status status,
        String message   // MR URL on SUCCESS, reason on other statuses
) {
    public enum Status {
        SUCCESS,
        NEEDS_CLARIFICATION,
        MAX_RETRIES_EXCEEDED
    }

    @JsonCreator
    public JiraImplementResult(
            @JsonProperty("status") Status status,
            @JsonProperty("message") String message
    ) {
        this.status = status;
        this.message = message;
    }

    public static JiraImplementResult success(String mrUrl) {
        return new JiraImplementResult(Status.SUCCESS, mrUrl);
    }

    public static JiraImplementResult needsClarification(String reason) {
        return new JiraImplementResult(Status.NEEDS_CLARIFICATION, reason);
    }

    public static JiraImplementResult maxRetriesExceeded() {
        return new JiraImplementResult(Status.MAX_RETRIES_EXCEEDED,
                "Coder exceeded 3 attempts. Manual intervention required.");
    }
}
