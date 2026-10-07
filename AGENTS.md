# WikiRace Agent Rules

Before making any changes:

1. Read PROJECT_SPEC.md.
2. Read PHASE_PLAN.md.
3. Read PROJECT_STATE.md.

Permanent rules:

- Work only on the explicitly requested phase.
- Never implement features from future phases.
- Do not silently change gameplay rules.
- Do not silently change architecture decisions.
- Do not modify PROJECT_SPEC.md unless explicitly instructed.
- Preserve completed and working functionality.
- Do not rewrite unrelated files.
- Avoid unnecessary abstractions.
- Avoid unnecessary dependencies.
- Prefer simple, maintainable code over clever code.
- The backend is authoritative for all gameplay state.
- Never trust the frontend for current article, click count, navigation history, visit log, timer, close-to-target status, or winner.
- Run all relevant tests and build checks after each implementation phase.
- Fix errors caused by the current phase before stopping.
- Update PROJECT_STATE.md after every completed phase.
- Keep PROJECT_STATE.md concise.
- Do not automatically begin the next phase.
- Stop after completing the requested phase.

When beginning a phase:

1. Inspect the existing repository.
2. Read PROJECT_SPEC.md.
3. Read PHASE_PLAN.md.
4. Read PROJECT_STATE.md.
5. Summarize the current state.
6. Explain the implementation plan.
7. List files expected to be created or modified.
8. Implement only the requested phase.
9. Run tests/build checks.
10. Update PROJECT_STATE.md.
11. Stop.

At the end of every implementation phase report:

PHASE STATUS: PASS or FAIL

Completed:
- ...

Tests:
- ...

Build:
- ...

Files changed:
- ...

Known limitations:
- ...

Manual checks:
- ...

PROJECT_STATE.md updated:
- yes/no
