package org.codeintello.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record JiraImplementRequest(
        String ticketId,
        String projectPath   // absolute path to the target repository
) {
    @JsonCreator
    public JiraImplementRequest(
            @JsonProperty("ticketId") String ticketId,
            @JsonProperty("projectPath") String projectPath
    ) {
        this.ticketId = ticketId;
        this.projectPath = projectPath;
    }
}
