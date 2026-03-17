# Orchestrator

AI-powered pipeline: Jira ticket → multi-agent workflow (Architect → Coder → Reviewer → QA) → MR/PR.

---

## Prerequisites

- **`gh`** (GitHub CLI) lub **`glab`** (GitLab CLI) musi być zainstalowany i dostępny z terminala, w zależności od używanego dostawcy Git.
  - Instalacja `gh`: https://cli.github.com
  - Instalacja `glab`: https://gitlab.com/gitlab-org/cli
- Oba narzędzia muszą mieć dostęp do tokena — aplikacja automatycznie przekazuje `GH_TOKEN` / `GITLAB_TOKEN` z wartości `PROJECT_GIT_TOKEN`.

---

## 1. Start infrastructure

```bash
docker compose -f docker/docker-compose.yml up -d
```

Starts Temporal + PostgreSQL. Wait ~15 s for setup to finish.

Temporal UI is available at **http://localhost:8088**.

---

## 2. Start the app

Export required environment variables and run:

```bash
export CLAUDE_BINARY=/path/to/claude

# Jira
export JIRA_BASE_URL=https://yourcompany.atlassian.net
export JIRA_EMAIL=you@yourcompany.com
export JIRA_API_TOKEN=your_jira_token

# Project
export PROJECT_PATH=/absolute/path/to/repo
export PROJECT_GIT_PROVIDER=GITLAB          # or GITHUB
export PROJECT_GIT_TOKEN=your_git_token     # GitLab: glpat-xxx  /  GitHub: ghp_xxx

mvn spring-boot:run
```

---

## 3. Run a task

Open `requests.http` and set `@ticketId`, then fire request **1. Start pipeline**.

The response contains a `reviewUrl` — open it in the browser when the pipeline reaches the plan review step.
From there you can **Approve** or **Request Changes** via the UI.
