# X and YouTube Publishing Implementation Plan

> Execute inline, with focused tests before publishing to main.

**Goal:** Ship two independent publishing pages for generated videos.

**Architecture:** Extend the existing social publishing tables and lease queue. The new channels have separate menu grants, credentials and provider clients. Existing Meta endpoints retain their original scope.

## Execution

- [ ] Verify protocol failure classification and validated resumable upload URLs with TkChannelProtocolTest.
- [ ] Add channel OAuth, encrypted refresh credentials, publish commands and provider-specific checkpoints.
- [ ] Add independent Vue menus and pages with shared form and history components.
- [ ] Run channel and Meta regression tests, type-check the new views, and build the server and web distribution.
- [ ] Verify the remote main revision before packaging. Apply the additive migration, release with both platforms disabled unless credentials are configured, and check application health.
