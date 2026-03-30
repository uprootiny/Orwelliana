# Orwelliana

[![CI](https://github.com/uprootiny/Orwelliana/actions/workflows/ci.yml/badge.svg)](https://github.com/uprootiny/Orwelliana/actions/workflows/ci.yml)
[![Pages](https://github.com/uprootiny/Orwelliana/actions/workflows/pages.yml/badge.svg)](https://github.com/uprootiny/Orwelliana/actions/workflows/pages.yml)

`Orwelliana` is a small Babashka toolkit for structured agent traces and cross-repo operator scaffolding. It turns the design from this session into a real JSONL event stream with a schema, trace writer, query surface, derived views, a tmux-friendly dashboard, and target-repo inspection/health checks.

The project is explicitly bootstrapped from the ongoing session in this workspace. The seed note lives in [`paste`](/home/uprootiny/Orwelliana/paste).

## What it does

- Writes canonical JSONL trace events with required keys: `ts`, `channel`, `event`
- Generates a session-seeded sample trace matching the ontology discussed here
- Filters traces by channel and event
- Renders a compact terminal dashboard suitable for a tmux pane
- Computes derived semantic views like trajectory, confidence curve, and failure manifold

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
- `bb -m orwelliana.core query path=... [channel=...] [event=...]` prints matching events
- `bb -m orwelliana.core dashboard path=...` prints a tmux-friendly summary
- `bb -m orwelliana.core derive path=...` prints higher-order semantic views
- `bb -m orwelliana.core inspect-repo target=...` attaches to a second repo and records discovery state
- `bb -m orwelliana.core health-check target=...` runs that repo’s health command and records the result
- `bb -m test-runner` runs the test suite

## Web UI

The repo also ships a static landing page and browser trace viewer in [`site/`](/home/uprootiny/Orwelliana/site). Open [`site/index.html`](/home/uprootiny/Orwelliana/site/index.html) or publish it with GitHub Pages to get:

- a project landing page
- a local JSONL trace uploader
- a browser summary of recent events, channel counts, and derived views

## Why this exists

The key property is simple: a good agent system’s logs should let you answer “why did this commit happen?” without reading the code.
