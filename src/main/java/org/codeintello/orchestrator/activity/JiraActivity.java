package org.codeintello.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.codeintello.orchestrator.model.JiraTicket;

@ActivityInterface
public interface JiraActivity {

    @ActivityMethod
    JiraTicket fetchTicket(String ticketId);
}
