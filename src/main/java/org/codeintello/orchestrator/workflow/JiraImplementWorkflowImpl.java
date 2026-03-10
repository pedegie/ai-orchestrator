package org.codeintello.orchestrator.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.common.SearchAttributeKey;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.codeintello.orchestrator.activity.*;
import org.codeintello.orchestrator.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@WorkflowImpl(taskQueues = "jira-implement")
public class JiraImplementWorkflowImpl implements JiraImplementWorkflow {

    private static final int MAX_CODER_ATTEMPTS = 3;

    // No auto-retries — agents are expensive and failures are usually logical errors, not transient.
    private static final RetryOptions NO_RETRY = RetryOptions.newBuilder()
            .setMaximumAttempts(1)
            .build();

    private final JiraActivity jiraActivity = Workflow.newActivityStub(JiraActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    private final GitActivity gitActivity = Workflow.newActivityStub(GitActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(NO_RETRY)
                    .build());

    private final ArchitectActivity architectActivity = Workflow.newActivityStub(ArchitectActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(60))
                    .setRetryOptions(NO_RETRY)
                    .build());

    private final CoderActivity coderActivity = Workflow.newActivityStub(CoderActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofHours(2))
                    .setRetryOptions(NO_RETRY)
                    .build());

    private final ReviewerActivity reviewerActivity = Workflow.newActivityStub(ReviewerActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setRetryOptions(NO_RETRY)
                    .build());

    private final QaActivity qaActivity = Workflow.newActivityStub(QaActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setRetryOptions(NO_RETRY)
                    .build());

    // Signal state — mutated by signal handler, read in await()
    private volatile HumanReviewSignal planReviewSignal;
    private volatile HumanReviewSignal clarificationSignal;

    private static final SearchAttributeKey<String> STAGE_KEY = SearchAttributeKey.forKeyword("Stage");

    // Queryable stage for observability
    private String stage = "INIT";

    // Queryable project path for the review UI
    private String projectPathValue;

    // Queryable worktree path where plan.md lives
    private String worktreePathValue;

    private void setStage(String newStage) {
        this.stage = newStage;
        Workflow.upsertTypedSearchAttributes(STAGE_KEY.valueSet(newStage));
    }

    @Override
    public JiraImplementResult execute(JiraImplementRequest req) {
        this.projectPathValue = req.projectPath();

        // ── 1. Fetch ticket ──────────────────────────────────────────────────
        setStage("FETCH_TICKET");
        var ticket = jiraActivity.fetchTicket(req.ticketId());

        // ── 2. Pull master + create isolated worktree ────────────────────────
        setStage("CREATE_WORKTREE");
        var worktreePath = gitActivity.createWorktree(req.projectPath(), req.ticketId());
        this.worktreePathValue = worktreePath;

        try {
            // ── 3. Init state dir inside the worktree ────────────────────────
            setStage("INIT_STATE");
            gitActivity.initState(worktreePath, req.ticketId());

            // Write ticket.md after state dir is initialized
            writeTicketFile(worktreePath, ticket);

            // ── 4. Architect loop (human can request changes with feedback) ───
            String planFeedback = null;
            while (true) {
                setStage("ARCHITECT");
                var plan = architectActivity.generatePlan(worktreePath, req.ticketId(), planFeedback);

                if ("NEEDS_CLARIFICATION".equals(plan.verdict())) {
                    setStage("AWAITING_CLARIFICATION");
                    clarificationSignal = null;
                    Workflow.await(() -> clarificationSignal != null);
                    planFeedback = clarificationSignal.feedback();
                    log.info("Clarification received — re-running architect with answer");
                    continue;
                }

                setStage("AWAITING_PLAN_REVIEW");
                planReviewSignal = null;
                Workflow.await(() -> planReviewSignal != null);

                if (planReviewSignal.action() == ReviewAction.APPROVE) break;

                planFeedback = planReviewSignal.feedback();
                log.info("Plan review rejected — re-running architect with feedback");
            }

            // ── 5. Coder → Reviewer → QA loop ────────────────────────────────
            int coderAttempt = 0;
            while (true) {
                coderAttempt++;
                if (coderAttempt > MAX_CODER_ATTEMPTS) {
                    setStage("MAX_RETRIES_EXCEEDED");
                    var mrUrl = gitActivity.commitAndCreateDraftMR(worktreePath, req.ticketId(), ticket.summary());
                    return JiraImplementResult.maxRetriesExceeded(mrUrl);
                }

                setStage("CODER_ATTEMPT_" + coderAttempt);
                var impl = coderActivity.implement(worktreePath, req.ticketId(), coderAttempt);
                if ("FAILURE".equals(impl.verdict())) {
                    setStage("MAX_RETRIES_EXCEEDED");
                    var mrUrl = gitActivity.commitAndCreateDraftMR(worktreePath, req.ticketId(), ticket.summary());
                    return JiraImplementResult.maxRetriesExceeded(mrUrl);
                }

                gitActivity.generateDiff(worktreePath);

                setStage("REVIEWER");
                var review = reviewerActivity.review(worktreePath, req.ticketId());
                if ("CHANGES_REQUIRED".equals(review.verdict())) {
                    log.info("Reviewer requested changes on attempt #{}", coderAttempt);
                    continue;
                }

                if (req.runQa()) {
                    setStage("QA");
                    var qa = qaActivity.runQa(worktreePath, req.ticketId());
                    if ("FAIL".equals(qa.verdict())) {
                        log.info("QA failed on attempt #{}", coderAttempt);
                        continue;
                    }
                } else {
                    log.info("QA skipped (runQa=false)");
                }

                break; // Reviewer passed (and QA if enabled)
            }

            // ── 6. Commit & create MR ────────────────────────────────────────
            setStage("COMMIT_AND_MR");
            var mrUrl = gitActivity.commitAndCreateMR(worktreePath, req.ticketId(), ticket.summary());

            setStage("DONE");
            return JiraImplementResult.success(mrUrl);

        } finally {
            // Always clean up the worktree directory (branch stays on GitLab as MR)
            gitActivity.removeWorktree(req.projectPath(), req.ticketId());
        }
    }

    @Override
    public void submitPlanReview(HumanReviewSignal signal) {
        this.planReviewSignal = signal;
    }

    @Override
    public void submitClarification(HumanReviewSignal signal) {
        this.clarificationSignal = signal;
    }

    @Override
    public String currentStage() {
        return stage;
    }

    @Override
    public String projectPath() {
        return projectPathValue;
    }

    @Override
    public String worktreePath() {
        return worktreePathValue;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeTicketFile(String projectPath, JiraTicket ticket) {
        var content = "# Ticket: " + ticket.id() + "\n\n"
                + "## Summary\n" + ticket.summary() + "\n\n"
                + "## Description\n" + ticket.description();
        try {
            var statePath = Path.of(projectPath, ".claude", "agents", "state");
            Files.createDirectories(statePath);
            Files.writeString(statePath.resolve("ticket.md"), content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write ticket.md", e);
        }
    }
}
