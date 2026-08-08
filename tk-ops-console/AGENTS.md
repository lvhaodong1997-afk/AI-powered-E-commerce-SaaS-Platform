# Operations Console Guide

## Scope

This is a small Node.js operations console, separate from the main Vue application.

- Server and database behavior: `server.mjs`
- Browser UI: `public/index.html`
- Local configuration: `config.json`
- Startup helper: `start.ps1`

## Low-Token Rules

- Never read or search `node_modules/`.
- Read `server.mjs` only for API, database, or server behavior.
- Read `public/index.html` only for console UI behavior.
- Read `config.json` only when configuration is part of the task; avoid exposing secrets in output.

## Commands

```powershell
npm run check
npm start
```

Use `npm run check` after server changes. UI-only changes require focused HTML/JavaScript inspection and browser verification when requested.
