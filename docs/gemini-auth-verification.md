# Gemini credential diagnosis — 31 August 2026

The supplied attachment was treated as troubleshooting context. Its suggested deployment and dependency changes were not executed automatically.

Historical findings before the fresh-key rollout:

- The earlier chat-shared credential generated `OK` through Google's official `generateContent` endpoint: HTTP 200, model `gemini-3.6-flash`. That credential was used only for the earlier diagnosis and is not authorized for production storage.
- The credential on serving Cloud Run revision `vidyut-agent-00020-crb` returned HTTP 401, `UNAUTHENTICATED`, reason `ACCESS_TOKEN_TYPE_UNSUPPORTED`, for the same generic request. No journey data was sent.
- Public traffic still serves `00020-crb`. The later service template, `00024-mgt`, has an empty `GOOGLE_API_KEY`. Activating that template as-is would not fix authentication.
- No duplicate `GEMINI_API_KEY` or enabled Vertex mode was found. Local packages are `google-adk==2.6.3` and `google-genai==2.17.0`. The evidence does not justify changing routing logic or upgrading these dependencies.
- A deployed `/v1/chat` probe could not obtain its demo login within the timeout, so an actual production ADK response has **not** been verified. `/health` showing configured authentication is insufficient evidence of successful Gemini generation.
- A later read of production logs for the Host fallback screenshot confirmed the serving `00020-crb` revision still returns Gemini 401 `ACCESS_TOKEN_TYPE_UNSUPPORTED`; OpenRouter then returns 402 because its remaining credits cannot cover the requested response. The displayed `Rules fallback · deterministic-host-fallback` badge accurately reports the backend fallback. Traffic remains 100% on `00020-crb`; the credential fix has not been applied.

Google documents authorization keys and automatic `GOOGLE_API_KEY` / `GEMINI_API_KEY` detection; when both variables exist, `GOOGLE_API_KEY` takes precedence: <https://ai.google.dev/gemini-api/docs/api-key>.

## Fresh-key-only update — deployed and verified

- Serving revision: `vidyut-agent-00028-sof`, READY, with 100% traffic. Previous revision: `vidyut-agent-00020-crb`.
- Gemini secret reference: `GOOGLE_API_KEY` → `vidyut-agent-gemini-key:1`; the key value was never included in command arguments or displayed.
- Runtime identity: `vidyut-agent-runtime@vidyut-autopilot.iam.gserviceaccount.com`. The secret's only direct IAM binding grants this identity `roles/secretmanager.secretAccessor`.
- One Host `/v1/chat` test succeeded using `GEMINI`, model `gemini-3.6-flash`, with no tool calls. Request ID: `gemini-key-check-cc2129d29978492da5c071ebae2157e4`.
- The matching `agent_chat_completed` log identified Gemini. The retrieved new-revision logs contained no `UNAUTHENTICATED`, no `ACCESS_TOKEN_TYPE_UNSUPPORTED`, and no entered secret value.
- The revision was verified using a temporary zero-traffic tag before promotion; that tag was removed after switching traffic.
- Backend remains `vidyut-backend-00045-bx4` at 100% with its original runtime account. Frontend was not deployed or modified for this credential task.
- OpenRouter remains unchanged. A separate generic OpenRouter test was rejected by auto-review as outside the Gemini credential approval; no retry or alternative test was attempted. This does not negate the verified Gemini result.

The user now authorizes Secret Manager storage, agent-only secret access, an agent revision, readiness and `/v1/chat` verification, and production promotion with rollback. **The previously shared credential must never be stored or reused.** The user subsequently supplied a new Gemini key in the ignored local `vidyut-ai/agent/vidyut_agent/.env` and asked to continue using the updated agent environment. The file is excluded from both Git and the Cloud Run build upload.

Only key-presence and equality checks were printed. The new local Google value differs from the serving revision. The local OpenRouter value is identical to the existing production value, so it is not being treated as a fresh replacement and remains unchanged.

The earlier masked-input attempt stopped before creating cloud resources because the 32-token provider test returned no answer. A new generic test showed HTTP 200 with `MAX_TOKENS` at a 32-token limit and HTTP 200 with a complete `STOP` response at 1024 tokens. The validator now allows 1024 tokens and requires a completed answer rather than thought text. This fixes an overly small test budget without weakening authentication verification.

`scripts/update-agent-gemini-key.py` normally opens a masked local input window and requires the operator to confirm that the key was freshly created and never exposed in chat or terminal output. For the user's supplied local replacement, `--from-local-env` reads that existing private file directly into memory. It never copies its values into commands, logs, or a new local file. It rejects the current serving credential and verifies the replacement against Google before storing it. The script cannot independently certify a key's entire exposure history and relies on the user's freshness statement.

Read-only preflight passes. The only non-key difference between the pending template and serving revision is Cloud Run's automatically generated single-container name. No unrelated runtime changes are present.

Before rollout, the backend and agent shared the default compute service account. The rollout assigned `vidyut-agent-runtime@vidyut-autopilot.iam.gserviceaccount.com` to **only the agent**, granting that identity `roles/secretmanager.secretAccessor` on the dedicated `vidyut-agent-gemini-key` secret. It did not add project-wide access or alter the backend identity. Existing inherited project administrators were not changed.

The staged deployment:

1. Tests the fresh key with a generic Google request, without any journey data.
2. Stores the key through stdin, never a command argument or local file, and pins its secret version to `GOOGLE_API_KEY`.
3. Builds **only the agent** with `--no-traffic` and a temporary verification tag.
4. Verifies the new revision is READY while the previous revision retains 100% public traffic.
5. Makes one generic `/v1/chat` request using the Host workspace and demo Host login, without requesting tools or private journey data.
6. Requires a nonempty `GEMINI` response, no tool calls, a matching `agent_chat_completed` log, no authentication errors, and no entered key value in the new revision's retrieved logs.
7. Promotes 100% traffic only after those checks pass, checks readiness and traffic again, and removes the temporary tag. Failure keeps or restores the previous revision.

Agent logging now records the successful provider/model/request ID without prompts, replies, tokens, or keys. Provider exception logging retains only error class, numeric status, and allowlisted reason codes instead of raw SDK exception bodies. Backend and frontend source and services are untouched by this credential task.

Validation: 48 agent tests and nine offline rollout-safety tests pass, including preventing promotion after fallback, rejecting an old key, preserving traffic for a non-READY revision, restoring traffic after a failed promotion check, and rejecting incomplete thought-only provider responses. The live fresh-key Google response, deployed Host `/v1/chat` response, provider log, and production promotion all passed verification. This is a provider connectivity check, not a replay of a real vehicle journey or a guarantee about future provider quota.

To open the masked local window after the approved preflight:

```powershell
.\vidyut-ai\agent\.venv\Scripts\python.exe scripts/update-agent-gemini-key.py --apply
```

Earlier auto-review denials preceded the user's conditional approval and occurred before the updater started. Those attempts created no secret, IAM binding, or revision. The current approval does not authorize storing the exposed key.

The pasted credential should be rotated because it was shared in chat. Enter a replacement through hidden input rather than pasting it into another message. Fresh journey-coordinate sharing with public routing services remains a separate, unapproved action.
