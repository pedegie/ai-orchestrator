package org.codeintello.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StartWorkflowRequest(String ticketId, boolean runQa) {
    @JsonCreator
    public StartWorkflowRequest(
            @JsonProperty("ticketId") String ticketId,
            @JsonProperty("runQa") boolean runQa
    ) {
        this.ticketId = ticketId;
        this.runQa = runQa;
    }
}
