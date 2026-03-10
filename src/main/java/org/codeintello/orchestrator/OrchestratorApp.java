package org.codeintello.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.codeintello.orchestrator.conf.OrchestratorProperties;

@SpringBootApplication
@EnableConfigurationProperties(OrchestratorProperties.class)
public class OrchestratorApp {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApp.class, args);
    }
}
