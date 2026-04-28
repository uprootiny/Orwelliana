# Changelog

All notable changes to this project are documented in this file.

## Unreleased

- Added tailnet integration:
  - `bb -m orwelliana.core tailnet` for declared/live tailnet visibility
  - drift detection for missing declared peers, unexpected live peers, and
    offline declared peers
  - `fleet` output now includes a tailnet summary
- Fixed `ops/fleet.edn` structure so it parses as valid EDN.
- Added tests covering tailnet drift and summary behavior.

## v0.1.0 - 2026-04-28

Core functionality release.

- Added typed trace tooling for simulate, emit, query, dashboard, and derive flows.
- Added conversation modeling with `conversation.message` events and bounded prompt windows.
- Added cross-repo inspection and health-check commands.
- Added `deploy-doctor` with local and remote preflight checks:
  - git working tree and branch drift
  - optional local test execution
  - optional GitHub workflow/Pages/public URL checks
- Added static landing page and browser trace viewer.
- Added operator orientation docs:
  - `AGENTS.md`
  - `docs/PROJECT_STATE.md`
  - `docs/NEXT_AGENT.md`
  - `docs/SELF_OBSERVATORY.md`
  - `skills/self-observatory/SKILL.md`
- Added `version` command and release metadata in CLI (`0.1.0`).
