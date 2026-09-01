"""Fresh-key-only rollout of vidyut-agent; masked input, staged test, and rollback.

--preflight is read-only. --apply opens a masked local input window. If the user
has supplied a fresh key in the ignored local agent .env, --from-local-env reads
that existing file in memory instead. No key is accepted as a command argument,
printed, or written to a new local file. Only operational metadata is saved.
"""
from __future__ import annotations

import argparse
import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

PROJECT = "vidyut-autopilot"
REGION = "asia-south1"
SERVICE = "vidyut-agent"
SECRET_ID = "vidyut-agent-gemini-key"
ACCOUNT_ID = "vidyut-agent-runtime"
ACCOUNT = f"{ACCOUNT_ID}@{PROJECT}.iam.gserviceaccount.com"
BACKEND = "https://vidyut-backend-558967442483.asia-south1.run.app"
ROOT = Path(__file__).resolve().parents[1]
STATUS_FILE = ROOT / "tmp" / "agent-credential-rollout-status.json"


def emit(state: str, **details: object) -> None:
    # Callers pass only allowlisted metadata, never raw API responses.
    record = {"state": state, **details}
    STATUS_FILE.parent.mkdir(exist_ok=True)
    pending = STATUS_FILE.with_suffix(".pending")
    pending.write_text(json.dumps(record, indent=2), encoding="utf-8")
    pending.replace(STATUS_FILE)
    if sys.stdout is not None:
        print(json.dumps(record), flush=True)


def command(*args: str, data: bytes | None = None) -> str:
    cloud = shutil.which("gcloud.cmd") or shutil.which("gcloud")
    if not cloud:
        raise RuntimeError("gcloud is not installed")
    env = dict(os.environ, CLOUDSDK_CORE_LOG_HTTP="false", CLOUDSDK_CORE_VERBOSITY="warning")
    result = subprocess.run([cloud, *args, "--project", PROJECT, "--quiet"],
                            input=data, capture_output=True, env=env, timeout=1800)
    if result.returncode:
        # Never echo raw stderr or describe responses: either could contain secrets.
        raise RuntimeError("gcloud operation failed: " + " ".join(args[:3]))
    return result.stdout.decode("utf-8").strip()


def service() -> dict:
    return json.loads(command("run", "services", "describe", SERVICE,
                              "--region", REGION, "--format=json"))


def revision(name: str) -> dict:
    return json.loads(command("run", "revisions", "describe", name,
                              "--region", REGION, "--format=json"))


def comparable_runtime(spec: dict, *, deployment: bool = False) -> dict:
    safe = copy.deepcopy(spec)
    if deployment:
        safe.pop("serviceAccountName", None)
    for container in safe.get("containers", []):
        # Cloud Run fills this generated name into revisions, while the service
        # template can omit it. No user-defined or multi-container names differ.
        if len(safe.get("containers", [])) == 1 and container.get("name") == SERVICE + "-1":
            container.pop("name")
        if deployment:
            container.pop("image", None)
        container["env"] = sorted(
            (v for v in container.get("env", []) if v["name"] != "GOOGLE_API_KEY"),
            key=lambda v: v["name"],
        )
    return safe


def serving_revision(state: dict) -> str:
    traffic = [t for t in state["status"].get("traffic", []) if t.get("percent", 0) > 0]
    if len(traffic) != 1 or traffic[0]["percent"] != 100:
        raise RuntimeError("Expected exactly one revision with 100 percent traffic")
    return traffic[0]["revisionName"]


def is_ready(state: dict) -> bool:
    return any(c.get("type") == "Ready" and c.get("status") == "True"
               for c in state.get("status", {}).get("conditions", []))


def preflight() -> tuple[dict, str, dict]:
    before = service()
    previous = serving_revision(before)
    active = revision(previous)
    runtime = before["spec"]["template"]["spec"]
    if comparable_runtime(runtime) != comparable_runtime(active["spec"]):
        raise RuntimeError("Pending runtime changes extend beyond GOOGLE_API_KEY; review them first")
    variables = {v["name"]: v for v in runtime["containers"][0].get("env", [])}
    for name in ("GOOGLE_GENAI_USE_VERTEXAI", "GOOGLE_GENAI_USE_ENTERPRISE", "VIDYUT_AGENT_DISABLE_GEMINI"):
        if variables.get(name, {}).get("value", "").lower() in {"true", "1", "yes"}:
            raise RuntimeError("Gemini API-key mode must be enabled before this rollout")
    duplicate = variables.get("GEMINI_API_KEY", {})
    if duplicate.get("value") or duplicate.get("valueFrom"):
        raise RuntimeError("Resolve duplicate GEMINI_API_KEY before rotation")
    if variables.get("VIDYUT_BACKEND_BASE_URL", {}).get("value", "").rstrip("/") != BACKEND:
        raise RuntimeError("Backend URL does not match the reviewed production backend")
    emit("PREFLIGHT_OK", previousRevision=previous, agentRuntimeAccount=ACCOUNT,
         secret=SECRET_ID, backendAndFrontendUnchanged=True)
    return before, previous, variables


