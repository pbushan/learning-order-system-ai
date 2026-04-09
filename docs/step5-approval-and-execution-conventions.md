# Step 5 Approval and Execution Conventions

This repository uses a lightweight label and naming convention for Step 5 issue pickup.

## Issue Label Conventions

- `approved-for-dev`: required label for issue pickup.
- `ai-in-progress`: optional label while an issue is being actively worked.

An issue is eligible for development when:
1. It has `approved-for-dev`.
2. It does not have `ai-in-progress`.

## Branch Naming Convention

Use this format for approved issue work:

`codex/issue-<number>-<short-slug>`

Examples:
- `codex/issue-128-fix-intake-timeout`
- `codex/issue-142-add-pr-template`

## PR Naming Convention

Use this format:

`Issue #<number>: <short action summary>`

For multi-slice issue work, append a slice marker:

`Issue #<number>: <short action summary> (slice <n>)`

## PR-Safe Scope Constraints

Keep each PR small and reviewable:

1. Single concern per PR whenever practical.
2. Avoid unrelated refactors.
3. Keep behavior explicit and readable.
4. Prefer additional small PRs over one large PR.
5. Leave non-blocking hardening for future passes.

## Governance Reminder

This is a portfolio project. AI proposes and implements changes, and humans remain the merge gate for normal project work.
