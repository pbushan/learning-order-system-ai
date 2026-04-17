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
    { eventType: 'intake.github.issue-creation.failed', timestamp: '2026-04-17T10:00:05Z', status: 'failed', summary: 'issue creation failed', decisionMetadata: {}, inputSummary: {}, artifactSummary: {}, governanceMetadata: {} },
    { eventType: 'intake.github.summary-comment.failed', timestamp: '2026-04-17T10:00:06Z', status: 'failed', summary: 'comments partially failed', decisionMetadata: {}, inputSummary: {}, artifactSummary: { failedIssueCount: 1 }, governanceMetadata: {} }
  ]);

  const titles = timeline.map((entry) => entry.stepTitle);
  assert.ok(titles.includes('Classification needs clarification'));
  assert.ok(titles.includes('GitHub issue creation failed'));
  assert.ok(titles.includes('GitHub summary comment posting had failures'));
});
