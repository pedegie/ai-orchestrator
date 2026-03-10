package org.codeintello.orchestrator.conf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Top-level orchestrator configuration bound from {@code orchestrator.*} in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "orchestrator")
public class OrchestratorProperties {

    private ProjectProperties project;
}
