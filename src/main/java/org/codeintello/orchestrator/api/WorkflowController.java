package org.codeintello.orchestrator.api;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.codeintello.orchestrator.api.dto.StartWorkflowRequest;
import org.codeintello.orchestrator.conf.OrchestratorProperties;
import org.codeintello.orchestrator.model.HumanReviewSignal;
import org.codeintello.orchestrator.model.JiraImplementRequest;
import org.codeintello.orchestrator.workflow.JiraImplementWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/workflows")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class WorkflowController {

    WorkflowClient workflowClient;
    OrchestratorProperties orchestratorProperties;

    /**
     * Start a new jira-implement pipeline.
     * Returns the workflowId and a reviewUrl to use in the browser.
     *
     * POST /api/workflows/jira-implement
     * { "ticketId": "TT-123", "projectPath": "/home/kacper/projects/tattoo" }
     */
    @PostMapping("/jira-implement")
    public ResponseEntity<Map<String, String>> start(@RequestBody StartWorkflowRequest req) {
        var projectPath = orchestratorProperties.getProject().path();
        var workflowId = "jira-" + req.ticketId();
        var reviewUrl = "http://localhost:8080/review.html?workflowId=" + workflowId;
        log.info("Starting jira-implement workflow {} for project {}", workflowId, projectPath);
        log.info("Review UI: {}", reviewUrl);

        var workflow = workflowClient.newWorkflowStub(
                JiraImplementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue("jira-implement")
                        .setMemo(Map.of("reviewUrl", reviewUrl))
                        .build());

        WorkflowClient.start(workflow::execute, new JiraImplementRequest(req.ticketId(), projectPath, req.runQa()));

        return ResponseEntity.accepted().body(Map.of(
                "workflowId", workflowId,
                "reviewUrl", reviewUrl,
                "message", "Pipeline started. Review plan at: " + reviewUrl
        ));
    }

    /**
     * Send a plan review signal to a running workflow.
     *
     * POST /api/workflows/jira-TT-123/plan-review
     * { "action": "APPROVE" }
     * { "action": "REQUEST_CHANGES", "feedback": "Add pagination to the endpoint" }
     */
    @PostMapping("/{workflowId}/plan-review")
    public ResponseEntity<Void> planReview(
            @PathVariable String workflowId,
            @RequestBody HumanReviewSignal signal) {

        log.info("Sending plan review signal to {} — action={}", workflowId, signal.action());
        workflowClient.newUntypedWorkflowStub(workflowId)
                .signal("submitPlanReview", signal);

        return ResponseEntity.ok().build();
    }

    /**
     * Query the current stage of a running workflow.
     *
     * GET /api/workflows/jira-TT-123/stage
     */
    @GetMapping("/{workflowId}/stage")
    public ResponseEntity<Map<String, String>> stage(@PathVariable String workflowId) {
        var currentStage = workflowClient.newUntypedWorkflowStub(workflowId)
                .query("currentStage", String.class);
        return ResponseEntity.ok(Map.of("stage", currentStage));
    }

    /**
     * Returns the plan.md content for a given workflow.
     * Only valid when the workflow is in AWAITING_PLAN_REVIEW stage.
     *
     * GET /api/workflows/jira-TT-123/plan-content
     */
    @GetMapping("/{workflowId}/plan-content")
    public ResponseEntity<Map<String, String>> planContent(@PathVariable String workflowId) {
        String worktreePath;
        try {
            worktreePath = workflowClient.newUntypedWorkflowStub(workflowId)
                    .query("worktreePath", String.class);
        } catch (WorkflowNotFoundException e) {
            log.warn("planContent: workflow not found — workflowId={}", workflowId);
            return ResponseEntity.status(404).body(Map.of("error", "Workflow not found: " + workflowId));
        }
        var planFile = Path.of(worktreePath, ".claude", "agents", "state", "plan.md");
        try {
            var content = Files.readString(planFile);
            return ResponseEntity.ok(Map.of("content", content));
        } catch (IOException e) {
            return ResponseEntity.status(404).body(Map.of("error", "plan.md not found yet"));
        }
    }

    /**
     * Simple boolean flag — true when the workflow is waiting for plan review.
     * Used by the UI for polling after REQUEST_CHANGES.
     *
     * GET /api/workflows/jira-TT-123/plan-review-ready
     */
    @GetMapping("/{workflowId}/plan-review-ready")
    public ResponseEntity<Map<String, Object>> planReviewReady(@PathVariable String workflowId) {
        String currentStage;
        try {
            currentStage = workflowClient.newUntypedWorkflowStub(workflowId)
                    .query("currentStage", String.class);
        } catch (WorkflowNotFoundException e) {
            log.warn("planReviewReady: workflow not found — workflowId={}", workflowId);
            return ResponseEntity.status(404).body(Map.of("ready", false, "stage", "NOT_FOUND"));
        }
        var ready = "AWAITING_PLAN_REVIEW".equals(currentStage);
        return ResponseEntity.ok(Map.of("ready", ready, "stage", currentStage));
    }

    /**
     * Send a clarification answer to a workflow waiting in AWAITING_CLARIFICATION stage.
     *
     * POST /api/workflows/jira-TT-123/clarification
     * { "action": "PROVIDE_CLARIFICATION", "feedback": "The endpoint should be paginated with cursor-based pagination" }
     */
    @PostMapping("/{workflowId}/clarification")
    public ResponseEntity<Void> clarification(
            @PathVariable String workflowId,
            @RequestBody HumanReviewSignal signal) {

        log.info("Sending clarification signal to {} — feedback length={}", workflowId,
                signal.feedback() != null ? signal.feedback().length() : 0);
        workflowClient.newUntypedWorkflowStub(workflowId)
                .signal("submitClarification", signal);

        return ResponseEntity.ok().build();
    }

    /**
     * Returns the questions.md content for a workflow in AWAITING_CLARIFICATION stage.
     *
     * GET /api/workflows/jira-TT-123/clarification-content
     */
    @GetMapping("/{workflowId}/clarification-content")
    public ResponseEntity<Map<String, String>> clarificationContent(@PathVariable String workflowId) {
        String worktreePath;
        try {
            worktreePath = workflowClient.newUntypedWorkflowStub(workflowId)
                    .query("worktreePath", String.class);
        } catch (WorkflowNotFoundException e) {
            log.warn("clarificationContent: workflow not found — workflowId={}", workflowId);
            return ResponseEntity.status(404).body(Map.of("error", "Workflow not found: " + workflowId));
        }
        var questionsFile = Path.of(worktreePath, ".claude", "agents", "state", "questions.md");
        try {
            var content = Files.readString(questionsFile);
            return ResponseEntity.ok(Map.of("content", content));
        } catch (IOException e) {
            return ResponseEntity.status(404).body(Map.of("error", "questions.md not found yet"));
        }
    }
}
