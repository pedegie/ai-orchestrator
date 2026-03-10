package org.codeintello.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.codeintello.orchestrator.model.AgentResult;

@ActivityInterface
public interface ArchitectActivity {

    /**
     * Runs the architect Claude agent.
     * feedback is null on the first attempt; contains human instructions on subsequent attempts.
     * Returns verdict: PROCEED or NEEDS_CLARIFICATION.
     */
    @ActivityMethod
    AgentResult generatePlan(String projectPath, String ticketId, String feedback);
}
