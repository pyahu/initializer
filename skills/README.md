# Skills

`pyahu-production-guides` is every guide of the site as plain Markdown, packaged as an
[Agent Skill](https://agentskills.io): an index (`SKILL.md`), one file per guide and the full checklist. People
can read it on GitHub and AI coding tools can load the one guide a task needs.

`guidekit generate` writes it from the same sources as the site, so the two never drift. Do not edit it by
hand: change the guide and regenerate.

## What it is good for

- **Writing code:** ask your tool to configure the database pool, add an outbox or harden the pod. It reads
  the matching step and follows it, naming the guide and step behind each change.
- **Reviewing a service:** ask for a review against `checklist.md`. Every item comes back pass, fail or not
  applicable, with its ID (such as `DB-03`) and the step that explains it.
- **Agreeing on a team standard:** the checklist IDs give your team a shared vocabulary. Write exceptions down
  with their reason, such as "KUBE-07 not applicable: batch job, no traffic".

## Install

Copy the folder into your project, so everyone on the team reads the same version:

```sh
git clone --depth 1 https://github.com/pyahu/initializer /tmp/pyahu-initializer
mkdir -p .claude/skills
cp -R /tmp/pyahu-initializer/skills/pyahu-production-guides .claude/skills/
```

- **Claude Code:** `.claude/skills/` in the project or `~/.claude/skills/` for yourself.
- **Codex:** `.codex/skills/` in the project or `~/.codex/skills/` for yourself.
- **Other tools:** keep the folder anywhere in the repository and point your rules file (`AGENTS.md`,
  `.cursor/rules` or `.github/copilot-instructions.md`) to `pyahu-production-guides/SKILL.md`.

To update, copy the folder again. The changes show up in your diff like any other code review.

## Without installing anything

The site publishes the same content for tools that read a URL:
[llms.txt](https://initializer.pyahu.io/llms.txt) for the index and
[llms-full.txt](https://initializer.pyahu.io/llms-full.txt) for every guide in one file.
