# Orwelliana Agent Brief

## Purpose

`Orwelliana` is not a generic logging toy. It is an operator-memory system for
agentic work:

- typed execution traces
- bounded conversation history
- deploy preflight / deploy verification
- fleet state that can be queried instead of guessed

The standard to hold is simple:

> A future operator should be able to answer "what happened, why did it happen,
> and what is safe to do next?" without reconstructing the session from
> terminal folklore.

## Mindset

Work here should bias toward:

- explicit state over cleverness
- reproducible checks over vibes
- operator clarity over internal elegance
- compression of judgment into durable artifacts

Do not add features that merely produce more output. Add features that reduce
ambiguity.

## What Success Looks Like

An agent landing in this repo should quickly find:

- the current thesis of the project
- the real CLI surface
- the operational runbook
- the current product/story positioning
- the next high-leverage gaps

If a new contribution does not make one of those easier to access or more
trustworthy, question whether it belongs.

## Working Rules

1. Start with `git status` and respect the existing worktree.
2. Read [`README.md`](/home/uprootiny/Orwelliana/README.md) for the public
   surface, then [`docs/PROJECT_STATE.md`](/home/uprootiny/Orwelliana/docs/PROJECT_STATE.md)
   for the internal truth, then
   [`docs/SELF_OBSERVATORY.md`](/home/uprootiny/Orwelliana/docs/SELF_OBSERVATORY.md)
   for the broader ambition.
3. Prefer adding typed checks and derived views over adding prose-only claims.
4. When touching the landing page, preserve the product thesis: operator memory,
   not dashboard theater.
5. When touching CLI logic, keep outputs structured and composable.
6. Before declaring deploy health, verify:
   local tests, git state, provider state, workflow state, and public
   reachability.

## Current Anchors

- Main implementation: [`src/orwelliana/core.clj`](/home/uprootiny/Orwelliana/src/orwelliana/core.clj)
- Landing page / viewer: [`site/index.html`](/home/uprootiny/Orwelliana/site/index.html),
  [`site/app.css`](/home/uprootiny/Orwelliana/site/app.css),
  [`site/app.js`](/home/uprootiny/Orwelliana/site/app.js)
- Tests: [`test/orwelliana/core_test.clj`](/home/uprootiny/Orwelliana/test/orwelliana/core_test.clj)
- Fleet inventory: [`ops/fleet.edn`](/home/uprootiny/Orwelliana/ops/fleet.edn)
- Self-observatory skill: [`skills/self-observatory/SKILL.md`](/home/uprootiny/Orwelliana/skills/self-observatory/SKILL.md)

## Anti-Patterns

Avoid:

- turning the repo into a bag of disconnected utilities
- inflating the UI while the CLI model stays weak
- shipping claims the deploy path cannot verify
- treating network/provider failures as if they were application failures

## First Commands

```bash
bb -m test-runner
bb -m orwelliana.core deploy-doctor target=. repo=uprootiny/Orwelliana
bb -m orwelliana.core derive path=traces/sample.jsonl
```
