package org.codeintello.orchestrator.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Runs plain shell commands (git, glab, gh, etc.) in a given directory.
 */
@Slf4j
@Component
public class ShellRunner {

    public String run(String workingDir, String... command) {
        return run(workingDir, Map.of(), command);
    }

    /**
     * Run a command with additional environment variables merged into the process environment.
     * Extra env values (e.g. tokens) are intentionally NOT logged.
     */
    public String run(String workingDir, Map<String, String> extraEnv, String... command) {
        log.debug("Running: {} in {}", String.join(" ", command), workingDir);
        try {
            var pb = new ProcessBuilder(command)
                    .directory(new File(workingDir))
                    .redirectErrorStream(true);

            if (!extraEnv.isEmpty()) {
                pb.environment().putAll(extraEnv);
            }

            var process = pb.start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(
                        "Command failed (exit %d): %s\n%s".formatted(exitCode, String.join(" ", command), output));
            }
            return output;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to run command: " + String.join(" ", command), e);
        }
    }
}
