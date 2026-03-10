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
@Component("qaActivity")
@ActivityImpl(taskQueues = "jira-implement")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class QaActivityImpl implements QaActivity {

    ClaudeCliRunner claude;

    @Override
    public AgentResult runQa(String projectPath, String ticketId) {
        log.info("Running QA agent for {}", ticketId);

        var qaPath = Path.of(projectPath, ".claude", "agents", "state", "qa-report.md");
        try { Files.deleteIfExists(qaPath); } catch (IOException e) { /* ignore */ }

        var prompt = """
                Use the Agent tool with subagent_type 'qa' and this prompt: \
                'Run QA and security checks for ticket %s. \
                Read .claude/agents/state/plan.md, .claude/agents/state/implementation-summary.md, \
                and .claude/agents/state/diff.patch. \
                Write your verdict (PASS or FAIL) with the full checklist to .claude/agents/state/qa-report.md.'
                """.formatted(ticketId).strip();

        claude.run(projectPath, prompt);

        try {
            var content = Files.readString(qaPath);
            var verdict = content.contains("FAIL") ? "FAIL" : "PASS";
            return new AgentResult(verdict, content);
        } catch (IOException e) {
            throw new RuntimeException("QA agent did not produce qa-report.md", e);
        }
    }
}
