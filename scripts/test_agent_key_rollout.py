"""Offline checks for the production credential rollout's safety boundaries."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("rollout", Path(__file__).with_name("update-agent-gemini-key.py"))
rollout = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rollout)


class RolloutSafetyTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.events = []
        self.fail_chat = False
        self.ready = True
        self.fail_promotion_confirmation = False
        self.template = {"serviceAccountName": "shared@example.invalid", "containers": [{
            "image": "old-image", "resources": {"limits": {"cpu": "1"}},
            "env": [{"name": "GOOGLE_API_KEY", "value": "old-exposed-test-value"},
                    {"name": "VIDYUT_AGENT_MODEL", "value": "gemini-test"},
                    {"name": "VIDYUT_BACKEND_BASE_URL", "value": rollout.BACKEND},
                    {"name": "OPENROUTER_API_KEY", "value": "unchanged-fake-value"}]}]}
        self.state = {"spec": {"template": {"spec": copy.deepcopy(self.template)}}, "status": {
            "url": "https://agent.example.invalid", "latestCreatedRevisionName": "old",
            "latestReadyRevisionName": "old", "traffic": [{"revisionName": "old", "percent": 100}]}}
        self.patchers = [
            patch.object(rollout, "emit", side_effect=lambda state, **kw: self.events.append({"state": state, **kw})),
            patch.object(rollout, "fresh_key", return_value="fresh-fake-value-for-unit-test"),
            patch.object(rollout, "service", side_effect=lambda: copy.deepcopy(self.state)),
            patch.object(rollout, "revision", side_effect=self.revision),
            patch.object(rollout, "command", side_effect=self.command),
            patch.object(rollout, "request_json", side_effect=self.request),
        ]
        for patcher in self.patchers:
            patcher.start()
            self.addCleanup(patcher.stop)

    def revision(self, name):
        ready = self.ready
        if name == "new" and self.fail_promotion_confirmation and rollout.serving_revision(self.state) == "new":
            ready = False
        return {"spec": copy.deepcopy(self.template if name == "old" else self.state["spec"]["template"]["spec"]),
                "status": {"conditions": [{"type": "Ready", "status": "True" if ready else "False"}]}}

    def request(self, url, *, payload, **kwargs):
        if "generateContent" in url:
            self.assertGreaterEqual(payload["generationConfig"]["maxOutputTokens"], 1024)
            return {"candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": "OK"}]}}]}
        if url.endswith("/api/auth/login"):
            return {"data": {"token": "fake-login-token"}}
        self.calls.append(("chat", url))
        self.assertEqual(rollout.serving_revision(self.state), "old")
        self.request_id = payload["requestId"]
        return {"provider": "DETERMINISTIC" if self.fail_chat else "GEMINI", "model": "gemini-test",
                "reply": "OK", "toolCalls": []}

    def command(self, *args, data=None):
        self.calls.append(args)
        if args[:3] == ("iam", "service-accounts", "list") or args[:2] == ("secrets", "list"):
            return "[]"
        if args[:2] == ("secrets", "get-iam-policy"):
            return '{"bindings": []}'
        if args[:3] == ("secrets", "versions", "add"):
            self.assertEqual(data, b"fresh-fake-value-for-unit-test")
            return '{"name": "projects/test/secrets/test/versions/1"}'
        if args[:2] == ("run", "deploy"):
            self.assertIn("--no-traffic", args)
            self.tag = next(a.split("=", 1)[1] for a in args if a.startswith("--tag="))
            runtime = self.state["spec"]["template"]["spec"]
            runtime["serviceAccountName"] = rollout.ACCOUNT
            runtime["containers"][0]["image"] = "new-image"
            runtime["containers"][0]["env"][0] = {"name": "GOOGLE_API_KEY", "valueFrom": {
                "secretKeyRef": {"name": rollout.SECRET_ID, "key": "1"}}}
            self.state["status"].update(latestCreatedRevisionName="new", latestReadyRevisionName="new")
            self.state["status"]["traffic"].append({"revisionName": "new", "tag": self.tag,
                                                   "url": "https://staged.example.invalid"})
        if args[:2] == ("logging", "read"):
            self.calls.append(("logs_checked",))
            return json.dumps([{"jsonPayload": {"event": "agent_chat_completed", "provider": "GEMINI",
                                                  "requestId": self.request_id}}])
        if args[:3] == ("run", "services", "update-traffic"):
            for arg in args:
                if arg.startswith("--to-revisions="):
                    name = arg.split("=", 2)[1]
                    self.state["status"]["traffic"] = [t for t in self.state["status"]["traffic"] if t.get("tag")]
                    self.state["status"]["traffic"].append({"revisionName": name, "percent": 100})
                elif arg.startswith("--remove-tags="):
                    self.state["status"]["traffic"] = [t for t in self.state["status"]["traffic"] if not t.get("tag")]
        return ""

    def test_success_requires_one_chat_and_logs_before_promotion(self):
        rollout.apply()
        self.assertEqual(rollout.serving_revision(self.state), "new")
        self.assertEqual(sum(c[0] == "chat" for c in self.calls), 1)
        promote = next(i for i, c in enumerate(self.calls) if "--to-revisions=new=100" in c)
        self.assertLess(next(i for i, c in enumerate(self.calls) if c[0] == "logs_checked"), promote)
        self.assertEqual(self.events[-1]["state"], "VERIFIED")
        self.assertNotIn("fresh-fake-value-for-unit-test", json.dumps(self.events) + str(self.calls))

    def test_existing_key_is_rejected_before_persistent_operations(self):
        with patch.object(rollout, "fresh_key", return_value="old-exposed-test-value"):
            with self.assertRaisesRegex(RuntimeError, "fresh key"):
                rollout.apply()
        self.assertEqual(self.calls, [])

    def test_generated_container_name_is_normalized_but_custom_name_is_not(self):
        automatic = copy.deepcopy(self.template)
        automatic["containers"][0]["name"] = rollout.SERVICE + "-1"
        self.assertEqual(rollout.comparable_runtime(automatic), rollout.comparable_runtime(self.template))
        automatic["containers"][0]["name"] = "custom-container"
        self.assertNotEqual(rollout.comparable_runtime(automatic), rollout.comparable_runtime(self.template))

    def test_invalid_fresh_key_is_never_stored(self):
        with patch.object(rollout, "validate_key", side_effect=RuntimeError("Google rejected test key")):
            with self.assertRaisesRegex(RuntimeError, "rejected"):
                rollout.apply()
        self.assertEqual(self.calls, [])

    def test_exhausted_thinking_budget_is_not_accepted_as_generated_answer(self):
        with patch.object(rollout, "request_json", return_value={"candidates": [{
            "finishReason": "MAX_TOKENS", "content": {"parts": [{"text": "thinking", "thought": True}]}
        }]}):
            with self.assertRaisesRegex(RuntimeError, "no generated text"):
                rollout.validate_key("fake-test-key", "gemini-test")

    def test_user_supplied_local_key_does_not_open_another_input_window(self):
        with patch.object(rollout, "fresh_local_key", return_value="fresh-fake-value-for-unit-test"), \
             patch.object(rollout, "fresh_key") as window:
            rollout.apply(from_local_env=True)
        window.assert_not_called()
        self.assertEqual(rollout.serving_revision(self.state), "new")

    def test_not_ready_revision_never_receives_chat_or_production_traffic(self):
        self.ready = False
        with self.assertRaisesRegex(RuntimeError, "READY"):
            rollout.apply()
        self.assertEqual(rollout.serving_revision(self.state), "old")
        self.assertFalse(any(c[0] == "chat" or "--to-revisions=new=100" in c for c in self.calls))

    def test_fallback_response_keeps_previous_traffic(self):
        self.fail_chat = True
        with self.assertRaisesRegex(RuntimeError, "successful Gemini"):
            rollout.apply()
        self.assertEqual(rollout.serving_revision(self.state), "old")
        self.assertFalse(any("--to-revisions=new=100" in c for c in self.calls))

    def test_post_promotion_failure_restores_previous_traffic(self):
        self.fail_promotion_confirmation = True
        with self.assertRaisesRegex(RuntimeError, "READY verification"):
            rollout.apply()
        self.assertEqual(rollout.serving_revision(self.state), "old")
        self.assertTrue(any("--to-revisions=old=100" in c for c in self.calls))


if __name__ == "__main__":
    unittest.main()
