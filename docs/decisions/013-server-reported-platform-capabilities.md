# Decision: Server-Reported Platform Capabilities

**Date:** 2026-08-11
**Who Decided:** Phase Zero modernization implementation
**Status:** Accepted
**Source:** FastAPI/NanoHTTPD contract drift audit

## The Situation

The Svelte client is shared by desktop and Android, but the backends do not
implement every route equally. Browser heuristics such as `window.Android`
cannot describe a remote backend and allowed desktop-only batch controls to
appear against Android, where those routes returned SPA HTML.

## What We Chose

Both backends expose `GET /api/capabilities` with the same schema. The response
identifies the backend platform and boolean feature support. The shared client
uses that response to hide unsupported workflows while keeping local native
bridge detection only for native file/media integration.

Unknown `/api/*` routes always return a JSON 404 rather than the SPA fallback.

## What We Rejected

- Growing a list of `window.Android` UI conditionals.
- Pretending all controls work everywhere.
- Implementing Android batch transcription solely to avoid capability gating.

## Consequences

- Adding iOS requires implementing one small capability response.
- Shared controls reflect the connected backend, including remote connections.
- Capability schema changes require synchronized backend and contract tests.
