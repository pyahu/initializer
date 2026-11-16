import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { selectChecks, workflowPaths } from './verify-publication.mjs';

const workflow = 'guides.yml';
const run = (id, status, conclusion, sha = 'current') => ({ id, event: 'push', path: `.github/workflows/${workflow}`, head_branch: 'main', head_sha: sha, status, conclusion });
const matching = (_, sha) => sha === 'current';

test('a newer pending run cannot fall back to an older green run', () => {
  assert.deepEqual(selectChecks([run(2, 'in_progress'), run(1, 'completed', 'success')], [workflow], matching), [{ workflow, status: 'pending', run: 2 }]);
});
test('a newer failure blocks even when an older run passed', () => {
  assert.throws(() => selectChecks([run(2, 'completed', 'failure'), run(1, 'completed', 'success')], [workflow], matching), /concluded failure/);
});
test('an old run with different inputs cannot approve publication', () => {
  assert.equal(selectChecks([run(1, 'completed', 'success', 'old')], [workflow], matching)[0].status, 'pending');
});
test('unchanged workflow inputs may reuse a successful earlier run', () => {
  assert.equal(selectChecks([run(1, 'completed', 'success', 'old')], [workflow], () => true)[0].status, 'success');
});
test('every required workflow must be present and successful', () => {
  const checks = selectChecks([run(1, 'completed', 'success')], [workflow, 'links.yml'], matching);
  assert.deepEqual(checks.map(check => check.status), ['success', 'pending']);
});
test('pull request results cannot approve a main publication', () => {
  assert.equal(selectChecks([{ ...run(1, 'completed', 'success'), head_branch: 'feature' }], [workflow], matching)[0].status, 'pending');
});
test('paths come from the actual workflow configuration', () => {
  assert.deepEqual(workflowPaths('on:\n  push:\n    paths:\n      - guides/**\n      - data/**\n'), ['guides/**', 'data/**']);
  assert.deepEqual(workflowPaths('on:\n  push:\n    branches: [main]\n'), ['.']);
  assert.throws(() => workflowPaths('on:\n  push:\n    paths: ["!guides/**"]\n'), /Negated/);
});
test('all publication workflows parse and declare their inputs', () => {
  for (const file of ['guides.yml', 'links.yml', 'fixture-single-hexagonal.yml', 'start.yml']) {
    assert.ok(workflowPaths(readFileSync(new URL(`../../.github/workflows/${file}`, import.meta.url), 'utf8')).length);
  }
});
