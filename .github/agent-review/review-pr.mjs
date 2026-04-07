const requiredEnv = ["GITHUB_TOKEN", "OPENAI_API_KEY", "REPO", "PR_NUMBER", "GITHUB_API_URL"];
const maxFiles = 20;
const maxPatchChars = 30000;
const maxInlineComments = 10;
const skippedPathPatterns = [
    /^node_modules\//,
    /^dist\//,
    /^build\//,
    /^target\//,
    /package-lock\.json$/,
    /yarn\.lock$/,
    /pnpm-lock\.yaml$/,
    /\.min\.(js|css)$/
];

for (const key of requiredEnv) {
    if (!process.env[key]) {
        throw new Error(`Missing required environment variable: ${key}`);
    }
}

const token = process.env.GITHUB_TOKEN;
const openAiApiKey = process.env.OPENAI_API_KEY;
const model = process.env.MODEL || "gpt-5";
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

const reviewContext = buildReviewContext(pull, files);
const llmReview = reviewContext.skipped
    ? buildSkippedReview(reviewContext)
    : await runLlmReview(reviewContext);

const event = llmReview.blocking ? "REQUEST_CHANGES" : "COMMENT";
const body = buildReviewBody(pull, reviewContext, llmReview, event);
const comments = buildInlineComments(reviewContext, llmReview);

await postReviewWithFallback({ event, body, comments });

function buildReviewContext(pullRequest, changedFiles) {
    const reviewableFiles = changedFiles.filter((file) => {
        return !skippedPathPatterns.some((pattern) => pattern.test(file.filename));
    });

    if (reviewableFiles.length > maxFiles) {
        return {
            pullRequest,
            changedFiles,
            reviewableFiles,
            skipped: true,
            skipReason: `PR has ${reviewableFiles.length} reviewable files, which exceeds the safe limit of ${maxFiles}.`
        };
    }

    let patchChars = 0;
    const patches = [];

    for (const file of reviewableFiles) {
        const patch = file.patch || "";
        patchChars += patch.length;

        if (patchChars > maxPatchChars) {
            return {
                pullRequest,
                changedFiles,
                reviewableFiles,
                skipped: true,
                skipReason: `PR patch context exceeds the safe limit of ${maxPatchChars} characters.`
            };
        }

        patches.push({
            filename: file.filename,
            status: file.status,
            additions: file.additions,
            deletions: file.deletions,
            patch
        });
    }

    return {
        pullRequest,
        changedFiles,
        reviewableFiles,
        patches,
        skipped: false
    };
}

function buildSkippedReview(reviewContext) {
    return {
        summary: "Automated review skipped deep LLM analysis because this PR exceeded the configured safe review limits.",
        blocking: false,
        findings: [
            {
                severity: "P3",
                title: "Deep review skipped due to policy limits",
                file: null,
                line: null,
                body: reviewContext.skipReason
            }
        ]
    };
}

