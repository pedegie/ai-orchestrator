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
@Component("architectActivity")
@ActivityImpl(taskQueues = "jira-implement")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class ArchitectActivityImpl implements ArchitectActivity {

    ClaudeCliRunner claude;

    @Override
    public AgentResult generatePlan(String projectPath, String ticketId, String feedback) {
        log.info("Running architect agent for {} (feedback={})", ticketId, feedback != null ? "yes" : "no");

        var feedbackSection = feedback != null
                ? "\n\nIMPORTANT — Human response to your previous questions / feedback on your previous plan:\n" + feedback
                        + "\n\nTake this into account before writing plan.md."
                : "";

        // Clean questions.md from previous attempt so a stale file doesn't linger
        var questionsPath = Path.of(projectPath, ".claude", "agents", "state", "questions.md");
        try { Files.deleteIfExists(questionsPath); } catch (IOException e) { /* ignore */ }

        var prompt = """
                Use the Agent tool with subagent_type 'architect' and this prompt: \
                'Analyze ticket %s. Read .claude/agents/state/ticket.md for ticket details. \
                If you have enough information, write the implementation plan to .claude/agents/state/plan.md \
                and make sure the file contains the word PROCEED. \
                If you need clarification before you can plan, write your questions to \
                .claude/agents/state/questions.md and write a plan.md that contains only the word NEEDS_CLARIFICATION.%s'
                """.formatted(ticketId, feedbackSection).strip();

        claude.run(projectPath, prompt);

        return readResult(projectPath, "plan.md", "PROCEED", "NEEDS_CLARIFICATION");
    }

    private AgentResult readResult(String projectPath, String fileName,
                                   String positiveVerdict, String negativeVerdict) {
        var filePath = Path.of(projectPath, ".claude", "agents", "state", fileName);
        try {
            var content = Files.readString(filePath);
            var verdict = content.contains(negativeVerdict) ? negativeVerdict : positiveVerdict;
            return new AgentResult(verdict, content);
        } catch (IOException e) {
            throw new RuntimeException("Agent did not produce " + fileName, e);
        }
    }
}