def fresh_key() -> str:
    import tkinter as tk
    window = tk.Tk()
    window.title("Vidyut — fresh Gemini key")
    window.geometry("620x310")
    window.resizable(False, False)
    window.attributes("-topmost", True)
    tk.Label(window, text="Agent-only deployment", font=("Segoe UI", 15, "bold")).pack(pady=(18, 8))
    tk.Label(window, text="Paste a NEW key below. It stays masked and is never sent to chat.\n"
             "Do not use any key previously shared in chat or terminal output.",
             font=("Segoe UI", 10)).pack(pady=6)
    entry = tk.Entry(window, show="\u2022", width=65, font=("Segoe UI", 11))
    entry.pack(padx=24, pady=14)
    confirmed = tk.BooleanVar(value=False)
    tk.Checkbutton(window, variable=confirmed, text="I created this key fresh and have never exposed it.",
                   font=("Segoe UI", 10)).pack()
    feedback = tk.Label(window, text="Google test → Secret Manager → READY revision → chat/log check → traffic",
                        font=("Segoe UI", 9))
    feedback.pack(pady=12)
    values: list[str] = []

    def submit() -> None:
        candidate = entry.get().strip()
        if not confirmed.get() or not candidate or any(c.isspace() for c in candidate):
            feedback.configure(text="Enter a nonempty key and confirm that it is fresh.")
            return
        values.append(candidate)
        entry.delete(0, "end")
        window.destroy()

    tk.Button(window, text="Test and deploy agent", command=submit, font=("Segoe UI", 10)).pack()
    entry.focus_set()
    emit("WAITING_FOR_FRESH_KEY", input="Masked local window; do not send the key in chat")
    window.mainloop()
    if not values:
        raise RuntimeError("Fresh-key entry was cancelled; no production changes applied")
    return values.pop()


def fresh_local_key() -> str:
    """Use only the local credential file the user explicitly updated for rollout."""
    from dotenv import dotenv_values
    path = ROOT / "vidyut-ai" / "agent" / "vidyut_agent" / ".env"
    values = dotenv_values(path, interpolate=False)
    credential = (values.get("GOOGLE_API_KEY") or "").strip()
    duplicate = (values.get("GEMINI_API_KEY") or "").strip()
    if not credential or any(c.isspace() for c in credential):
        raise RuntimeError("The user-updated local file must contain a nonempty GOOGLE_API_KEY")
    if duplicate and duplicate != credential:
        raise RuntimeError("The local file contains conflicting Gemini credentials")
    return credential


def request_json(url: str, *, payload: dict, headers: dict | None = None, timeout: int = 60) -> dict:
    request = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"HTTP request failed with status {error.code}") from None
    except (urllib.error.URLError, TimeoutError):
        raise RuntimeError("HTTP request failed or timed out; no response body logged") from None


def validate_key(credential: str, model: str) -> None:
    result = request_json(
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        payload={"contents": [{"parts": [{"text": "Reply only with OK."}]}],
                 # Thinking consumes the output budget too. A 32-token test can
                 # authenticate successfully but end before producing any answer.
                 "generationConfig": {"maxOutputTokens": 1024}},
        headers={"x-goog-api-key": credential}, timeout=45,
    )
    if not any(p.get("text", "").strip() and not p.get("thought")
               and c.get("finishReason") == "STOP" for c in result.get("candidates", [])
               for p in c.get("content", {}).get("parts", [])):
        raise RuntimeError("Gemini returned no generated text; do not store this credential")


def verify_chat(url: str, token: str, request_id: str) -> dict:
    response = request_json(url + "/v1/chat", headers={"Authorization": "Bearer " + token},
        payload={"workspace": "HOST", "sessionId": request_id, "requestId": request_id,
                 "message": "This is only a provider connectivity test. Reply only with OK. "
                            "Do not call tools or access any vehicle, journey, location, property, "
                            "company, booking, or wallet data."}, timeout=180)
    if response.get("provider") != "GEMINI" or not response.get("reply", "").strip():
        raise RuntimeError("The new revision did not produce a successful Gemini response")
    if response.get("toolCalls") or response.get("plan") or response.get("actionResult"):
        raise RuntimeError("Provider test unexpectedly invoked an application tool")
    return {"provider": "GEMINI", "model": response.get("model"), "requestId": request_id}


