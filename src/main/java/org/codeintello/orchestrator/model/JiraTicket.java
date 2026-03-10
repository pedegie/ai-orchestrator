package org.codeintello.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record JiraTicket(
        String id,
        String summary,
        String description
) {
    @JsonCreator
    public JiraTicket(
            @JsonProperty("id") String id,
            @JsonProperty("summary") String summary,
            @JsonProperty("description") String description
    ) {
        this.id = id;
        this.summary = summary;
        this.description = description;
    }
}
