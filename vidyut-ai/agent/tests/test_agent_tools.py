from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

from vidyut_agent import tools as agent_tools
from vidyut_agent.backend import redact_sensitive
from vidyut_agent.runtime_context import reset_request_context, set_request_context


class ToolTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message="show chargers near Lucknow",
        )

    def tearDown(self) -> None:
        reset_request_context(self.context)

    async def test_find_chargers_maps_search_filters(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = [{"id": 7, "name": "Vidyut Central"}]

            result = await agent_tools.find_chargers(
                query="Lucknow",
                latitude=26.85,
                longitude=80.95,
                connector_type="CCS2",
            )

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with(
            "GET",
            "/api/stations/search",
            params={
                "query": "Lucknow",
                "lat": 26.85,
                "lng": 80.95,
                "radius": 25,
                "connectorType": "CCS2",
                "availableOnly": True,
                "maxPricePerKwh": None,
            },
            authenticated=False,
        )

    async def test_booking_needs_explicit_user_intent(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.book_charger(station_id=7)

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_negated_booking_never_executes(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message="Do not book this charger yet",
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.book_charger(station_id=7)

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_autopilot_preview_is_read_only(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"origin": "Kanpur", "destination": "Delhi"}
            result = await agent_tools.preview_autopilot_trip(
                vehicle_id=2,
                origin="Kanpur",
                destination="Delhi",
                current_battery_percent=42,
                maximum_charging_budget=900,
            )

        self.assertTrue(result["ok"])
        request.assert_awaited_once()
        method, path = request.await_args.args
        self.assertEqual(method, "POST")
        self.assertEqual(path, "/api/ev/autopilot/trips/preview")

    async def test_web_confirmation_copy_authorizes_autopilot_launch(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message=(
                "I explicitly confirm launching this Autopilot trip and reserving "
                "its charging stops. Launch vehicle ID 2 from Kanpur to Delhi, "
                "battery 42%, minimum reserve 15%, maximum charging budget INR 900, "
                "deadline 18:00, trip purpose REST_STOP, optimize for TIME, autonomy "
                "mode ASK_BEFORE_ACTIONS. Call launch_autopilot_trip now. Goal: "
                "Get me to Delhi by 6 PM and don't let my battery fall below 15%."
            ),
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"id": 12, "status": "RESERVED"}
            result = await agent_tools.launch_autopilot_trip(
                vehicle_id=2,
                origin="Kanpur",
                destination="Delhi",
                current_battery_percent=42,
                maximum_charging_budget=900,
            )

        self.assertTrue(result["ok"])
        request.assert_awaited_once()
        method, path = request.await_args.args
        self.assertEqual(method, "POST")
        self.assertEqual(path, "/api/ev/autopilot/trips")

    async def test_mobile_confirmation_labels_authorize_autopilot_launch(self) -> None:
        for copy in ("Confirm tentative bookings", "Authorize full Autopilot"):
            with self.subTest(copy=copy):
                reset_request_context(self.context)
                self.context = set_request_context(
                    authorization="Bearer test-user-token",
                    request_id="request-12345678",
                    user_message=copy,
                )
                with patch.object(
                    agent_tools.backend, "request", new_callable=AsyncMock
                ) as request:
                    request.return_value = {"id": 12, "status": "RESERVED"}
                    result = await agent_tools.launch_autopilot_trip(
                        vehicle_id=2,
                        origin="Kanpur",
                        destination="Delhi",
                        current_battery_percent=42,
                        maximum_charging_budget=900,
                    )

                self.assertTrue(result["ok"])
                request.assert_awaited_once()

    async def test_action_negated_autopilot_confirmations_do_not_launch(self) -> None:
        negative_copies = (
            "Do not launch this trip. I explicitly confirm launching this Autopilot trip as an example.",
            "Don't reserve this trip. I explicitly confirm launching this Autopilot trip.",
            "Never book these charging stops. I explicitly confirm launching this Autopilot trip.",
            "Preview only; I explicitly confirm launching this Autopilot trip for comparison.",
        )
        for negative_copy in negative_copies:
            with self.subTest(copy=negative_copy):
                reset_request_context(self.context)
                self.context = set_request_context(
                    authorization="Bearer test-user-token",
                    request_id="request-12345678",
                    user_message=negative_copy,
                )
                with patch.object(
                    agent_tools.backend, "request", new_callable=AsyncMock
                ) as request:
                    result = await agent_tools.launch_autopilot_trip(
                        vehicle_id=2,
                        origin="Kanpur",
                        destination="Delhi",
                        current_battery_percent=42,
                        maximum_charging_budget=900,
                    )

                self.assertFalse(result["ok"])
                self.assertTrue(result["confirmationRequired"])
                request.assert_not_awaited()

    async def test_mobile_read_only_copy_does_not_launch(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message="No booking or payment happens until you confirm this plan.",
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.launch_autopilot_trip(
                vehicle_id=2,
                origin="Kanpur",
                destination="Delhi",
                current_battery_percent=42,
                maximum_charging_budget=900,
            )

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_stop_swap_needs_explicit_user_intent(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.swap_autopilot_stop(12, 4, 27)

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_charger_unavailable_event_runs_recovery_tool(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message='{"type":"CHARGER_UNAVAILABLE","tripId":12}',
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"id": 12, "status": "REROUTED"}
            result = await agent_tools.handle_charger_unavailable(12)

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with(
            "POST", "/api/ev/autopilot/trips/12/simulate-fault", json={}
        )

    async def test_complete_charging_requires_explicit_intent(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            result = await agent_tools.complete_autopilot_charging(12)

        self.assertFalse(result["ok"])
        self.assertTrue(result["confirmationRequired"])
        request.assert_not_awaited()

    async def test_cancel_booking_maps_to_authenticated_backend(self) -> None:
        reset_request_context(self.context)
        self.context = set_request_context(
            authorization="Bearer test-user-token",
            request_id="request-12345678",
            user_message="Cancel reservation 44",
        )
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"id": 44, "status": "CANCELLED"}
            result = await agent_tools.cancel_booking(44)

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with("POST", "/api/ev/bookings/44/cancel")

    async def test_trip_summary_is_read_only(self) -> None:
        with patch.object(
            agent_tools.backend, "request", new_callable=AsyncMock
        ) as request:
            request.return_value = {"distanceKm": 1420, "chargingStops": 4}
            result = await agent_tools.get_autopilot_trip_summary(12)

        self.assertTrue(result["ok"])
        request.assert_awaited_once_with("GET", "/api/agent/trips/12/summary")

    def test_sensitive_backend_fields_are_removed_recursively(self) -> None:
        value = {
            "id": 1,
            "userId": 99,
            "nested": [{"accessToken": "secret", "batteryPercent": 68}],
        }

        self.assertEqual(
            redact_sensitive(value),
            {"id": 1, "nested": [{"batteryPercent": 68}]},
        )


if __name__ == "__main__":
    unittest.main()
