const requiredEnv = ["GITHUB_TOKEN", "OPENAI_API_KEY", "REPO", "PR_NUMBER", "GITHUB_API_URL"];
const maxFiles = 20;
const maxPatchChars = 30000;
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

await githubRequest(`${apiBaseUrl}/repos/${repo}/pulls/${prNumber}/reviews`, {
    method: "POST",
    body: JSON.stringify({
        event,
        body
    })
});

console.log(`Posted ${event} review for PR #${prNumber} in ${repo}.`);

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
        "- Inline comments are not enabled yet; this is Phase 1 review behavior."
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
