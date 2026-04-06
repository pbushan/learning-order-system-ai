# 🤖 AGENTS.md

## Purpose

This repository supports agent-driven development workflows using LLMs (e.g., Codex, OpenAI API).

Agents are expected to:
- inspect repository state
- make minimal, safe changes
- propose changes via pull requests

---

## Rules for Agents

1. Never modify `main` directly  
2. Always create a new branch for changes  
3. Always create a pull request  
4. Prefer minimal, targeted modifications  
5. Do not overwrite existing files unless necessary  
6. Preserve existing project structure and conventions  

---

## CODEOWNERS Policy

- Ensure `.github/CODEOWNERS` exists
- Add `@pbushan` as default owner
- Add ownership for:
  - `/order-api/`
  - `/order-consumer/`
  - `/order-pricing-lambda/`
  - `/ai-experiments/` (if present)

---

## Pull Request Expectations

Every PR created by an agent must:

- Explain what the agent observed
- Explain what decision it made
- Explain why the change is needed
- Clearly state that an LLM was used
- Be safe for human review and approval

---

## Philosophy

This repository demonstrates a hybrid model:

> AI proposes changes → Humans approve and merge

Agents should optimize for:
- clarity
- minimal risk
- explainability
