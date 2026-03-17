package org.codeintello.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StartWorkflowRequest(String ticketId, String defaultBranch, boolean runQa) {
    @JsonCreator
    public StartWorkflowRequest(
            @JsonProperty("ticketId") String ticketId,
            @JsonProperty("defaultBranch") String defaultBranch,
            @JsonProperty("runQa") boolean runQa
    ) {
        this.ticketId = ticketId;
        this.defaultBranch = defaultBranch;
        this.runQa = runQa;
    }
}
