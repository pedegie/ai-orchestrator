package org.codeintello.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record JiraImplementRequest(
        String ticketId,
        String projectPath,  // absolute path to the target repository
        String defaultBranch,
        boolean runQa
) {
    @JsonCreator
    public JiraImplementRequest(
            @JsonProperty("ticketId") String ticketId,
            @JsonProperty("projectPath") String projectPath,
            @JsonProperty("defaultBranch") String defaultBranch,
            @JsonProperty("runQa") boolean runQa
    ) {
        this.ticketId = ticketId;
        this.projectPath = projectPath;
        this.defaultBranch = defaultBranch;
        this.runQa = runQa;
    }
}
