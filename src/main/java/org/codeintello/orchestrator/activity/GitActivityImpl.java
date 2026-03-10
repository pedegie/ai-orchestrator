package org.codeintello.orchestrator.activity;

import io.temporal.spring.boot.ActivityImpl;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.codeintello.orchestrator.agent.ShellRunner;
import org.codeintello.orchestrator.conf.OrchestratorProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Component("gitActivity")
@ActivityImpl(taskQueues = "jira-implement")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class GitActivityImpl implements GitActivity {

    ShellRunner shell;
    OrchestratorProperties orchestratorProperties;

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Override
    public String createWorktree(String projectPath, String ticketId) {
        var project = orchestratorProperties.getProject();
        var defaultBranch = project.defaultBranch();

        log.info("Pulling latest {} for {}", defaultBranch, projectPath);
        shell.run(projectPath, "git", "pull", "origin", defaultBranch);

        var worktreePath = projectPath + "-" + ticketId;
        log.info("Creating worktree at {}", worktreePath);

        var worktreeDir = Path.of(worktreePath);
        if (Files.exists(worktreeDir)) {
            log.warn("Worktree path {} already exists — removing and re-creating", worktreePath);
            shell.run(projectPath, "git", "worktree", "remove", "--force", worktreePath);
            try { deleteDirectory(worktreeDir); } catch (IOException e) { /* best effort */ }
        }

        try {
            shell.run(projectPath, "git", "worktree", "add", "-b", ticketId, worktreePath, defaultBranch);
        } catch (RuntimeException e) {
            shell.run(projectPath, "git", "worktree", "add", worktreePath, ticketId);
        }

        log.info("Worktree ready at {}", worktreePath);
        return worktreePath;
    }

    @Override
    public void removeWorktree(String projectPath, String ticketId) {
        var worktreePath = projectPath + "-" + ticketId;
        log.info("Removing worktree at {}", worktreePath);
        try {
            shell.run(projectPath, "git", "worktree", "remove", "--force", worktreePath);
        } catch (RuntimeException e) {
            log.warn("git worktree remove failed (already gone?): {}", e.getMessage());
        }
    }

    @Override
    public void initState(String worktreePath, String ticketId) {
        log.info("Initializing state directory in worktree {}", worktreePath);
        var statePath = Path.of(worktreePath, ".claude", "agents", "state");
        try {
            Files.createDirectories(statePath);
            // Clean previous state files
            try (var files = Files.list(statePath)) {
                files.forEach(f -> {
                    try { Files.deleteIfExists(f); } catch (IOException e) { /* ignore */ }
                });
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to init state directory", e);
        }

        // Save base commit SHA
        var sha = shell.run(worktreePath, "git", "rev-parse", "HEAD").trim();
        try {
            Files.writeString(statePath.resolve("base.sha"), sha);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write base.sha", e);
        }

        log.info("State initialized. Base SHA: {}", sha);
    }

    @Override
    public void generateDiff(String worktreePath) {
        log.info("Generating diff patch in {}", worktreePath);
        var statePath = Path.of(worktreePath, ".claude", "agents", "state");
        try {
            var baseSha = Files.readString(statePath.resolve("base.sha")).trim();
            shell.run(worktreePath, "git", "add", "-N", ".");
            var diff = shell.run(worktreePath, "git", "diff", "-U5", baseSha);
            Files.writeString(statePath.resolve("diff.patch"), diff);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate diff", e);
        }
    }

    @Override
    public String commitAndCreateMR(String worktreePath, String ticketId, String summary) {
        var project = orchestratorProperties.getProject();

        log.info("Committing and creating MR/PR for {} via {}", ticketId, project.provider());
        var commitMessage = ticketId + " " + summary;
        shell.run(worktreePath, "git", "add", "-A");
        shell.run(worktreePath, "git", "commit", "-m", commitMessage);
        shell.run(worktreePath, "git", "push", "-u", "origin", ticketId);

        var provider = project.provider().toUpperCase();
        var token = project.token();
        var defaultBranch = project.defaultBranch();

        String mrOutput;
        if ("GITHUB".equals(provider)) {
            mrOutput = shell.run(worktreePath,
                    Map.of("GH_TOKEN", token),
                    "gh", "pr", "create",
                    "--title", commitMessage,
                    "--head", ticketId,
                    "--base", defaultBranch,
                    "--body", "Implements " + ticketId + ": " + summary);
        } else {
            // GITLAB (default)
            mrOutput = shell.run(worktreePath,
                    Map.of("GITLAB_TOKEN", token),
                    "glab", "mr", "create",
                    "--title", commitMessage,
                    "--source-branch", ticketId,
                    "--target-branch", defaultBranch,
                    "--description", "Implements " + ticketId + ": " + summary,
                    "--yes");
        }

        var lines = mrOutput.lines()
                .filter(l -> !l.isBlank())
                .toList();
        return lines.isEmpty() ? mrOutput : lines.getLast();
    }


    @Override
    public String commitAndCreateDraftMR(String worktreePath, String ticketId, String summary) {
        var project = orchestratorProperties.getProject();

        log.info("Committing and creating DRAFT MR/PR for {} via {}", ticketId, project.provider());
        var commitMessage = ticketId + " " + summary;
        shell.run(worktreePath, "git", "add", "-A");
        shell.run(worktreePath, "git", "commit", "-m", commitMessage);
        shell.run(worktreePath, "git", "push", "-u", "origin", ticketId);

        var provider = project.provider().toUpperCase();
        var token = project.token();
        var defaultBranch = project.defaultBranch();

        String mrOutput;
        if ("GITHUB".equals(provider)) {
            mrOutput = shell.run(worktreePath,
                    Map.of("GH_TOKEN", token),
                    "gh", "pr", "create",
                    "--draft",
                    "--title", "Draft: " + commitMessage,
                    "--head", ticketId,
                    "--base", defaultBranch,
                    "--body", "⚠️ Draft — coder exceeded max attempts.\n\nImplements " + ticketId + ": " + summary);
        } else {
            // GITLAB (default)
            mrOutput = shell.run(worktreePath,
                    Map.of("GITLAB_TOKEN", token),
                    "glab", "mr", "create",
                    "--title", "Draft: " + commitMessage,
                    "--source-branch", ticketId,
                    "--target-branch", defaultBranch,
                    "--description", "⚠️ Draft — coder exceeded max attempts.\n\nImplements " + ticketId + ": " + summary,
                    "--draft",
                    "--yes");
        }

        var lines = mrOutput.lines()
                .filter(l -> !l.isBlank())
                .toList();
        return lines.isEmpty() ? mrOutput : lines.getLast();
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException e) { /* ignore */ } });
        }
    }
}
