package org.codeintello.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic result returned by an agent activity.
 * content holds the full output written by the agent (plan.md, review.md, etc.).
 * verdict is the key decision word extracted from content (PROCEED, APPROVED, PASS, etc.).
 */
public record AgentResult(
        String verdict,
        String content
) {
    @JsonCreator
    public AgentResult(
            @JsonProperty("verdict") String verdict,
            @JsonProperty("content") String content
    ) {
        this.verdict = verdict;
        this.content = content;
    }
}
