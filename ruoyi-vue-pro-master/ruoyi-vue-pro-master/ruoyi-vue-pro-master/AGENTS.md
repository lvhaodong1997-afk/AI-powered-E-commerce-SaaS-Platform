# Product Source Guide

## Scope

This directory contains the Spring Boot backend, Vue frontend, media worker, and shared framework modules. For TK product work, start in `yudao-module-tk/` or the exact frontend page. Do not scan every Maven module.

## Search Boundaries

- Backend business behavior: search `yudao-module-tk/src/` first.
- Server configuration and bootstrap: search `yudao-server/src/` only when runtime wiring is relevant.
- Frontend behavior: search `yudao-ui/yudao-ui-admin-vue3/src/` and follow direct imports.
- Worker behavior: search `worker/` only for asynchronous media-processing tasks.
- Search `yudao-module-system/` or `yudao-framework/` only when a direct TK dependency points there.
- Exclude `.codex-build/`, `target/`, `node_modules/`, `dist*/`, `build/`, logs, archives, generated media, and vendored tool downloads.

## Commands

Run from this directory unless noted otherwise.

```powershell
# Focused backend module tests
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am test

# Frontend type check, run from yudao-ui\yudao-ui-admin-vue3
..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check
```

Use the narrowest test class or frontend check that covers the changed behavior before running a full module command.

## Configuration

- Base server config: `yudao-server/src/main/resources/application.yaml`
- Local config: `yudao-server/src/main/resources/application-local.yaml`
- Development config: `yudao-server/src/main/resources/application-dev.yaml`
- Production effective config is managed on the server and must not be inferred from local development files.

## Change Discipline

- Preserve unrelated user changes and generated files.
- Do not edit framework modules to solve a TK-local concern unless the shared behavior is demonstrably responsible.
- Do not rebuild deployment archives unless the user requests a release or server sync.

