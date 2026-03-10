package org.codeintello.orchestrator.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.spring.boot.ActivityImpl;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.codeintello.orchestrator.model.JiraTicket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

@Slf4j
@Component("jiraActivity")
@ActivityImpl(taskQueues = "jira-implement")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class JiraActivityImpl implements JiraActivity {

    HttpClient httpClient;
    ObjectMapper objectMapper;

    @Value("${orchestrator.jira.base-url}")
    @NonFinal
    String jiraBaseUrl;

    @Value("${orchestrator.jira.email}")
    @NonFinal
    String jiraEmail;

    @Value("${orchestrator.jira.api-token}")
    @NonFinal
    String jiraApiToken;

    @Override
    public JiraTicket fetchTicket(String ticketId) {
        log.info("Fetching Jira ticket {}", ticketId);
        try {
            var credentials = Base64.getEncoder()
                    .encodeToString((jiraEmail + ":" + jiraApiToken).getBytes());

            var baseUrl = jiraBaseUrl.stripTrailing().replaceAll("/+$", "");
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/api/3/issue/" + ticketId))
                    .header("Authorization", "Basic " + credentials)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Jira API returned HTTP " + response.statusCode() +
                        " for ticket " + ticketId + ". Response: " + response.body());
            }

            var root = objectMapper.readTree(response.body());

            var summary = root.path("fields").path("summary").asText();
            var description = extractDescription(root.path("fields").path("description"));

            log.info("Fetched ticket {}: {}", ticketId, summary);
            return new JiraTicket(ticketId, summary, description);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Jira ticket " + ticketId, e);
        }
    }

    private String extractDescription(JsonNode descNode) {
        if (descNode.isNull() || descNode.isMissingNode()) return "";
        return extractText(descNode);
    }

    private String extractText(JsonNode node) {
        if (node.has("type") && "text".equals(node.get("type").asText())) {
            return node.path("text").asText();
        }
        var sb = new StringBuilder();
        if (node.has("content")) {
            for (var child : node.get("content")) {
                sb.append(extractText(child));
            }
        }
        return sb.toString();
    }
}
