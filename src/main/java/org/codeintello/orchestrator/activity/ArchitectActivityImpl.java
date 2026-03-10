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
                ? "\n\nIMPORTANT — Human feedback on your previous plan:\n" + feedback
                        + "\n\nRevise the plan accordingly before writing plan.md."
                : "";

        var prompt = """
                Use the Agent tool with subagent_type 'architect' and this prompt: \
                'Analyze ticket %s. Read .claude/agents/state/ticket.md for ticket details. \
                Write the implementation plan to .claude/agents/state/plan.md.%s'
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
