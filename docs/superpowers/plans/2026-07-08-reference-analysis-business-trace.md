# Reference Analysis Business Trace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI reference analysis the starting point of the same business trace ID used by generation, publishing, and super-admin log lookup.

**Architecture:** Add `businessTraceId` to `tk_reference_analysis`, generate it when a new AI analysis is created, and pass it into `REFERENCE_ANALYSIS` business logs. When a generation task is created from a reference analysis, inherit that trace ID instead of creating a new one.

**Tech Stack:** Java 8/Spring Boot, MyBatis Plus, MySQL, Vue 3/Element Plus/TypeScript.

## Global Constraints

- Do not add a normal-user log viewing entry; users can only see/copy trace IDs.
- Historical records must keep displaying; nullable DB fields are required.
- Use existing `TkBusinessTraceIdGenerator` and `TkBusinessLogService` overloads.
- Use repo-bundled Maven and pnpm under `.runtime`.

---

### Task 1: Backend Trace Propagation

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/dataobject/TkReferenceAnalysisDO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/reference/vo/TkReferenceAnalysisRespVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/reference/vo/TkReferenceAnalysisPageReqVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkReferenceAnalysisMapper.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/TkReferenceAnalysisServiceImpl.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImpl.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/reference/TkReferenceAnalysisServiceImplTest.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImplTest.java`

**Interfaces:**
- Produces: `TkReferenceAnalysisDO.businessTraceId`
- Produces: `TkReferenceAnalysisRespVO.businessTraceId`
- Produces: `TkReferenceAnalysisPageReqVO.businessTraceId`
- Consumes: `TkBusinessTraceIdGenerator.generate(Long tenantId)`

- [ ] Write failing tests for failed analysis trace persistence and generation inheritance.
- [ ] Implement minimal backend fields and propagation.
- [ ] Run focused tests until green.

### Task 2: SQL and Frontend

**Files:**
- Create: `yudao-module-tk/src/main/resources/sql/tk_reference_analysis_trace_id_upgrade_mysql.sql`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_reference_analysis_upgrade_mysql.sql`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_mysql.sql`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/tk/reference/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`

**Interfaces:**
- Consumes: backend `businessTraceId` response field.
- Produces: dashboard trace display and copy action with no log navigation.

- [ ] Add nullable `business_trace_id` and trace index SQL.
- [ ] Add TypeScript field to reference analysis API type.
- [ ] Show/copy the trace ID on the AI analysis result panel.
- [ ] Run `pnpm ts:check` and `pnpm lint:eslint:check`.

### Task 3: Deploy

**Files:**
- Build output: `yudao-server/target/yudao-server.jar`
- Build output: `yudao-ui/yudao-ui-admin-vue3/dist-prod`

- [ ] Run backend TK tests and package server jar.
- [ ] Run frontend checks and production build.
- [ ] Upload jar, frontend tarball, and SQL to `/data/Tk/deploy`.
- [ ] Execute SQL through SSH tunnel.
- [ ] Back up and replace `/data/Tk/current/app/yudao-server.jar` and `/data/Tk/current/web/dist`.
- [ ] Restart Java process and verify public site/API.