async function runLlmReview(reviewContext) {
    const prompt = buildPrompt(reviewContext);
    const response = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
            Authorization: `Bearer ${openAiApiKey}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            model,
            input: [
                {
                    role: "system",
                    content: [
                        {
                            type: "input_text",
                            text: "You are a careful senior code reviewer. Focus on correctness, regressions, security, data loss, and broken workflows. Mention missing tests only when the diff clearly removes or weakens coverage, or when the changed behavior has no apparent guard in the provided context. Do not claim tests are absent if they may exist outside the diff. Do not comment on style unless it blocks maintainability. Return only valid JSON."
                        }
                    ]
                },
                {
                    role: "user",
                    content: [
                        {
                            type: "input_text",
                            text: prompt
                        }
                    ]
                }
            ],
            text: {
                format: {
                    type: "json_object"
                }
            }
        })
    });

    if (!response.ok) {
        const body = await response.text();
        throw new Error(`OpenAI review request failed (${response.status}): ${body}`);
    }

    const data = await response.json();
    const text = data.output_text || extractResponseText(data);

    if (!text) {
        throw new Error("OpenAI review response did not include text output.");
    }

    return normalizeReview(JSON.parse(text));
}

function buildPrompt(reviewContext) {
    return [
        "Review this pull request using the provided metadata and unified diffs.",
        "",
        "Return JSON in exactly this shape:",
        "{",
        "  \"summary\": \"short review summary\",",
        "  \"blocking\": false,",
        "  \"findings\": [",
        "    {",
        "      \"severity\": \"P1|P2|P3\",",
        "      \"title\": \"short title\",",
        "      \"file\": \"path or null\",",
        "      \"line\": 123,",
        "      \"body\": \"specific actionable explanation\"",
        "    }",
        "  ]",
        "}",
        "",
        "Set blocking=true only for findings that should prevent merge. Use P1/P2 for blocking findings and P3 for informational findings.",
        "If there are no substantive issues, return blocking=false and findings=[].",
        "For file-specific findings, set file to the changed file path and line to the new-file line number from the diff whenever possible. If the finding is general or cannot be tied to a changed line, use file=null and line=null.",
        "Important test guidance: avoid broad claims like \"there are no tests\" unless the provided context proves it. If test coverage is uncertain because unchanged tests are not included, say that a targeted test should be considered rather than claiming coverage is missing.",
        "",
        `PR title: ${reviewContext.pullRequest.title}`,
        `PR body: ${reviewContext.pullRequest.body || "(none)"}`,
        `Base branch: ${reviewContext.pullRequest.base.ref}`,
        `Head branch: ${reviewContext.pullRequest.head.ref}`,
        "",
        "Changed files:",
        ...reviewContext.patches.map((file) => {
            return [
                `FILE: ${file.filename}`,
                `STATUS: ${file.status}`,
                `ADDITIONS: ${file.additions}`,
                `DELETIONS: ${file.deletions}`,
                "PATCH:",
                file.patch || "(no patch provided)",
                ""
            ].join("\n");
        })
    ].join("\n");
}

function normalizeReview(review) {
    const findings = Array.isArray(review.findings) ? review.findings : [];
    const normalizedFindings = findings.map((finding) => {
        return {
            severity: ["P1", "P2", "P3"].includes(finding.severity) ? finding.severity : "P3",
            title: String(finding.title || "Review finding"),
            file: finding.file || null,
            line: Number.isFinite(Number(finding.line)) ? Number(finding.line) : null,
            body: String(finding.body || "")
        };
    }).filter((finding) => finding.body.trim().length > 0);

    return {
        summary: String(review.summary || "Automated review completed."),
        blocking: Boolean(review.blocking),
        findings: normalizedFindings
    };
}

function extractResponseText(responseBody) {
    const output = responseBody.output || [];

    for (const item of output) {
        for (const content of item.content || []) {
            if (content.type === "output_text" && content.text) {
                return content.text;
            }
        }
    }

    return "";
}

function buildReviewBody(pullRequest, reviewContext, llmReview, event) {
    const fileLines = reviewContext.changedFiles.slice(0, 10).map((file) => {
        return `- \`${file.filename}\` (+${file.additions} -${file.deletions})`;
    });

    const truncatedNotice = reviewContext.changedFiles.length > 10
        ? `\n- ...and ${reviewContext.changedFiles.length - 10} more file(s)`
        : "";

    const findingLines = llmReview.findings.length
        ? llmReview.findings.flatMap((finding) => {
            const location = finding.file
                ? `${finding.file}${finding.line ? `:${finding.line}` : ""}`
                : "General";

            return [
                `### ${finding.severity}: ${finding.title}`,
                `Location: \`${location}\``,
                "",
                finding.body,
                ""
            ];
        })
        : ["No blocking findings were identified in the bounded automated review."];

    return [
        "Agent review note: this review was generated by an LLM through the `pbushan-agent-reviewer` GitHub App. Human review is still required.",
        "",
        `Review event: **${event}**`,
        "",
        `Summary: ${llmReview.summary}`,
        "",
        "What was inspected:",
        `- PR number: #${pullRequest.number}`,
        `- Changed files: ${reviewContext.changedFiles.length}`,
        `- Reviewable files: ${reviewContext.reviewableFiles.length}`,
        `- Base branch: \`${pullRequest.base.ref}\``,
        `- Head branch: \`${pullRequest.head.ref}\``,
        "",
        "Changed files reviewed:",
        ...fileLines,
        truncatedNotice,
        "",
        "Findings:",
        ...findingLines,
        "",
        "Policy:",
        `- Max reviewable files: ${maxFiles}`,
        `- Max patch characters: ${maxPatchChars}`,
        `- Max inline comments: ${maxInlineComments}`,
        "- Findings with valid changed-file line references are also posted as inline review comments."
    ].filter(Boolean).join("\n");
}

