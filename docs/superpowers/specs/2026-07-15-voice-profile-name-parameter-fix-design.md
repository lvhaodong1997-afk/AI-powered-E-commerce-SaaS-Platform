# Voice Profile Name Parameter Fix Design

## Scope

Fix only the frontend voice-profile creation request where the visible voice name is not received by the backend. Do not change backend code, Nginx, the shared upload wrapper, or unrelated upload flows.

## Design

Keep the selected file in `FormData`. Pass `name` and `consentConfirmed` through Axios `params` on `POST /tk/voice-profile/create`. The existing backend `@RequestParam` parameters accept query parameters, while the multipart body continues to carry the file.

`VoiceProfileDialog.vue` will pass the trimmed name, consent value, and selected file explicitly to the voice API. `src/api/tk/voice/index.ts` will construct the file-only `FormData` and attach the two scalar values as request parameters.

## Error Handling

Retain the existing required-field gating and request error handling. This change does not alter global error messages.

## Verification

Run only focused verification for the changed voice-profile API request shape and the narrowest TypeScript check needed for the two modified files. Do not run unrelated full builds or backend tests.
