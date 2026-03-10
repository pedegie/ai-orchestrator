package org.codeintello.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StartWorkflowRequest(String ticketId) {
    @JsonCreator
    public StartWorkflowRequest(@JsonProperty("ticketId") String ticketId) {
        this.ticketId = ticketId;
    }
}
