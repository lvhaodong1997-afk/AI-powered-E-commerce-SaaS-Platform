# Meta Publishing OpenAPI Implementation Plan

**Goal:** Expose Facebook Page and Instagram publishing to signed SaaS clients without sharing workbench state.

**Architecture:** Reuse the HMAC gateway and existing Meta provider client. Add isolated SQL state, scoped connections and media, and a durable worker that stops on ambiguous mutations.

- [x] Add contract tests for exact public routes, signed workspace and media validation; observe failure before wiring the endpoints.
- [x] Add OAuth state and Page selection, private chunk uploads, and idempotent publish tasks. Cover scoping and ambiguous worker outcomes.
- [ ] Validate the migration and release artifacts. The OpenAPI documentation bundle is generated separately to preserve the existing login and X workbench assets.
- [ ] Commit and push, release with backup and rollback, smoke test the exact deployed routes, and notify the Saas publishing integration task.
