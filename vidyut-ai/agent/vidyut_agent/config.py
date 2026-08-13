from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


PACKAGE_DIR = Path(__file__).resolve().parent
load_dotenv(PACKAGE_DIR / ".env", override=False)


def _positive_float(name: str, default: float) -> float:
    raw = os.getenv(name, str(default)).strip()
    try:
        value = float(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be a number") from exc
    if value <= 0:
        raise RuntimeError(f"{name} must be greater than zero")
    return value


def _env_true(name: str) -> bool:
    return os.getenv(name, "").strip().lower() in {"1", "true", "yes"}


@dataclass(frozen=True)
class Settings:
    model: str
    fallback_models: tuple[str, ...]
    backend_base_url: str
    backend_timeout_seconds: float

    @property
    def google_auth_configured(self) -> bool:
        if _env_true("GOOGLE_GENAI_USE_VERTEXAI") or _env_true(
            "GOOGLE_GENAI_USE_ENTERPRISE"
        ):
            return bool(
                os.getenv("GOOGLE_CLOUD_PROJECT", "").strip()
                and os.getenv("GOOGLE_CLOUD_LOCATION", "").strip()
            )
        return bool(
            os.getenv("GOOGLE_API_KEY", "").strip()
            or os.getenv("GEMINI_API_KEY", "").strip()
        )


def load_settings() -> Settings:
    model = os.getenv("VIDYUT_AGENT_MODEL", "gemini-3.5-flash").strip()
    if not model:
        raise RuntimeError("VIDYUT_AGENT_MODEL cannot be empty")
    fallback_models = tuple(
        candidate
        for candidate in (
            value.strip()
            for value in os.getenv(
                "VIDYUT_AGENT_FALLBACK_MODELS", "gemini-3.5-flash-lite"
            ).split(",")
        )
        if candidate and candidate != model
    )

    backend_base_url = os.getenv(
        "VIDYUT_BACKEND_BASE_URL", "http://localhost:8080"
    ).strip().rstrip("/")
    if not backend_base_url.startswith(("http://", "https://")):
        raise RuntimeError("VIDYUT_BACKEND_BASE_URL must be an HTTP(S) URL")

    return Settings(
        model=model,
        fallback_models=fallback_models,
        backend_base_url=backend_base_url,
        backend_timeout_seconds=_positive_float(
            "VIDYUT_BACKEND_TIMEOUT_SECONDS", 15
        ),
    )


settings = load_settings()
