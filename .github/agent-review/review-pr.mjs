const requiredEnv = ["GITHUB_TOKEN", "REPO", "PR_NUMBER", "GITHUB_API_URL"];

for (const key of requiredEnv) {
    if (!process.env[key]) {
        throw new Error(`Missing required environment variable: ${key}`);
    }
}

const token = process.env.GITHUB_TOKEN;
const repo = process.env.REPO;
const prNumber = process.env.PR_NUMBER;
const apiBaseUrl = process.env.GITHUB_API_URL;

const headers = {
    Accept: "application/vnd.github+json",
    Authorization: `Bearer ${token}`,
    "X-GitHub-Api-Version": "2022-11-28"
};

const pull = await githubRequest(`${apiBaseUrl}/repos/${repo}/pulls/${prNumber}`);
const files = await githubRequest(`${apiBaseUrl}/repos/${repo}/pulls/${prNumber}/files?per_page=100`);

const body = buildReviewBody(pull, files);

await githubRequest(`${apiBaseUrl}/repos/${repo}/pulls/${prNumber}/reviews`, {
    method: "POST",
    body: JSON.stringify({
        event: "COMMENT",
        body
    })
});

console.log(`Posted formal review for PR #${prNumber} in ${repo}.`);

function buildReviewBody(pullRequest, changedFiles) {
    const fileLines = changedFiles.slice(0, 10).map((file) => {
        return `- \`${file.filename}\` (+${file.additions} -${file.deletions})`;
    });

    const truncatedNotice = changedFiles.length > 10
        ? `\n- ...and ${changedFiles.length - 10} more file(s)`
        : "";

    return [
        "Agent review note: this formal review was posted by the GitHub App workflow, not by the PR author.",
        "",
        `This is a minimal safe review for **${pullRequest.title}**.`,
        "",
        "What was inspected:",
        `- PR number: #${pullRequest.number}`,
        `- Changed files: ${changedFiles.length}`,
        `- Base branch: \`${pullRequest.base.ref}\``,
        `- Head branch: \`${pullRequest.head.ref}\``,
        "",
        "Changed files reviewed:",
        ...fileLines,
        truncatedNotice,
        "",
        "Current behavior:",
        "- The workflow now creates a formal GitHub PR review event using the app identity.",
        "- No blocking logic or inline findings are applied yet.",
        "- Human review and approval are still required."
    ].filter(Boolean).join("\n");
}

async function githubRequest(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            ...headers,
            ...(options.headers || {})
        }
    });

    if (!response.ok) {
        const body = await response.text();
        throw new Error(`GitHub API request failed (${response.status}): ${body}`);
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}
