# Documentation Map

The current documentation distinguishes verified implementation, active plans,
open decisions, historical references, and archived designs.

## Current source of truth

- [overview.md](overview.md) — what the product and platform surfaces are now
- [status.md](status.md) — verified feature/build/test status
- [unknowns.md](unknowns.md) — unresolved product, model, platform, and brand
  decisions
- [PHASE_ZERO_MODERNIZATION_PLAN.md](PHASE_ZERO_MODERNIZATION_PLAN.md) — active
  modernization sequence and exit criteria
- [TECH_DEBT_AND_MODERNIZATION_AUDIT.md](TECH_DEBT_AND_MODERNIZATION_AUDIT.md) —
  detailed audit evidence and research
- feature folders — current behavior, flows, and edge cases where still aligned
- `decisions/` — historical architectural decisions; accepted status does not
  prove the implementation still matches the decision

## Reference and archive material

`docs/_reference/` contains older architecture, roadmap, setup, and planning
material that may still be useful as engineering context. It is not current
product truth unless a current document links to a specific verified section.

`docs/_archive/` contains superseded approaches and remains historical only.

The 2026 modernization audit found material drift in both areas, including
model identity, model size, PWA, Android build, voice/language, and streaming
claims. Do not copy claims from reference/archive documents into public copy
without rechecking the current code and upstream source.

## Status markers

- 🟢 Verified — current evidence supports the claim
- 🟡 Working with debt — implemented but incomplete, unverified, or drifted in
  important ways
- 🔴 Blocked/drifted — visible claim or workflow is materially wrong or broken
- 🔵 Planned — no implementation claim
- ❓ Open decision — outcome depends on explicit product or technical choice

## Documentation rules

1. State the exact platform/runtime/model being described.
2. Separate source/build, package, emulator, physical install, preservation,
   runtime, and hands-on acceptance evidence.
3. Do not label planned architecture as implemented.
4. Record model family, version, precision, language, size, source, and license.
5. Keep public README claims narrower than the strongest current evidence.
6. Link stale material through a current warning rather than presenting it as
   canonical.
7. Update overview, status, unknowns, and the active plan whenever a major
   implementation decision changes.
