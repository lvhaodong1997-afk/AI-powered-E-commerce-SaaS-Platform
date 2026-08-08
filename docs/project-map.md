# TK Auto Mix SaaS Project Map

Updated: 2026-07-15

## Workspace Boundaries

| Area | Path | Read When |
| --- | --- | --- |
| Product source root | `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/` | Backend or customer-facing web work |
| TK backend module | `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/` | TK business APIs, services, persistence, generation flows |
| Server bootstrap | `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-server/` | Spring Boot entry, runtime wiring, application configuration |
| Web frontend | `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/` | Customer-facing pages, API clients, stores, routes, UI behavior |
| Media worker | `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/worker/` | Python or media-processing worker tasks |
| Operations console | `tk-ops-console/` | Local operational UI and MySQL-backed console behavior |
| Deployment config | `deploy/nginx/`, `deploy/systemd/`, `deploy/scripts/` | Deployment-specific changes only |
| Latest local release | `deploy/voice-video-upload-20260715/` | Release inspection or explicit deployment work only |

## Backend Modules

- `yudao-module-tk/`: product-specific TK business code. Start here for business behavior.
- `yudao-server/`: application bootstrap and environment configuration.
- `yudao-module-system/`: users, tenants, permissions, and shared system capabilities.
- `yudao-module-infra/`: shared infrastructure capabilities.
- `yudao-framework/`: shared framework code. Read only when a TK call crosses into framework behavior.

## Web Frontend

- Source: `yudao-ui/yudao-ui-admin-vue3/src/`
- Public assets: `yudao-ui/yudao-ui-admin-vue3/public/`
- Tests: `yudao-ui/yudao-ui-admin-vue3/tests/`
- Manifest: `yudao-ui/yudao-ui-admin-vue3/package.json`
- Do not search `node_modules/`, `dist/`, `dist-*/`, build caches, ZIP files, or Vite logs.

## Operations Console

- Server entry: `tk-ops-console/server.mjs`
- Browser UI: `tk-ops-console/public/index.html`
- Local configuration: `tk-ops-console/config.json`
- Syntax check: run `npm run check` inside `tk-ops-console/`.

## Local Toolchain

- Maven: `.runtime/apache-maven-3.9.10/bin/mvn.cmd`
- pnpm: `.runtime/npm-global/node_modules/.bin/pnpm.cmd`
- Backend source root for Maven commands: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/`
- Web source root for pnpm commands: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/`

## Task Routing

| Task | Start With | Add Only If Needed |
| --- | --- | --- |
| TK API or business rule | `yudao-module-tk/src/` | `yudao-server/`, system or framework modules called directly |
| Web page or interaction | frontend `src/` route/page and its API/store imports | matching TK controller and service |
| Tenant or authentication issue | TK caller plus `yudao-module-system/` | tenant/security framework package used by the call |
| Generation or media processing | TK generation service and `worker/` | FFmpeg helpers and relevant configuration |
| Operations dashboard | `tk-ops-console/server.mjs` and `public/index.html` | `config.json` and database schema references |
| Deployment | latest release plus specific Nginx/systemd/script file | effective production config only when server work is authorized |

## Focused Commands

```powershell
# Operations console syntax
Set-Location tk-ops-console
npm run check

# Backend TK module and dependencies
Set-Location ruoyi-vue-pro-master\ruoyi-vue-pro-master\ruoyi-vue-pro-master
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am test

# Frontend type check
Set-Location yudao-ui\yudao-ui-admin-vue3
..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check
```

