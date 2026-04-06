#!/bin/bash

set -e

BRANCH_NAME="${BRANCH_NAME:-agent/codeowners-update}"
CURRENT_BRANCH="$(git branch --show-current)"

if [ "$CURRENT_BRANCH" != "$BRANCH_NAME" ]; then
  echo "🔀 Switching to branch: $BRANCH_NAME"
  if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
    git checkout "$BRANCH_NAME"
  else
    git checkout -b "$BRANCH_NAME"
  fi
else
  echo "🔀 Using current branch: $BRANCH_NAME"
fi

echo "📦 Staging changes..."
git add .github/CODEOWNERS PR_DESCRIPTION.md scripts/agent-pr.sh

if git diff --cached --quiet; then
  echo "⚠️ No staged changes to commit"
else
  echo "💾 Committing..."
  git commit -m "🤖 Agent: add/update CODEOWNERS via Codex"
fi

echo "🚀 Pushing branch..."
git push origin "$BRANCH_NAME"

echo "📬 Creating PR..."
gh pr create \
  --title "🤖 Agent: CODEOWNERS update via Codex" \
  --body-file PR_DESCRIPTION.md \
  --base main \
  --head "$BRANCH_NAME"

echo "✅ PR created successfully!"
