package org.codeintello.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.codeintello.orchestrator.model.AgentResult;

@ActivityInterface
public interface CoderActivity {

    /**
     * Runs the coder Claude agent.
     * attemptNumber is 1-based; on attempt > 1 the agent reads existing review.md / qa-report.md.
     * Returns verdict: SUCCESS or FAILURE (if mvn clean test did not exit 0).
     */
    @ActivityMethod
    AgentResult implement(String projectPath, String ticketId, int attemptNumber);
}
