const test = require('node:test');
const assert = require('node:assert/strict');
const ui = require('../decision-trace-ui.js');

test('normalizeTraceResponse sorts events and keeps traceId', () => {
  const normalized = ui.normalizeTraceResponse({
    traceId: 'trace-1',
    events: [
      { eventType: 'intake.github.issue-creation.completed', timestamp: '2026-04-17T10:00:02Z', status: 'completed', summary: 'issues created' },
      { eventType: 'intake.session.started', timestamp: '2026-04-17T10:00:00Z', status: 'recorded', summary: 'started' }
    ]
  });
  assert.equal(normalized.traceId, 'trace-1');
  assert.equal(normalized.events[0].eventType, 'intake.session.started');
  assert.equal(Object.prototype.hasOwnProperty.call(normalized, 'summary'), false);
});

test('summary helper exports are intentionally absent after revert', () => {
  assert.equal(typeof ui.buildTraceSummary, 'undefined');
  assert.equal(typeof ui.buildCompactTraceSummary, 'undefined');
});

test('buildCustomerTimeline presents lifecycle steps compactly', () => {
  const timeline = ui.buildCustomerTimeline([
    { eventType: 'intake.session.started', timestamp: '2026-04-17T10:00:00Z', status: 'recorded', summary: 'start', decisionMetadata: {}, inputSummary: {}, artifactSummary: {}, governanceMetadata: {} },
    { eventType: 'intake.classification.completed', timestamp: '2026-04-17T10:00:01Z', status: 'accepted', summary: 'classified', decisionMetadata: {}, inputSummary: {}, artifactSummary: {}, governanceMetadata: {} },
    { eventType: 'intake.decomposition.completed', timestamp: '2026-04-17T10:00:02Z', status: 'completed', summary: 'decomposed', decisionMetadata: {}, inputSummary: {}, artifactSummary: {}, governanceMetadata: {} },
    { eventType: 'intake.github.payload.prepared', timestamp: '2026-04-17T10:00:03Z', status: 'recorded', summary: 'payload', decisionMetadata: {}, inputSummary: {}, artifactSummary: {}, governanceMetadata: {} },
    { eventType: 'intake.github.issue-creation.completed', timestamp: '2026-04-17T10:00:04Z', status: 'completed', summary: 'issues', decisionMetadata: {}, inputSummary: {}, artifactSummary: {}, governanceMetadata: {} }
  ]);
  assert.equal(timeline.length, 5);
  assert.equal(timeline[0].stepTitle, 'Intake captured');
  assert.equal(timeline[4].stepTitle, 'GitHub issues created');
});

test('buildEngineerTimeline keeps all events with detailed payload', () => {
  const timeline = ui.buildEngineerTimeline([
    {
      traceId: 'trace-1',
      eventType: 'intake.github.issue-creation.completed',
      timestamp: '2026-04-17T10:00:04Z',
      status: 'completed',
      summary: 'issues created',
      actor: 'github-issue-creation-service',
      decisionMetadata: { sourceType: 'bug' },
      inputSummary: {},
      artifactSummary: { issueLinks: ['https://example.test/issues/1'] },
      governanceMetadata: { rawReasoningStored: false }
    }
  ]);
  assert.equal(timeline.length, 1);
  assert.equal(timeline[0].details.traceId, 'trace-1');
  assert.equal(timeline[0].details.issueLinks.length, 1);
});

test('buildCustomerTimeline surfaces failure states with clear step titles', () => {
  const timeline = ui.buildCustomerTimeline([
    { eventType: 'intake.classification.completed', timestamp: '2026-04-17T10:00:01Z', status: 'pending', summary: 'classification uncertain', decisionMetadata: {}, inputSummary: {}, artifactSummary: {}, governanceMetadata: {} },
    { eventType: 'intake.github.issue-creation.failed', timestamp: '2026-04-17T10:00:05Z', status: 'FAILED', summary: 'issue creation failed', decisionMetadata: {}, inputSummary: {}, artifactSummary: {}, governanceMetadata: {} },
    { eventType: 'intake.github.summary-comment.failed', timestamp: '2026-04-17T10:00:06Z', summary: 'comments partially failed', decisionMetadata: {}, inputSummary: {}, artifactSummary: { failedIssueCount: 1 }, governanceMetadata: {} }
  ]);

  const titles = timeline.map((entry) => entry.stepTitle);
  assert.ok(titles.includes('Classification needs clarification'));
  assert.ok(titles.includes('GitHub issue creation failed'));
  assert.ok(titles.includes('GitHub summary comment posting had failures'));
});

