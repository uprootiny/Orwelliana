# Orwelliana

[![CI](https://github.com/uprootiny/Orwelliana/actions/workflows/ci.yml/badge.svg)](https://github.com/uprootiny/Orwelliana/actions/workflows/ci.yml)
[![Pages](https://github.com/uprootiny/Orwelliana/actions/workflows/pages.yml/badge.svg)](https://github.com/uprootiny/Orwelliana/actions/workflows/pages.yml)

`Orwelliana` is a small Babashka toolkit for structured agent traces and cross-repo operator scaffolding. It turns the design from this session into a real JSONL event stream with a schema, trace writer, query surface, derived views, a tmux-friendly dashboard, and target-repo inspection/health checks.

The project is explicitly bootstrapped from the ongoing session in this workspace. The seed note lives in [`paste`](/home/uprootiny/Orwelliana/paste).

## What it does

- Writes canonical JSONL trace events with required keys: `ts`, `channel`, `event`
- Stores typed conversation history as `conversation.message` events with session IDs and role/content metadata
- Generates a session-seeded sample trace matching the ontology discussed here
- Filters traces by channel and event
- Renders a compact terminal dashboard suitable for a tmux pane
- Computes derived semantic views like trajectory, confidence curve, failure manifold, and bounded conversation windows
- Runs a `deploy-doctor` preflight for git state, Pages provisioning, workflow health, and public reachability

## Quick start

```bash
bb -m orwelliana.core simulate path=traces/sample.jsonl
bb -m orwelliana.core dashboard path=traces/sample.jsonl
bb -m orwelliana.core query path=traces/sample.jsonl channel=execution
bb -m orwelliana.core derive path=traces/sample.jsonl
```

Append an event:

```bash
bb -m orwelliana.core emit \
  path=traces/session.jsonl \
  channel=execution \
  event=apply_patch \
  summary="Fix off-by-one in tokenizer" \
  details='{"file":"src/tokenizer.rs","lines_changed":12}'
```

Append a conversation turn:

```bash
bb -m orwelliana.core emit-message \
  path=traces/session.jsonl \
  session=ops \
  role=user \
  content="Investigate the failing tests"
```

Inspect the bounded conversation window that would be safe to reuse as prompt context:

```bash
bb -m orwelliana.core convo path=traces/session.jsonl session=ops limit=6 chars=4000
```

Run the deployment preflight against the current repo:

```bash
bb -m orwelliana.core deploy-doctor target=. repo=uprootiny/Orwelliana
```

Run a local-only preflight (skip GitHub/API reachability checks):

```bash
bb -m orwelliana.core deploy-doctor target=. repo=uprootiny/Orwelliana remote_checks=false
```

Skip local test execution during preflight:

```bash
bb -m orwelliana.core deploy-doctor target=. repo=uprootiny/Orwelliana verify_tests=false
```

Inspect another repo and log what was discovered:

```bash
bb -m orwelliana.core inspect-repo \
  target=/path/to/frontispiece \
  path=traces/frontispiece.jsonl
```

Run a health check against that repo using an auto-detected test command:

```bash
bb -m orwelliana.core health-check \
  target=/path/to/frontispiece \
  path=traces/frontispiece.jsonl
```

## Event shape

```json
{
  "ts": "2026-03-30T12:01:03.221Z",
  "agent": "agent-parser",
  "repo": "parser-lib",
  "loop": 7,
  "channel": "execution",
  "event": "apply_patch",
  "summary": "Fix off-by-one in tokenizer",
  "details": {
    "files": ["src/tokenizer.rs"],
    "diff_hunks": 3
  },
  "confidence": 0.78
}
```

## Commands

- `bb -m orwelliana.core simulate path=...` writes the seeded sample trace
- `bb -m orwelliana.core emit path=... channel=... event=...` appends one event
- `bb -m orwelliana.core emit-message path=... session=... role=... content=...` appends one conversation turn
- `bb -m orwelliana.core query path=... [channel=...] [event=...]` prints matching events
- `bb -m orwelliana.core convo path=... [session=...] [limit=...] [chars=...]` prints a bounded conversation view
- `bb -m orwelliana.core dashboard path=...` prints a tmux-friendly summary
- `bb -m orwelliana.core derive path=...` prints higher-order semantic views
- `bb -m orwelliana.core deploy-doctor [target=.] [repo=owner/name] [verify_tests=true|false] [remote_checks=true|false]` runs deploy preflight checks (tests and remote checks default to enabled)
- `bb -m orwelliana.core inspect-repo target=...` attaches to a second repo and records discovery state
- `bb -m orwelliana.core health-check target=...` runs that repo’s health command and records the result
- `bb -m test-runner` runs the test suite

## Web UI

The repo also ships a static landing page and browser trace viewer in [`site/`](/home/uprootiny/Orwelliana/site). Open [`site/index.html`](/home/uprootiny/Orwelliana/site/index.html) or publish it with GitHub Pages to get:

- a project landing page that explains the operator model
- a local JSONL trace uploader
- browser views for recent events, bounded conversation windows, trajectories, and derived state

## Deployment Graph

`Orwelliana` also carries a simple deployment inventory in [`ops/fleet.edn`](/home/uprootiny/Orwelliana/ops/fleet.edn) and a CLI summary:

```bash
bb -m orwelliana.core fleet path=ops/fleet.edn
```

This is meant to force a graph-shaped view of the estate:

- preferred target: `gce-primary`
- local sprawl explicitly marked as `:live`, `:stale`, or `:dead`
- degraded services surfaced as typed inventory, not terminal folklore

The NixOps-oriented infrastructure note is in [`docs/nixops-mastery.md`](/home/uprootiny/Orwelliana/docs/nixops-mastery.md).
The corresponding lab scaffold is in [`ops/nixops/README.md`](/home/uprootiny/Orwelliana/ops/nixops/README.md).

## Orientation

If you are stepping into this repo as a new operator/agent, read in this order:

- [`AGENTS.md`](/home/uprootiny/Orwelliana/AGENTS.md)
- [`docs/PROJECT_STATE.md`](/home/uprootiny/Orwelliana/docs/PROJECT_STATE.md)
- [`docs/NEXT_AGENT.md`](/home/uprootiny/Orwelliana/docs/NEXT_AGENT.md)
- [`docs/SELF_OBSERVATORY.md`](/home/uprootiny/Orwelliana/docs/SELF_OBSERVATORY.md)
- [`skills/self-observatory/SKILL.md`](/home/uprootiny/Orwelliana/skills/self-observatory/SKILL.md)

## Why this exists

The key property is simple: a good agent system’s logs should let you answer “why did this commit happen?” without reading the code.
