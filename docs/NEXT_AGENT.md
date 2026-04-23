# Next Agent Notes

## If You Only Read One Page

Read this file, then [`AGENTS.md`](/home/uprootiny/Orwelliana/AGENTS.md), then
open [`src/orwelliana/core.clj`](/home/uprootiny/Orwelliana/src/orwelliana/core.clj).

## Practical Priorities

High-leverage work, in order:

1. Strengthen `deploy-doctor`
   - classify provider/network failures explicitly
   - make output easier to consume by humans and other tools
   - widen checks without losing clarity

2. Turn traces into better operational views
   - session summaries
   - anomaly detection
   - stronger trajectory / failure clustering

3. Reduce the gap between the static site and the CLI
   - the page should reflect real capabilities
   - avoid duplicating logic in incompatible JS/Clojure ways where possible

4. Improve the repo-attachment workflow
   - inspecting another repo should generate a trustworthy, minimal briefing

## Likely Good Contributions

- new derived views with clear operator value
- better deployment verification surfaces
- cleaner repo-inspection outputs
- documentation that removes ambiguity instead of adding slogans

## Likely Bad Contributions

- decorative metrics with no clear decision use
- UI flourishes that are disconnected from the CLI model
- abstractions added before a second real use case exists
- sprawling infrastructure work that does not tighten the core product

## Reality Check

This project is still small. That is an advantage. Do not solve imaginary scale
problems. Preserve the tightness of the model.

The job is not to make Orwelliana look complete. The job is to make it become
trustworthy.
