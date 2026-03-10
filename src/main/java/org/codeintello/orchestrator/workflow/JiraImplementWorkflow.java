package org.codeintello.orchestrator.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.codeintello.orchestrator.model.HumanReviewSignal;
import org.codeintello.orchestrator.model.JiraImplementRequest;
import org.codeintello.orchestrator.model.JiraImplementResult;

@WorkflowInterface
public interface JiraImplementWorkflow {

    @WorkflowMethod
    JiraImplementResult execute(JiraImplementRequest request);

    /**
     * Human sends this signal after reviewing the architect's plan.md.
     * action=APPROVE → pipeline continues to coder.
     * action=REQUEST_CHANGES + feedback → architect re-runs with feedback.
     */
    @SignalMethod
    void submitPlanReview(HumanReviewSignal signal);

    /** Returns the current stage the workflow is in — useful for the UI. */
    @QueryMethod
    String currentStage();

    /** Returns the project path — used by the review UI to fetch plan.md. */
    @QueryMethod
    String projectPath();

    /** Returns the worktree path where plan.md lives. */
    @QueryMethod
    String worktreePath();
}
