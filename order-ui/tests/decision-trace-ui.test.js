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
  assert.equal(normalized.summary, 'Trace trace-1 · 2 events · starts completed · ends recorded');
});

test('buildTraceSummary handles empty, partial, and populated responses', () => {
  assert.equal(ui.buildTraceSummary(null), '');
  assert.equal(ui.buildTraceSummary({ traceId: '  trace-2  ' }), 'Trace trace-2');
  assert.equal(
    ui.buildTraceSummary({
      traceId: 'trace-3',
      events: [{ status: 'recorded' }, { status: 'completed' }]
    }),
    'Trace trace-3 · 2 events · starts recorded · ends completed'
  );
  assert.equal(
    ui.buildTraceSummary({
      events: [{ status: 'recorded' }, { summary: 'done' }]
    }),
    '2 events · starts recorded'
  );
});

test('buildCompactTraceSummary returns a compact readable string', () => {
  assert.equal(
    ui.buildCompactTraceSummary({
      traceId: 'trace-9',
      eventType: 'intake.classification.completed',
      status: 'accepted',
      actor: 'classifier-service',
      decisionMetadata: { sourceType: 'bug', classifiedType: 'feature' }
    }),
    'Trace trace-9 · intake.classification.completed · accepted · by classifier-service · bug → feature'
  );
  assert.equal(ui.buildCompactTraceSummary({ traceId: 'trace-10' }), 'Trace trace-10');
  assert.equal(ui.buildCompactTraceSummary({}), '');
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

  assert.equal(timeline[0].details, 'commentedIssueCount: 2');
});
