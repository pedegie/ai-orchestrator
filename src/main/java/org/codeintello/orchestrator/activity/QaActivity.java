package org.codeintello.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.codeintello.orchestrator.model.AgentResult;

@ActivityInterface
public interface QaActivity {

    /**
     * Runs the QA Claude agent.
     * Returns verdict: PASS or FAIL.
     */
    @ActivityMethod
    AgentResult runQa(String projectPath, String ticketId);
}
