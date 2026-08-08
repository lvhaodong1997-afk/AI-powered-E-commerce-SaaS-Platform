# Voice Profile Name Parameter Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure voice-profile creation always sends the visible name and consent value as request parameters while uploading only the selected file as multipart data.

**Architecture:** Keep the change local to the voice dialog and voice API client. The dialog passes typed scalar values and the file; the API client builds `FormData` for the file and sends the scalars through Axios `params`, which the existing backend `@RequestParam` contract accepts.

**Tech Stack:** Vue 3, TypeScript, Axios, Element Plus

## Global Constraints

- Modify frontend code only.
- Do not change the shared Axios upload wrapper or unrelated upload APIs.
- Test only the changed request structure and TypeScript compilation.

---

### Task 1: Preserve Voice Creation Parameters

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/api/tk/voice/index.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/voice/components/VoiceProfileDialog.vue`

**Interfaces:**
- Consumes: trimmed voice name, confirmed consent state, and selected `File`
- Produces: `TkVoiceProfileApi.create(name: string, consentConfirmed: boolean, file: File): Promise<number>`

- [ ] **Step 1: Change the voice API request contract**

Replace the `FormData` argument with explicit arguments. Build file-only multipart data and attach scalar request parameters:

```ts
create: async (name: string, consentConfirmed: boolean, file: File): Promise<number> => {
  const data = new FormData()
  data.append('file', file)
  return await request.upload({
    url: '/tk/voice-profile/create',
    data,
    params: { name, consentConfirmed },
    timeout: 300_000
  })
}
```

- [ ] **Step 2: Update the dialog call site**

Replace local `FormData` construction with the typed API call:

```ts
await TkVoiceProfileApi.create(name.value.trim(), consent.value, file.value)
```

- [ ] **Step 3: Verify only the changed request structure**

Run:

```powershell
rg -n -A8 "create: async \(name: string" src/api/tk/voice/index.ts
rg -n "TkVoiceProfileApi.create\(name.value.trim\(\), consent.value, file.value\)" src/views/tk/voice/components/VoiceProfileDialog.vue
```

Expected: the API request contains `params: { name, consentConfirmed }`, FormData contains only `file`, and the dialog passes all three explicit arguments.

- [ ] **Step 4: Run the existing TypeScript check**

Run from `yudao-ui/yudao-ui-admin-vue3/`:

```powershell
..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check
```

Expected: exit code `0` with no TypeScript errors.
