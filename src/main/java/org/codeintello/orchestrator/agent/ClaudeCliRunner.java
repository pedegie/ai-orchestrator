package org.codeintello.orchestrator.agent;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;

/**
 * Runs a Claude agent via the claude CLI with --dangerously-skip-permissions.
 * The agent has full access to the project directory (reads/writes files, runs build commands).
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClaudeCliRunner {

    String claudeBinary;

    public ClaudeCliRunner(@Value("${orchestrator.claude-binary}") String claudeBinary) {
        this.claudeBinary = resolveExecutable(claudeBinary);
    }

    /**
     * If the configured binary is not an absolute path, search the system PATH for it.
     */
    private static String resolveExecutable(String binary) {
        if (new File(binary).isAbsolute()) {
            return binary;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            return Arrays.stream(pathEnv.split(File.pathSeparator))
                    .map(dir -> new File(dir, binary))
                    .filter(f -> f.isFile() && f.canExecute())
                    .map(File::getAbsolutePath)
                    .findFirst()
                    .orElse(binary);
        }
        return binary;
    }

    /**
     * Runs a claude agent with the given prompt inside the project directory.
     * Blocks until the agent process exits. Streams stdout/stderr to the logger in real time.
     *
     * @param projectPath working directory for the agent
     * @param prompt      the prompt passed via -p
     * @return stdout output from the claude CLI
     */
    public String run(String projectPath, String prompt) {
        log.info("Running claude agent in {}", projectPath);
        log.debug("Prompt: {}", prompt);
        try {
            var process = new ProcessBuilder(claudeBinary, "--dangerously-skip-permissions", "-p", prompt)
                    .directory(new File(projectPath))
                    .redirectErrorStream(true)
                    .redirectInput(new File("/dev/null"))
                    .start();

            // Stream output in real time so we can see what Claude is doing
            var outputBuilder = new StringBuilder();
            var streamThread = Thread.ofVirtual().start(() -> {
                try (var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("[claude] {}", line);
                        outputBuilder.append(line).append('\n');
                    }
                } catch (Exception e) {
                    log.warn("Error reading claude output stream", e);
                }
            });

            var exitCode = process.waitFor();
            streamThread.join();
            var output = outputBuilder.toString();

            if (exitCode != 0) {
                log.error("Claude agent exited with code {}. Output:\n{}", exitCode, output);
                throw new RuntimeException("Claude agent failed with exit code " + exitCode);
            }

            log.info("Claude agent completed successfully");
            return output;
        } catch (Exception e) {
            throw new RuntimeException("Failed to run claude agent", e);
        }
    }
}
