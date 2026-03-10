package org.codeintello.orchestrator.conf;

import io.temporal.activity.ActivityExecutionContext;
import io.temporal.common.interceptors.ActivityInboundCallsInterceptor;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.common.interceptors.WorkflowInboundCallsInterceptor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Temporal WorkerInterceptor that injects the Jira ticket ID into SLF4J MDC
 * for the duration of every activity execution.
 *
 * The workflow ID follows the pattern "jira-{ticketId}" (e.g. "jira-TT-131"),
 * so the ticket ID is derived by stripping the "jira-" prefix.
 */
@Component
public class TicketMdcInterceptor implements WorkerInterceptor {

    static final String MDC_KEY = "ticketId";

    @Override
    public ActivityInboundCallsInterceptor interceptActivity(ActivityInboundCallsInterceptor next) {
        return new ActivityInboundCallsInterceptor() {

            private ActivityExecutionContext ctx;

            @Override
            public void init(ActivityExecutionContext context) {
                this.ctx = context;
                next.init(context);
            }

            @Override
            public ActivityOutput execute(ActivityInput input) {
                var workflowId = ctx.getInfo().getWorkflowId(); // e.g. "jira-TT-131"
                var ticketId = workflowId.startsWith("jira-")
                        ? workflowId.substring("jira-".length())
                        : workflowId;
                MDC.put(MDC_KEY, ticketId);
                try {
                    return next.execute(input);
                } finally {
                    MDC.remove(MDC_KEY);
                }
            }
        };
    }

    @Override
    public WorkflowInboundCallsInterceptor interceptWorkflow(WorkflowInboundCallsInterceptor next) {
        return next;
    }
}



