package org.codeintello.orchestrator.activity;

import io.temporal.spring.boot.ActivityImpl;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.codeintello.orchestrator.agent.ClaudeCliRunner;
import org.codeintello.orchestrator.model.AgentResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component("coderActivity")
@ActivityImpl(taskQueues = "jira-implement")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class CoderActivityImpl implements CoderActivity {

    ClaudeCliRunner claude;

    @Override
    public AgentResult implement(String projectPath, String ticketId, int attemptNumber) {
        log.info("Running coder agent for {} — attempt #{}", ticketId, attemptNumber);

        // Clean the summary from previous attempt so we can detect if coder writes a new one
        var summaryPath = Path.of(projectPath, ".claude", "agents", "state", "implementation-summary.md");
        try { Files.deleteIfExists(summaryPath); } catch (IOException e) { /* ignore */ }

        var prompt = """
                Use the Agent tool with subagent_type 'coder' and this prompt: \
                'Implement ticket %s. Read .claude/agents/state/plan.md for the approved plan. \
                This is attempt #%d. If attempt > 1, first read .claude/agents/state/review.md (if it exists) \
                and .claude/agents/state/qa-report.md (if it exists) and fix every reported issue before writing \
                any new code. Write completion summary to .claude/agents/state/implementation-summary.md only \
                after mvn clean test exits 0.'
                """.formatted(ticketId, attemptNumber).strip();

        claude.run(projectPath, prompt);

        // Verify implementation-summary.md exists and confirms passing build
        try {
            if (!Files.exists(summaryPath)) {
                return new AgentResult("FAILURE", "implementation-summary.md not created by coder agent");
            }
            var summary = Files.readString(summaryPath);
            var verdict = summary.contains("mvn clean test") && !summary.contains("FAILURE") ? "SUCCESS" : "FAILURE";
            return new AgentResult(verdict, summary);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read implementation summary", e);
        }
    }
}
