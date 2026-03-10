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
@Component("reviewerActivity")
@ActivityImpl(taskQueues = "jira-implement")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class ReviewerActivityImpl implements ReviewerActivity {

    ClaudeCliRunner claude;

    @Override
    public AgentResult review(String projectPath, String ticketId) {
        log.info("Running reviewer agent for {}", ticketId);

        var reviewPath = Path.of(projectPath, ".claude", "agents", "state", "review.md");
        try { Files.deleteIfExists(reviewPath); } catch (IOException e) { /* ignore */ }

        var prompt = """
                Use the Agent tool with subagent_type 'reviewer' and this prompt: \
                'Review the implementation for ticket %s. \
                Read .claude/agents/state/plan.md, .claude/agents/state/implementation-summary.md, \
                and .claude/agents/state/diff.patch. \
                Write your verdict (APPROVED or CHANGES_REQUIRED) to .claude/agents/state/review.md.'
                """.formatted(ticketId).strip();

        claude.run(projectPath, prompt);

        try {
            var content = Files.readString(reviewPath);
            var verdict = content.contains("CHANGES_REQUIRED") ? "CHANGES_REQUIRED" : "APPROVED";
            return new AgentResult(verdict, content);
        } catch (IOException e) {
            throw new RuntimeException("Reviewer agent did not produce review.md", e);
        }
    }
}
