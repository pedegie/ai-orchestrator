package org.codeintello.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.codeintello.orchestrator.model.AgentResult;

@ActivityInterface
public interface ReviewerActivity {

    /**
     * Runs the reviewer Claude agent.
     * Returns verdict: APPROVED or CHANGES_REQUIRED.
     */
    @ActivityMethod
    AgentResult review(String projectPath, String ticketId);
}
