import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { parse } from 'yaml';

const root = fileURLToPath(new URL('../../', import.meta.url));
// What the site shows must be proven: the guides and their data, and the fixture whose tests prove the claims.
// Online checks (links, npm audit) and the generator's workflow report on their own and never block a publication.
export const required = ['guides.yml', 'fixture-single-hexagonal.yml', 'fixture-monorepo.yml'];
const git = (...args) => execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();

export function workflowPaths(text) {
  const paths = parse(text).on?.push?.paths ?? ['.'];
  if (paths.some(path => path.startsWith('!'))) throw new Error('Negated workflow paths are not supported');
  return paths;
}

export function selectChecks(runs, workflows, matches) {
  return workflows.map(workflow => {
    const run = runs.find(run => run.path === `.github/workflows/${workflow}` && run.head_branch === 'main' && ['push', 'schedule', 'workflow_dispatch'].includes(run.event) && matches(workflow, run.head_sha));
    if (!run) return { workflow, status: 'pending' };
    if (run.status !== 'completed') return { workflow, status: 'pending', run: run.id };
    if (run.conclusion !== 'success') throw new Error(`${workflow}: run ${run.id} concluded ${run.conclusion}`);
    return { workflow, status: 'success', run: run.id };
  });
}

export async function verifyPublication() {
  if (git('status', '--porcelain')) throw new Error('Uncommitted source files cannot be published');
  const target = process.env.WORKERS_CI_COMMIT_SHA ?? git('rev-parse', 'HEAD');
  if (!/^[a-f0-9]{40}$/.test(target) || git('rev-parse', 'HEAD') !== target) throw new Error('Build commit does not match checkout');
  if (git('rev-parse', '--is-shallow-repository') === 'true') git('fetch', '--unshallow', '--no-tags', 'origin', 'main');
  else git('fetch', '--no-tags', 'origin', 'main');
  const assertCurrent = () => {
    if (git('rev-parse', 'origin/main') !== target) throw new Error('A newer main commit exists; this build must not publish');
  };
  assertCurrent();
  const paths = Object.fromEntries(required.map(workflow => [workflow, workflowPaths(readFileSync(resolve(root, '.github/workflows', workflow), 'utf8'))]));
  const cache = new Map();
  const matches = (workflow, sha) => {
    const key = `${workflow}:${sha}`;
    if (!cache.has(key)) {
      try {
        git('merge-base', '--is-ancestor', sha, target);
        git('diff', '--quiet', sha, target, '--', ...paths[workflow]);
        cache.set(key, true);
      } catch { cache.set(key, false); }
    }
    return cache.get(key);
  };
  const token = process.env.GITHUB_TOKEN;
  const headers = { Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'pyahu-publication-check' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const deadline = Date.now() + 30 * 60 * 1000;
  while (true) {
    const runs = [];
    let checks;
    for (let page = 1; page <= 10; page++) {
      const response = await fetch(`https://api.github.com/repos/pyahu/initializer/actions/runs?branch=main&per_page=100&page=${page}`, { headers, signal: AbortSignal.timeout(30_000) });
      if (!response.ok) throw new Error(`Cannot verify GitHub checks: HTTP ${response.status}`);
      const data = await response.json();
      runs.push(...data.workflow_runs);
      checks = selectChecks(runs, required, matches);
      if (checks.every(check => check.run) || data.workflow_runs.length < 100) break;
    }
    console.log(checks.map(check => `${check.workflow}: ${check.status}${check.run ? ` (${check.run})` : ''}`).join('\n'));
    if (checks.every(check => check.status === 'success')) {
      git('fetch', '--no-tags', 'origin', 'main');
      assertCurrent();
      console.log(`Publication approved for ${target}`);
      return;
    }
    if (Date.now() >= deadline) throw new Error('Required checks did not pass within 30 minutes; publication stopped');
    await new Promise(resolve => setTimeout(resolve, 60_000));
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPublication().catch(error => { console.error(error.message); process.exitCode = 1; });
}