function buildInlineComments(reviewContext, llmReview) {
    if (!reviewContext.patches) {
        console.warn("Inline comments skipped because review context did not include patch data.");
        return [];
    }

    if (!llmReview || !Array.isArray(llmReview.findings)) {
        console.warn("Inline comments skipped because the LLM review did not include a findings array.");
        return [];
    }

    const changedLinesByFile = new Map(reviewContext.patches.map((file) => {
        return [file.filename, collectNewFileLines(file.patch)];
    }));

    return llmReview.findings
        .map(normalizeInlineFinding)
        .filter((finding) => finding)
        .filter((finding) => changedLinesByFile.get(finding.file)?.has(finding.line))
        .sort(compareFindingSeverity)
        .slice(0, maxInlineComments)
        .map((finding) => {
            return {
                path: finding.file,
                line: finding.line,
                side: "RIGHT",
                body: [
                    `**${finding.severity}: ${finding.title}**`,
                    "",
                    finding.body,
                    "",
                    "_Posted by the LLM-backed GitHub App reviewer. Human review is still required._"
                ].join("\n")
            };
        });
}

async function postReviewWithFallback({ event, body, comments }) {
    try {
        await postReview({ event, body, comments });
        console.log(`Posted ${event} review for PR #${prNumber} in ${repo} with ${comments.length} inline comments.`);
    } catch (error) {
        if (!comments.length || !isInlineCommentValidationError(error)) {
            throw error;
        }

        const fallbackBody = [
            body,
            "",
            "Inline comment fallback:",
            `- ${comments.length} inline comment(s) were omitted because GitHub rejected the inline review payload.`,
            "- The top-level review was posted so feedback is not lost."
        ].join("\n");

        await postReview({ event, body: fallbackBody, comments: [] });
        console.log(`Posted ${event} summary-only fallback review for PR #${prNumber} in ${repo}.`);
    }
}

async function postReview({ event, body, comments }) {
    const payload = {
        commit_id: pull.head.sha,
        event,
        body
    };

    if (comments.length) {
        payload.comments = comments;
    }

    await githubRequest(`${apiBaseUrl}/repos/${repo}/pulls/${prNumber}/reviews`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

function isInlineCommentValidationError(error) {
    return error instanceof GitHubRequestError && [400, 413, 422].includes(error.status);
}

function normalizeInlineFinding(finding) {
    if (!finding.file || !finding.line) {
        return null;
    }

    const line = Number(finding.line);

    if (!Number.isInteger(line) || line <= 0) {
        return null;
    }

    return {
        ...finding,
        file: String(finding.file).replace(/^\.\//, ""),
        line
    };
}

function compareFindingSeverity(left, right) {
    const severityRank = {
        P1: 0,
        P2: 1,
        P3: 2
    };

    return (severityRank[left.severity] ?? severityRank.P3) - (severityRank[right.severity] ?? severityRank.P3);
}

function collectNewFileLines(patch) {
    const lines = new Set();

    if (!patch) {
        return lines;
    }

    let newLine = null;

    for (const line of patch.split("\n")) {
        const hunkMatch = line.match(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/);

        if (hunkMatch) {
            newLine = Number(hunkMatch[1]);
            continue;
        }

        if (newLine === null) {
            continue;
        }

        if (line.startsWith("+")) {
            lines.add(newLine);
            newLine += 1;
            continue;
        }

        if (line.startsWith("-")) {
            continue;
        }

        if (!line.startsWith("\\")) {
            newLine += 1;
        }
    }

    return lines;
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
        throw new GitHubRequestError(response.status, body);
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}

class GitHubRequestError extends Error {
    constructor(status, body) {
        super(`GitHub API request failed (${status}): ${body}`);
        this.status = status;
        this.body = body;
    }
}