test('buildCustomerTimeline omits absent count fields and parses numeric strings', () => {
  const timeline = ui.buildCustomerTimeline([
    {
      eventType: 'intake.github.summary-comment.failed',
      timestamp: '2026-04-17T10:00:06Z',
      status: 'failed',
      summary: 'comments partially failed',
      decisionMetadata: {},
      inputSummary: {},
      artifactSummary: { commentedIssueCount: '2', unexpected: 'n/a' },
      governanceMetadata: {}
    }
  ]);

  assert.equal(timeline.length, 1);
  assert.equal(timeline[0].details.commentedIssueCount, 2);
  assert.equal(Object.prototype.hasOwnProperty.call(timeline[0].details, 'issueCount'), false);
  assert.equal(Object.prototype.hasOwnProperty.call(timeline[0].details, 'failedIssueCount'), false);
});

test('buildCustomerTimeline detects failure when eventType ends with failed without dot delimiter', () => {
  const timeline = ui.buildCustomerTimeline([
    {
      eventType: 'intake.github.summary-comment-failed',
      timestamp: '2026-04-17T10:00:07Z',
      status: 'completed',
      summary: 'legacy failed suffix event',
      decisionMetadata: {},
      inputSummary: {},
      artifactSummary: {},
      governanceMetadata: {}
    }
  ]);

  assert.equal(timeline.length, 1);
  assert.equal(timeline[0].status, 'completed');
  assert.equal(timeline[0].stepTitle, 'GitHub summary comment posting had failures');
});

test('buildCustomerTimeline falls back to issueUrl when issueLinks are not present', () => {
  const timeline = ui.buildCustomerTimeline([
    {
      eventType: 'intake.github.issue-creation.completed',
      timestamp: '2026-04-17T10:00:08Z',
      status: 'completed',
      summary: 'issues created',
      decisionMetadata: {},
      inputSummary: {},
      artifactSummary: { issueUrl: 'https://example.test/issues/501' },
      governanceMetadata: {}
    }
  ]);

  assert.equal(timeline.length, 1);
  assert.deepEqual(timeline[0].details.issueLinks, ['https://example.test/issues/501']);
});

test('formatTimestamp returns original value when input is not parseable', () => {
  assert.equal(ui.formatTimestamp('not-a-date'), 'not-a-date');
});

test('formatTimestamp returns deterministic UTC ISO output for parseable timestamps', () => {
  assert.equal(ui.formatTimestamp('2026-04-17T10:00:00Z'), '2026-04-17T10:00:00.000Z');
});

test('normalizeTraceResponse trims traceId and string event fields', () => {
  const normalized = ui.normalizeTraceResponse({
    traceId: ' trace-abc ',
    events: [
      {
        eventType: ' intake.session.started ',
        timestamp: ' 2026-04-17T10:00:00Z ',
        status: ' recorded ',
        summary: ' hi ',
        actor: ' intake-api ',
        correlationId: ' corr-1 '
      }
    ]
  });

  assert.equal(normalized.traceId, 'trace-abc');
  assert.equal(normalized.events[0].eventType, 'intake.session.started');
  assert.equal(normalized.events[0].timestamp, '2026-04-17T10:00:00Z');
  assert.equal(normalized.events[0].status, 'recorded');
  assert.equal(normalized.events[0].summary, 'hi');
  assert.equal(normalized.events[0].actor, 'intake-api');
  assert.equal(normalized.events[0].correlationId, 'corr-1');
});

test('buildCustomerTimeline keeps non-numeric count strings and omits nullish values', () => {
  const timeline = ui.buildCustomerTimeline([
    {
      eventType: 'intake.github.summary-comment.failed',
      timestamp: '2026-04-17T10:00:09Z',
      status: 'failed',
      summary: 'mixed value details',
      decisionMetadata: {},
      inputSummary: {},
      artifactSummary: {
        issueCount: null,
        failedIssueCount: '',
        unknownFailedIssueCount: 'n/a'
      },
      governanceMetadata: {}
    }
  ]);

  assert.equal(timeline.length, 1);
  assert.equal(Object.prototype.hasOwnProperty.call(timeline[0].details, 'issueCount'), false);
  assert.equal(Object.prototype.hasOwnProperty.call(timeline[0].details, 'failedIssueCount'), false);
  assert.equal(timeline[0].details.unknownFailedIssueCount, 'n/a');
});
