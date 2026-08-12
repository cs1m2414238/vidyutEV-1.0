# Vidyut Autopilot agent

This service runs Google ADK locally and uses the Gemini Developer API while
Spring Boot remains responsible for authentication, authorization, and data.
The user's bearer token is forwarded only to the Vidyut backend tools; it is
never included in the Gemini prompt.

## Gemini Developer API setup

1. Create an API key at <https://aistudio.google.com/app/apikey>.
2. Copy `vidyut_agent/.env.example` to `vidyut_agent/.env` if needed.
3. Put the key in the ignored `.env` file:

   ```env
   GOOGLE_API_KEY=your-private-key
   VIDYUT_AGENT_MODEL=gemini-3.5-flash
   VIDYUT_BACKEND_BASE_URL=http://localhost:8080
   ```

4. Install and run from this directory:

   ```powershell
   .\.venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   python -m vidyut_agent
   ```

The service listens on `http://127.0.0.1:8001`. Check `/health`, then send chat
requests through Spring Boot at `POST /api/ev/agent/chat` so the verified Vidyut
JWT is forwarded to the agent.

The ADK tool set covers read-only trip preview plus confirmed launch, journey
monitoring, charger-failure recovery, reservation cancellation, stop swapping,
charging completion/AutoPay, wallet status, and wallet top-up. A
`CHARGER_UNAVAILABLE` event can therefore demonstrate the full backend flow:
release the failed booking, select and reserve a compatible replacement,
recalculate the route, and return the updated trip action timeline.

For ADK's development UI, run `adk web` from this directory. The `root_agent`
uses the same Gemini model and tools.

## Later: switch to Vertex AI

Remove `GOOGLE_API_KEY` and configure Google's standard environment variables:

```env
GOOGLE_GENAI_USE_VERTEXAI=TRUE
GOOGLE_CLOUD_PROJECT=your-project-id
GOOGLE_CLOUD_LOCATION=us-central1
VIDYUT_AGENT_MODEL=your-Vertex-supported-model-id
```

No Python tool or agent workflow changes are required. Verify the chosen model
is available in the selected Vertex region before deployment.
