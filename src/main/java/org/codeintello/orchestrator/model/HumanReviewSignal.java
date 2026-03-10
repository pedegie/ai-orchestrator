package org.codeintello.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Signal sent by a human to resume a waiting workflow checkpoint.
 * feedback is required when action == REQUEST_CHANGES or PROVIDE_CLARIFICATION; null when APPROVE.
 */
public record HumanReviewSignal(
        ReviewAction action,
        String feedback
) {
    @JsonCreator
    public HumanReviewSignal(
            @JsonProperty("action") ReviewAction action,
            @JsonProperty("feedback") String feedback
    ) {
        this.action = action;
        this.feedback = feedback;
    }
}
