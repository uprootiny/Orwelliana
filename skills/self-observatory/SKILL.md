---
name: self-observatory
description: Use when the task is to study, organize, summarize, or operationalize a large heterogeneous corpus of agent conversations, logs, repos, shell history, artifacts, summaries, memories, and effects. This skill is for turning messy accumulated operational residue into a queryable self-observatory with clear strata, evidence paths, and reusable views.
---

# Self Observatory

Treat the corpus as an evolving observatory of self and system, not as a bag of
files.

The job is to convert:

- conversations
- shell traces
- git repos and commits
- generated artifacts
- logs
- summaries
- memories
- deployment effects

into a set of navigable layers with clear provenance and decision value.

## Core Skill

The transferable skill is **cybernetic archaeology**:

> identify the meaningful strata in a living heap of traces, recover the causal
> structure, and produce views that support action rather than mere recollection.

This means learning to distinguish:

- raw evidence from summaries
- durable state from transient narration
- effects from intentions
- canonical records from convenience copies
- useful compression from lossy mystification

## Default Approach

When asked to work on such a corpus:

1. Find the strata.
   Classify material into:
   - conversations
   - code/repos
   - shell/runtime traces
   - generated artifacts
   - derived summaries/memories
   - external effects and deployments

2. Find the canonical surfaces.
   For each stratum, determine:
   - where truth most likely lives
   - what is duplicated or derivative
   - what has stable identifiers
   - what can be regenerated

3. Recover the joins.
   Ask:
   - how does a conversation connect to a commit?
   - how does a shell session connect to an artifact?
   - how does a repo state connect to a deployment effect?
   - how do summaries point back to evidence?

4. Produce a usable model.
   Prefer outputs like:
   - inventories
   - timelines
   - typed events
   - provenance chains
   - anomaly buckets
   - bounded context windows
   - operator briefings

5. Preserve actionability.
   Every output should help answer at least one of:
   - what happened?
   - why did it happen?
   - what changed?
   - what is trustworthy?
   - what is safe to do next?

## Working Heuristics

- Prefer a small number of strong lenses over exhaustive dumping.
- Preserve pointers back to evidence whenever you compress.
- Build joins explicitly; do not imply them poetically.
- Name uncertainty clearly.
- If the corpus is alive, model refresh and staleness explicitly.
- If the corpus spans multiple repos or hosts, treat identity and provenance as
  first-class concerns.

## Good Outputs

Examples of good outputs:

- "These 14 sessions are the canonical source for the deployment story."
- "This summary is derivative of these 3 logs and 2 commits."
- "These artifacts have no evidence chain and should not drive decisions."
- "This slice of conversation is safe to reuse as prompt context."
- "These servers are still emitting effects, but the index is stale."

## Failure Modes

Avoid:

- flattening all material into one undifferentiated timeline
- trusting summaries more than evidence
- mistaking volume for coverage
- building an index that cannot be refreshed
- collecting everything without defining the decision it serves

## If Building Tooling

Prefer tools that:

- keep raw evidence addressable
- create typed derived views
- expose staleness and health
- support both exploration and disciplined reuse

For web surfaces, avoid generic dashboard tropes. The UI should feel like a
responsive observatory: alive, layered, and interrogable.

## Deliverable Pattern

For most tasks in this domain, try to return:

1. a concise model of the corpus
2. a list of canonical sources
3. the highest-value joins
4. the main ambiguities or blind spots
5. the next best indexing or instrumentation move
