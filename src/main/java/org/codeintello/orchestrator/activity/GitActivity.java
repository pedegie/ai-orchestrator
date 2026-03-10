package org.codeintello.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface GitActivity {

    /** Pull latest master, create a worktree + branch for ticketId, return the worktree path. */
    @ActivityMethod
    String createWorktree(String projectPath, String ticketId);

    /** Remove the worktree directory from disk (branch stays, will be on GitLab as MR). */
    @ActivityMethod
    void removeWorktree(String projectPath, String ticketId);

    /** Initialize .claude/agents/state/ inside the worktree and save base.sha. */
    @ActivityMethod
    void initState(String worktreePath, String ticketId);

    @ActivityMethod
    void generateDiff(String worktreePath);

    @ActivityMethod
    String commitAndCreateMR(String worktreePath, String ticketId, String summary);
}
