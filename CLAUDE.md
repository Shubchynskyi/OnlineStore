<!-- agent-orchestrator:managed-start -->
# CLAUDE.md
<!-- markdownlint-disable MD040 -->

# Agent Orchestrator Rule Index

`CLAUDE.md` and files linked from it under `agent-orchestrator/live/docs/agent-rules/` are the single source of truth for agent workflow rules.
All other agent instruction files must only redirect to `CLAUDE.md`.

## How To Use This File
1. Always read `agent-orchestrator/live/docs/agent-rules/00-core.md`.
2. Read only the linked rule files required for the current task.
3. Avoid loading unrelated rule files to save context and tokens.

## Rule Routing
| Task context | File to read |
|---|---|
| Language, communication, code quality | `agent-orchestrator/live/docs/agent-rules/00-core.md` |
| Project goals and tech stack | `agent-orchestrator/live/docs/agent-rules/10-project-context.md` |
| System architecture and data or event flow | `agent-orchestrator/live/docs/agent-rules/20-architecture.md` |
| Java, TypeScript, Angular code style | `agent-orchestrator/live/docs/agent-rules/30-code-style.md` |
| Strict SOLID rules and quality gates | `agent-orchestrator/live/docs/agent-rules/35-strict-coding-rules.md` |
| Command policy and available task commands | `agent-orchestrator/live/docs/agent-rules/40-commands.md` |
| Repository structure and documentation map | `agent-orchestrator/live/docs/agent-rules/50-structure-and-docs.md` |
| Operating workflow rules | `agent-orchestrator/live/docs/agent-rules/60-operating-rules.md` |
| Security constraints and mandatory controls | `agent-orchestrator/live/docs/agent-rules/70-security.md` |
| Task lifecycle and independent review process | `agent-orchestrator/live/docs/agent-rules/80-task-workflow.md` |
| Mandatory skill catalog and invocation policy | `agent-orchestrator/live/docs/agent-rules/90-skill-catalog.md` |

## Rule Files
- [Core Rules](agent-orchestrator/live/docs/agent-rules/00-core.md)
- [Project Context](agent-orchestrator/live/docs/agent-rules/10-project-context.md)
- [Architecture](agent-orchestrator/live/docs/agent-rules/20-architecture.md)
- [Code Style](agent-orchestrator/live/docs/agent-rules/30-code-style.md)
- [Strict Coding Rules](agent-orchestrator/live/docs/agent-rules/35-strict-coding-rules.md)
- [Commands](agent-orchestrator/live/docs/agent-rules/40-commands.md)
- [Structure and Documentation](agent-orchestrator/live/docs/agent-rules/50-structure-and-docs.md)
- [Operating Rules](agent-orchestrator/live/docs/agent-rules/60-operating-rules.md)
- [Security](agent-orchestrator/live/docs/agent-rules/70-security.md)
- [Task Workflow](agent-orchestrator/live/docs/agent-rules/80-task-workflow.md)
- [Skill Catalog](agent-orchestrator/live/docs/agent-rules/90-skill-catalog.md)
<!-- agent-orchestrator:managed-end -->