def verify_logs(name: str, request_id: str, credential: str) -> None:
    for attempt in range(12):
        raw = command("logging", "read",
            f"resource.type=cloud_run_revision AND resource.labels.service_name={SERVICE} "
            f"AND resource.labels.revision_name={name}",
            "--freshness=30m", "--limit=2000", "--format=json")
        if credential in raw:
            raise RuntimeError("Credential detected in new revision logs; rotate it again; values suppressed")
        if any(marker in raw for marker in ("UNAUTHENTICATED", "ACCESS_TOKEN_TYPE_UNSUPPORTED")):
            raise RuntimeError("Authentication failure found in the new revision logs")
        for record in json.loads(raw):
            content = record.get("jsonPayload") or record.get("textPayload") or ""
            text = json.dumps(content) if isinstance(content, dict) else content
            if "agent_chat_completed" in text and request_id in text and "GEMINI" in text:
                return
        emit("WAITING_FOR_VERIFICATION_LOG", revision=name, attempt=attempt + 1)
        time.sleep(5)
    raise RuntimeError("Gemini success could not be confirmed in Cloud Run logs")


def apply(*, from_local_env: bool = False) -> None:
    before, previous, variables = preflight()
    runtime = before["spec"]["template"]["spec"]
    credential = fresh_local_key() if from_local_env else fresh_key()
    previous_key = {v["name"]: v.get("value") for v in revision(previous)["spec"]["containers"][0].get("env", [])}.get("GOOGLE_API_KEY")
    if credential == previous_key:
        raise RuntimeError("The key matches the old serving credential; a fresh key is required")
    previous_key = None
    model = variables.get("VIDYUT_AGENT_MODEL", {}).get("value", "gemini-3.6-flash")
    tag = "key-check-" + uuid.uuid4().hex[:8]
    promoted = False
    staging_started = False
    new_revision = None
    try:
        emit("TESTING_FRESH_KEY")
        validate_key(credential, model)
        # Ensure the generic smoke test can log in before persistent changes.
        login = request_json(BACKEND + "/api/auth/login", payload={
            "email": "demo.host@vidyut.com", "password": "VidyutDemo@2026"})
        token = login.get("data", {}).get("token")
        if not token:
            raise RuntimeError("Demo authentication is unavailable; deployment has not started")
        if service()["spec"] != before["spec"]:
            raise RuntimeError("Agent changed while waiting for input; review concurrent changes")
        emit("STORING_TESTED_FRESH_KEY", secret=SECRET_ID, runtimeAccount=ACCOUNT)
        accounts = json.loads(command("iam", "service-accounts", "list", "--filter=email=" + ACCOUNT, "--format=json"))
        if not accounts:
            command("iam", "service-accounts", "create", ACCOUNT_ID,
                    "--display-name=Vidyut Agent Runtime", "--format=none")
        existing = json.loads(command("secrets", "list", "--filter=name:" + SECRET_ID, "--format=json"))
        if not any(s["name"].endswith("/" + SECRET_ID) for s in existing):
            command("secrets", "create", SECRET_ID, "--replication-policy=automatic",
                    "--labels=application=vidyut-agent", "--format=none")
        policy = json.loads(command("secrets", "get-iam-policy", SECRET_ID, "--format=json"))
        member = "serviceAccount:" + ACCOUNT
        for binding in policy.get("bindings", []):
            if any(existing_member != member for existing_member in binding.get("members", [])):
                raise RuntimeError("The dedicated secret has unexpected direct IAM bindings")
        added = json.loads(command("secrets", "versions", "add", SECRET_ID,
                                   "--data-file=-", "--format=json", data=credential.encode()))
        version = added["name"].rsplit("/", 1)[-1]
        command("secrets", "add-iam-policy-binding", SECRET_ID, "--member=" + member,
                "--role=roles/secretmanager.secretAccessor", "--condition=None", "--format=none")
        emit("BUILDING_AGENT_WITH_ZERO_TRAFFIC", previousRevision=previous)
        staging_started = True
        command("run", "deploy", SERVICE, "--source", str(ROOT / "vidyut-ai" / "agent"),
                "--region", REGION, "--no-traffic", "--tag=" + tag,
                "--service-account=" + ACCOUNT, "--remove-env-vars=GOOGLE_API_KEY",
                "--update-secrets=GOOGLE_API_KEY=" + SECRET_ID + ":" + version, "--format=none")
        staged = service()
        new_revision = staged["status"]["latestCreatedRevisionName"]
        if not is_ready(revision(new_revision)) or staged["status"].get("latestReadyRevisionName") != new_revision:
            raise RuntimeError("New revision is not READY; previous revision retains traffic")
        if serving_revision(staged) != previous:
            raise RuntimeError("Traffic moved unexpectedly before verification")
        new_runtime = staged["spec"]["template"]["spec"]
        if comparable_runtime(new_runtime, deployment=True) != comparable_runtime(runtime, deployment=True):
            raise RuntimeError("Unexpected runtime settings changed")
        if new_runtime.get("serviceAccountName") != ACCOUNT:
            raise RuntimeError("The new revision does not use its dedicated runtime account")
        new_vars = {v["name"]: v for v in new_runtime["containers"][0].get("env", [])}
        reference = new_vars.get("GOOGLE_API_KEY", {}).get("valueFrom", {}).get("secretKeyRef", {})
        if reference.get("name") != SECRET_ID or reference.get("key") != version:
            raise RuntimeError("The new revision does not use the pinned secret version")
        urls = [t["url"] for t in staged["status"].get("traffic", [])
                if t.get("tag") == tag and t.get("revisionName") == new_revision]
        if len(urls) != 1:
            raise RuntimeError("No unique zero-traffic revision URL was returned")
        request_id = "gemini-key-check-" + uuid.uuid4().hex
        emit("VERIFYING_READY_REVISION", revision=new_revision, requestId=request_id)
        proof = verify_chat(urls[0], token, request_id)
        token = None
        verify_logs(new_revision, request_id, credential)
        if service()["spec"] != staged["spec"]:
            raise RuntimeError("Agent changed during verification; not promoting this revision")
        emit("PROMOTING_VERIFIED_REVISION", revision=new_revision, **proof)
        command("run", "services", "update-traffic", SERVICE, "--region", REGION,
                "--to-revisions=" + new_revision + "=100", "--format=none")
        promoted = True
        after = service()
        if serving_revision(after) != new_revision or not is_ready(revision(new_revision)):
            raise RuntimeError("Production traffic/READY verification failed")
        if after["status"]["url"] != before["status"]["url"]:
            raise RuntimeError("The public service URL changed unexpectedly")
        command("run", "services", "update-traffic", SERVICE, "--region", REGION,
                "--remove-tags=" + tag, "--format=none")
        emit("VERIFIED", revision=new_revision, previousRevision=previous, trafficPercent=100,
             secret=SECRET_ID, secretVersion=version, runtimeAccount=ACCOUNT,
             url=after["status"]["url"], geminiSuccessLogged=True,
             authenticationErrorsFound=False, secretFoundInRevisionLogs=False,
             backendAndFrontendUnchanged=True, **proof)
    except BaseException:
        if staging_started:
            # Also handles a traffic operation that applied but timed out locally.
            current = service()
            if promoted or serving_revision(current) != previous:
                command("run", "services", "update-traffic", SERVICE, "--region", REGION,
                        "--to-revisions=" + previous + "=100", "--format=none")
            if any(t.get("tag") == tag for t in current["status"].get("traffic", [])):
                command("run", "services", "update-traffic", SERVICE, "--region", REGION,
                        "--remove-tags=" + tag, "--format=none")
            if serving_revision(service()) != previous:
                raise RuntimeError("Automatic rollback could not be confirmed; inspect agent traffic") from None
            emit("PREVIOUS_TRAFFIC_PRESERVED", previousRevision=previous, rejectedRevision=new_revision)
        raise
    finally:
        credential = None


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_mutually_exclusive_group(required=True)
    actions.add_argument("--apply", action="store_true")
    actions.add_argument("--preflight", action="store_true")
    parser.add_argument("--from-local-env", action="store_true",
                        help="Use the ignored agent .env only after the user supplies a fresh key there")
    args = parser.parse_args()
    try:
        preflight() if args.preflight else apply(from_local_env=args.from_local_env)
    except BaseException as error:
        # Only our fixed RuntimeError messages are safe. Do not format arbitrary
        # SDK, OS, subprocess, HTTP, or GUI exceptions containing user input.
        detail = str(error) if type(error) is RuntimeError else type(error).__name__
        emit("STOPPED", reason=detail)
        raise SystemExit(1)
