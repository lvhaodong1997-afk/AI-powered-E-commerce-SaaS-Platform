# BGM and Audience Audio Implementation Plan

> For agentic workers: use subagent-driven-development and focused TDD. Execute continuously; user authorized implementation, deployment, commit, merge main and push on 2026-10-30.

Goal: deliver owned BGM management, ten optional personal audience audio presets, and measurable 0–100% BGM mixing without changing existing generation architecture.

Architecture: preset CRUD and resolution precede existing generation snapshot creation. Concrete existing voice/BGM task fields remain the execution source; new metadata records preset and mixing versions. Preserve legacy rendering for existing tasks and clients.

Tech stack: Vue 3, Element Plus, Java 8-compatible Spring Boot/MyBatis, MySQL, FFmpeg.

## Global Constraints

- Preserve unrelated original checkout changes. Worktree branch codex/bgm-audience-audio starts at origin/main 4a85f947.
- No new external service, no automatic paid generation or publication, no replay of historical tasks.
- Default audience selection is empty. Selected saved preset overrides audio selections only; clearing restores this submission's manual draft. Do not enable voice if existing flow explicitly disables it.
- Exactly ten categories: SILVER, GEN_Z, WOMEN, SINGLE, PET, MATERNAL, EMOTIONAL, LOWER_TIER, MIDDLE_CLASS, HIGH_NET_WORTH, with the Chinese labels in the approved proposal.
- BGM is soft-disabled, never physically deleted. Only its creator in current scope can delete; system music cannot be deleted. Referenced presets require explicit unbind confirmation and become INCOMPLETE.
- Presets scoped by tenant, non-null normalized company (0 means tenant scope), user; optimistic integer version. New frontend sends audioMixVersion=2; absent version stays 1. Historical task columns default to 1; do not rewrite stored volumes.
- Snapshot resolution happens once before batch expansion; later edit/reset/delete never changes existing tasks or retries.
- Existing request fields: audioPresetId?: number, audioPresetVersion?: number, audioMixVersion?: number. New task metadata: audienceCode, audioPresetId, audioPresetVersion, audioMixVersion.

## Task 1: Backend presets and owned BGM management

Files: new controller/admin/audiopreset, service/audiopreset, TkAudioAudiencePresetDO/Mapper, TkAudioAudienceEnum; existing bgm Controller/Service/RespVO/Mapper; incremental SQL and focused service tests.

Interfaces (binding for other tasks):
- GET /tk/audio-audience-preset/list -> saved preset array; GET /get?id; POST /save with flat voice fields matching generation (ttsProvider,voiceId,voiceCode,voiceProfileId,mimoVoiceMode,mimoVoiceCode,mimoVoicePrompt,mimoVoiceSampleUrl), audienceCode,bgmEnabled,bgmAssetId,bgmVolume,id?,version? -> id; DELETE /reset?id&version.
- Response includes above fields plus id,version,configStatus,companyId. Store allowlisted voice fields in DO.voiceConfig JSON, DO.ttsProvider also available.
- Service: List<TkAudioAudiencePresetRespVO> getList(); TkAudioAudiencePresetRespVO get(Long id); Long save(TkAudioAudiencePresetSaveReqVO req); void reset(Long id,Integer version); TkAudioAudiencePresetDO resolveForGeneration(Long id,Integer expectedVersion,Long companyId).
- DO metadata getId(),getVersion(),getAudienceCode(),getTtsProvider(),getVoiceConfig(),getBgmEnabled(),getBgmAssetId(),getBgmVolume(),getConfigStatus().
- Extend BGM list DTO canDelete:boolean, referencedPresetCount:int. GET /tk/bgm/references?id -> accessible referencing preset summaries (id,audienceCode,version); DELETE /tk/bgm/delete?id&unbindPresets=false. Preserve old deleteUserBgm(Long) overload, add confirmed overload.
- SQL creates preset table and adds task audience_code varchar(32),audio_preset_id bigint,audio_preset_version int,audio_mix_version int NOT NULL DEFAULT 1 using idempotent MySQL migration.

- [ ] Write failing service tests for isolation, stale version, invalid voice/BGM, own-only deletion, reference confirmation and atomic invalidation.
- [ ] Run focused tests and record expected failures.
- [ ] Implement minimal CRUD, voice/BGM validation with existing services, safe row locking and optimistic updates; no circular service dependencies.
- [ ] Run focused tests and mapper/schema validation. Commit task files only.

## Task 2: Versioned audio mixing

Files: new pipeline/TkAudioMixSupport.java; existing DefaultTkVideoRenderService.java and focused tests.
Interface: TkAudioMixSupport.normalizeVersion(Integer) -> 1 or 2 (reject other); normalizeVolume(Double,Integer) preserves V1 .01–.30 default .10 and V2 0–1 default .10. Render selects task.audioMixVersion. Existing buildFinalRenderCommand overloads remain legacy; add overload with Integer audioMixVersion.

- [ ] Write tests for legacy preservation, V2 0 and 100%, all audio branches, short durations.
- [ ] Implement BGM loudnorm before gain in V2, amix normalize=0 and postmix limiter without automatic makeup gain; preserve native opening timing and duration.
- [ ] Verify command tests and actual generated sine/BGM fixtures at 0/10/30/60/100%, decoded amplitude/peak/duration. Commit task files only.

## Task 3: Frontend preset and BGM management

Files: dashboard/index.vue; new components/AudioAudiencePresetDialog.vue,BgmManagerDialog.vue; audioSettings.mjs; API audio-audience-preset; existing BGM/generation API; focused Node state tests.

- [ ] Write failing state tests for select/clear/invalid config, concurrent save version and immutable submission snapshot.
- [ ] Implement optional selector, preset editor reusing current voice choices/provider selection, current BGM manager, reference confirmation and delete-current clearing.
- [ ] Selected preset audio controls locked with origin label; manage entry always available. Preserve saved/custom voices across providers, including MiMo profile selection. Do not silently replace missing tracks.
- [ ] Send optional preset ID/version and mix version 2 in snapshot payload; slider and preview support true zero through one.
- [ ] Verify Node tests, vue-tsc, focused lint and production build; commit task files only.

## Task 4: Generation snapshot integration

Files: GenerationCreateReqVO/RespVO/TaskDO and service; generation tests.

- [ ] Add request/task metadata and use a copy of request for effective preset selection, retaining voiceEnabled and nonaudio fields.
- [ ] Resolve preset after actual tenant/company context has been determined, before batch expansion. Allowlist parsed voice JSON. Persist concrete voice/BGM values and metadata; verify target-language compatibility through existing voice mechanisms.
- [ ] Test manual fallback, preset priority, batch consistency and stale/missing/foreign presets, retries and legacy requests; run combined backend tests and package.

## Task 5: Review, merge and server synchronization

- [ ] Independently review scoped diffs; fix important findings and rerun covering tests.
- [ ] Build exact committed source, preserve production effective configuration and latest external changes. Prepare release backup and migration; apply schema before activating code.
- [ ] Merge feature into latest origin/main, push and verify remote SHA. Deploy exact merged commit, verify JAR/frontend hashes, service/backend/public page and authenticated nonpaid feature smoke. Do not trigger real paid generation/publication.
- [ ] Save sanitized deployment evidence and concise diff/commit/deployment result.
