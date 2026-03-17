package org.codeintello.orchestrator.conf;

/**
 * Configuration for a single Git project.
 * Bound from the {@code orchestrator.projects[*]} YAML list.
 *
 * @param path          Absolute path to the local repository clone.
 * @param provider      Git hosting provider: {@code GITLAB} or {@code GITHUB}.
 * @param token         Personal / project access token for the hosting provider.
 */
public record ProjectProperties(
        String path,
        String provider,
        String token
) {}


