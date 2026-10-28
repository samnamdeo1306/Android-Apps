# Repository guidance

This repository contains independent Android sample projects under `apps/<sample>/`. Work within the selected sample's Gradle project and preserve its independent build and dependency ownership.

## Keep design decisions current

- Read the affected sample's `docs/DECISIONS.md` before making a significant design change.
- When a significant architecture, product-behavior, dependency, or reliability decision is made, record it in that log in the same change. Include a stable ID, date, status, context, choice, reasoning, alternatives/tradeoffs, and implementation status.
- Distinguish accepted direction from completed implementation. Update implementation status when follow-up work lands; keep architecture, requirements, roadmap, and release documentation consistent where affected.
- Preserve history: add and cross-link a superseding ADR when a choice changes. Do not silently overwrite its original rationale.
- Routine refactors without a new design choice need no new ADR. Avoid adding abstractions solely to satisfy a pattern.
- Repository-wide decisions belong under root `docs/`; application-specific decisions belong to their sample. Daybreak's log is `apps/daybreak/docs/DECISIONS.md`.
