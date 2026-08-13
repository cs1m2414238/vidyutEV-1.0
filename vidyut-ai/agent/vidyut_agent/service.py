from __future__ import annotations

import hashlib
import logging
import uuid
from typing import Any

from fastapi import FastAPI, Header, HTTPException, status
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from pydantic import BaseModel, Field

from .agent import root_agent
from .config import settings
from .runtime_context import reset_request_context, set_request_context


APP_NAME = "vidyut_autopilot"
logger = logging.getLogger(__name__)
session_service = InMemorySessionService()
runner = Runner(
    app_name=APP_NAME,
    agent=root_agent,
    session_service=session_service,
)
model_runners: list[tuple[str, Runner]] = [(settings.model, runner)]
model_runners.extend(
    (
        model,
        Runner(
            app_name=APP_NAME,
            agent=root_agent.model_copy(update={"model": model}),
            session_service=session_service,
        ),
    )
    for model in settings.fallback_models
)

app = FastAPI(
    title="Vidyut Autopilot Agent",
    version="1.0.0",
    docs_url="/docs",
    redoc_url=None,
)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    sessionId: str | None = Field(
        default=None,
        min_length=8,
        max_length=100,
        pattern=r"^[A-Za-z0-9._:-]+$",
    )
    requestId: str | None = Field(
        default=None,
        min_length=8,
        max_length=100,
        pattern=r"^[A-Za-z0-9._:-]+$",
    )


class ToolCallResponse(BaseModel):
    name: str
    status: str


class ChatResponse(BaseModel):
    sessionId: str
    requestId: str
    reply: str
    model: str
    toolCalls: list[ToolCallResponse]
    plan: dict[str, Any] | None = None
    actionResult: dict[str, Any] | None = None


def _caller_id(authorization: str) -> str:
    digest = hashlib.sha256(authorization.encode("utf-8")).hexdigest()
    return f"jwt-{digest[:32]}"


def _text_from_event(event: Any) -> str:
    if not getattr(event, "content", None):
        return ""
    return "".join(
        part.text
        for part in (event.content.parts or [])
        if getattr(part, "text", None)
    ).strip()


def _tool_data(response: Any) -> dict[str, Any] | None:
    value = getattr(response, "response", None)
    if isinstance(value, dict) and isinstance(value.get("result"), dict):
        value = value["result"]
    if not isinstance(value, dict) or value.get("ok") is not True:
        return None
    data = value.get("data")
    return data if isinstance(data, dict) else None


def _record_tool_events(
    event: Any,
    states: dict[str, str],
    artifacts: dict[str, dict[str, Any]],
) -> None:
    for call in event.get_function_calls() or []:
        states[call.name] = "requested"
    for response in event.get_function_responses() or []:
        result = getattr(response, "response", None)
        failed = isinstance(result, dict) and result.get("ok") is False
        states[response.name] = "failed" if failed else "completed"
        data = _tool_data(response)
        if data is not None and response.name == "preview_autopilot_trip":
            artifacts["plan"] = data
        if data is not None and response.name in {
            "launch_autopilot_trip",
            "start_autopilot_monitoring",
            "handle_charger_unavailable",
            "complete_autopilot_charging",
            "swap_autopilot_stop",
            "simulate_autopilot_delay",
            "reroute",
            "cancel_booking",
            "top_up_wallet",
        }:
            artifacts["actionResult"] = data


def _is_quota_error(exc: Exception) -> bool:
    """Recognize quota exhaustion without depending on one SDK exception type."""
    current: BaseException | None = exc
    visited: set[int] = set()
    while current is not None and id(current) not in visited:
        visited.add(id(current))
        code = getattr(current, "code", None)
        status_code = getattr(current, "status_code", None)
        message = str(current).upper()
        if code == 429 or status_code == 429 or "RESOURCE_EXHAUSTED" in message:
            return True
        current = current.__cause__ or current.__context__
    return False


async def _ensure_session(user_id: str, session_id: str) -> None:
    existing = await session_service.get_session(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=session_id,
    )
    if existing is None:
        await session_service.create_session(
            app_name=APP_NAME,
            user_id=user_id,
            session_id=session_id,
        )


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ready" if settings.google_auth_configured else "configuration-required",
        "model": settings.model,
        "fallbackModels": list(settings.fallback_models),
        "geminiAuthenticationConfigured": settings.google_auth_configured,
        "backendBaseUrl": settings.backend_base_url,
    }


@app.post("/v1/chat", response_model=ChatResponse)
async def chat(
    request: ChatRequest,
    authorization: str = Header(..., alias="Authorization"),
) -> ChatResponse:
    if not authorization.startswith("Bearer ") or len(authorization) <= len("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="A valid Vidyut bearer token is required",
        )
    if not settings.google_auth_configured:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=(
                "Gemini authentication is not configured. Add GOOGLE_API_KEY "
                "to vidyut_agent/.env."
            ),
        )

    session_id = request.sessionId or f"session-{uuid.uuid4().hex}"
    request_id = request.requestId or f"request-{uuid.uuid4().hex}"
    user_id = _caller_id(authorization)
    await _ensure_session(user_id, session_id)

    context_tokens = set_request_context(
        authorization=authorization,
        request_id=request_id,
        user_message=request.message,
    )
    reply = ""
    tool_states: dict[str, str] = {}
    artifacts: dict[str, dict[str, Any]] = {}
    used_model = settings.model
    try:
        message = types.Content(
            role="user",
            parts=[types.Part.from_text(text=request.message)],
        )
        last_quota_error: Exception | None = None
        for attempt, (model, active_runner) in enumerate(model_runners):
            try:
                async for event in active_runner.run_async(
                    user_id=user_id,
                    session_id=session_id,
                    invocation_id=request_id if attempt == 0 else f"{request_id}-fallback-{attempt}",
                    new_message=message,
                ):
                    _record_tool_events(event, tool_states, artifacts)
                    if event.is_final_response():
                        final_text = _text_from_event(event)
                        if final_text:
                            reply = final_text
                used_model = model
                break
            except Exception as exc:
                if not _is_quota_error(exc):
                    raise
                last_quota_error = exc
                logger.warning(
                    "Gemini quota exhausted model=%s request_id=%s; fallback_available=%s",
                    model,
                    request_id,
                    attempt + 1 < len(model_runners),
                )
                # Never replay an agent turn after a tool may already have changed
                # state. The caller receives a precise retryable quota response.
                if tool_states:
                    break
        else:
            last_quota_error = last_quota_error or RuntimeError("Gemini quota exhausted")

        if not reply and last_quota_error is not None:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=(
                    "Gemini quota is currently exhausted for the configured models. "
                    "Retry after the quota window resets or configure billing."
                ),
            ) from last_quota_error
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception(
            "Gemini ADK invocation failed request_id=%s session_id=%s",
            request_id,
            session_id,
        )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Gemini could not complete the request. Check the API key and quota.",
        ) from exc
    finally:
        reset_request_context(context_tokens)

    if not reply:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Gemini returned an empty response",
        )

    return ChatResponse(
        sessionId=session_id,
        requestId=request_id,
        reply=reply,
        model=used_model,
        toolCalls=[
            ToolCallResponse(name=name, status=tool_status)
            for name, tool_status in tool_states.items()
        ],
        plan=artifacts.get("plan"),
        actionResult=artifacts.get("actionResult"),
    )
