# Project State

## Thesis

`Orwelliana` is becoming a small but serious operator control plane for
agent-driven work. Its value is not "logs" in the usual sense. Its value is the
conversion of messy agent activity into:

- typed events
- bounded reusable context
- deployment truth surfaces
- estate/fleet summaries

The project should feel closer to an operational notebook with executable
judgment than to a frontend demo.

## What Exists

Today the repo has four meaningful surfaces:

1. Trace writing and querying
   - canonical JSONL event stream
   - basic emit/query/derive/dashboard flows

2. Conversation modeling
   - `conversation.message` events
   - bounded history windows suitable for prompt reuse

3. Cross-repo operator scaffolding
   - repo inspection
   - health-check command detection

4. Deployment/fleet framing
   - `deploy-doctor`
   - `ops/fleet.edn`
   - static landing page and browser trace viewer

## What Is Good

- The core thesis is coherent.
- The CLI remains small.
- The event model is legible.
- The Pages deployment path has been exercised against reality.
- The landing page now explains the product instead of merely showing widgets.

## What Is Still Weak

- There is no long-lived real trace corpus in the repo yet.
- `deploy-doctor` is useful but still network-fragile and provider-specific.
- The browser UI is a viewer, not yet an operational workbench.
- There is no server-backed live data path; the site mostly renders sample or
  uploaded traces.
- The project still risks drifting into "beautiful instrumentation" unless each
  addition sharpens operator action.

## Strategic Direction

The strongest next direction is not breadth. It is consolidation around one
idea:

> encode operator technique as structured, inspectable, reusable state.

That implies the next meaningful moves are things like:

- stronger deploy/runbook encoding
- richer derived views from traces
- better multi-repo attachment stories
- higher confidence around health and reachability evidence

It does not imply random integrations or cosmetic analytics.

## Decision Filter

Before adding anything, ask:

1. Does this reduce ambiguity for the next operator?
2. Does this make a decision safer or faster?
3. Does this convert transient session knowledge into durable state?
4. Would this still matter if the UI vanished and only the trace remained?

If the answer is "no" or "mostly aesthetic", it is probably not the next best
use of effort.
