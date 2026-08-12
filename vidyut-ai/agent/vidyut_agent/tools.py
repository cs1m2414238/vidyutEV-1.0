from __future__ import annotations

from collections.abc import Awaitable, Callable
import re
from typing import Any

from .backend import BackendError, backend
from .runtime_context import request_id_context, user_message_context


async def _execute(call: Callable[[], Awaitable[Any]]) -> dict[str, Any]:
    try:
        return {"ok": True, "data": await call()}
    except BackendError as exc:
        return {
            "ok": False,
            "error": str(exc),
            "statusCode": exc.status_code,
        }


def _explicitly_requested(*keywords: str) -> bool:
    message = " ".join(user_message_context.get().lower().split())
    negated_action = re.search(
        r"\b(?:do not|don't|dont|never)\s+"
        r"(?:launch|start|book|reserve|confirm|authorize|proceed|reroute|re-route|"
        r"divert|change|swap|cancel|top[- ]?up|add|recharge|complete|finish|pay|run|simulate)\b",
        message,
    )
    read_only_phrases = (
        "not now",
        "without booking",
        "without reserving",
        "no booking",
        "no reservation",
        "read only",
        "read-only",
        "recommend only",
        "recommend-only",
        "preview only",
        "preview-only",
        "plan only",
        "plan-only",
    )
    if negated_action or any(phrase in message for phrase in read_only_phrases):
        return False
    if message in {"yes", "yes please", "confirm", "confirmed", "proceed", "do it"}:
        return True
    return any(keyword in message for keyword in keywords)


def _confirmation_required(action: str) -> dict[str, Any]:
    return {
        "ok": False,
        "confirmationRequired": True,
        "error": f"Ask the user to explicitly confirm {action} before continuing.",
    }


def _idempotency_key(action: str, resource_id: int = 0) -> str:
    request_id = request_id_context.get() or "request"
    return f"agent-{action}-{request_id}-{resource_id}"[:80]


async def get_vehicle_status(vehicle_id: int = 0) -> dict[str, Any]:
    """Get authenticated vehicle telemetry and connectivity.

    Args:
        vehicle_id: Vehicle ID to inspect. Use 0 to list all vehicles.
    """
    path = f"/api/ev/vehicles/{vehicle_id}" if vehicle_id > 0 else "/api/ev/vehicles"
    return await _execute(lambda: backend.request("GET", path))


async def find_chargers(
    query: str = "",
    latitude: float | None = None,
    longitude: float | None = None,
    radius_km: float = 25,
    connector_type: str = "",
    available_only: bool = True,
    max_price_per_kwh: float | None = None,
) -> dict[str, Any]:
    """Find charging stations by text, coordinates, compatibility, and price.

    Args:
        query: City, area, station name, or address text.
        latitude: Optional current latitude.
        longitude: Optional current longitude.
        radius_km: Search radius in kilometres.
        connector_type: Optional connector such as CCS2 or TYPE_2.
        available_only: Whether to return only currently available stations.
        max_price_per_kwh: Optional maximum INR price per kWh.
    """
    return await _execute(
        lambda: backend.request(
            "GET",
            "/api/stations/search",
            params={
                "query": query,
                "lat": latitude,
                "lng": longitude,
                "radius": radius_km,
                "connectorType": connector_type,
                "availableOnly": available_only,
                "maxPricePerKwh": max_price_per_kwh,
            },
            authenticated=False,
        )
    )


async def plan_trip(
    origin: str,
    destination: str,
    current_battery_percent: float,
    vehicle_id: int = 0,
    reserve_battery_percent: float = 15,
    destination_distance_km: float | None = None,
    trip_purpose: str = "GENERAL",
) -> dict[str, Any]:
    """Plan an EV route without creating a booking.

    Args:
        origin: Human-readable starting location.
        destination: Human-readable destination.
        current_battery_percent: Current battery percentage from the user or vehicle.
        vehicle_id: Authenticated vehicle ID, or 0 when unavailable.
        reserve_battery_percent: Desired battery reserve on arrival.
        destination_distance_km: Optional known trip distance.
        trip_purpose: GENERAL, MALL_VISIT, REST_STOP, COMMUTE, or DESTINATION_CHARGING.
    """
    body: dict[str, Any] = {
        "origin": origin,
        "destination": destination,
        "currentBatteryPercent": current_battery_percent,
        "reserveBatteryPercent": reserve_battery_percent,
        "tripPurpose": trip_purpose,
    }
    if vehicle_id > 0:
        body["vehicleId"] = vehicle_id
    if destination_distance_km is not None:
        body["destinationDistanceKm"] = destination_distance_km
    return await _execute(
        lambda: backend.request("POST", "/api/routing/plan", json=body)
    )


async def preview_autopilot_trip(
    vehicle_id: int,
    origin: str,
    destination: str,
    current_battery_percent: float,
    minimum_arrival_battery_percent: float = 15,
    maximum_charging_budget: float = 1000,
    optimize_for: str = "TIME",
    autonomy_mode: str = "ASK_BEFORE_ACTIONS",
    trip_purpose: str = "GENERAL",
    goal: str = "",
    arrival_deadline: str = "",
) -> dict[str, Any]:
    """Create a read-only Autopilot proposal without booking or payment.

    Args:
        vehicle_id: Vehicle to use for the proposed journey.
        origin: Starting location.
        destination: Destination location.
        current_battery_percent: Current vehicle battery percentage.
        minimum_arrival_battery_percent: Required battery at destination.
        maximum_charging_budget: Maximum allowed charging budget in INR.
        optimize_for: TIME, COST, or BALANCED.
        autonomy_mode: RECOMMEND_ONLY, ASK_BEFORE_ACTIONS, or FULL_AUTOPILOT.
        trip_purpose: GENERAL, MALL_VISIT, REST_STOP, COMMUTE, or DESTINATION_CHARGING.
        goal: Optional user journey goal.
        arrival_deadline: Optional local arrival time such as 18:00.
    """
    body: dict[str, Any] = {
        "vehicleId": vehicle_id,
        "origin": origin,
        "destination": destination,
        "currentBatteryPercent": current_battery_percent,
        "minimumArrivalBatteryPercent": minimum_arrival_battery_percent,
        "maximumChargingBudget": maximum_charging_budget,
        "optimizeFor": optimize_for,
        "autonomyMode": autonomy_mode,
        "tripPurpose": trip_purpose,
        "idempotencyKey": _idempotency_key("preview", vehicle_id),
    }
    if goal:
        body["goal"] = goal
    if arrival_deadline:
        body["arrivalDeadline"] = arrival_deadline
    return await _execute(
        lambda: backend.request("POST", "/api/ev/autopilot/trips/preview", json=body)
    )


async def book_charger(
    station_id: int,
    duration_minutes: int = 60,
    vehicle_id: int = 0,
    start_time: str = "",
) -> dict[str, Any]:
    """Book a charger only after the user explicitly asks to book or confirms.

    Args:
        station_id: Charging station ID selected by the user.
        duration_minutes: Charging duration from 15 to 720 minutes.
        vehicle_id: Optional vehicle ID.
        start_time: Optional local ISO-8601 start time.
    """
    if not _explicitly_requested("book", "reserve", "confirm booking"):
        return _confirmation_required("the charger booking")
    body: dict[str, Any] = {
        "stationId": station_id,
        "durationMinutes": duration_minutes,
        "idempotencyKey": _idempotency_key("booking", station_id),
    }
    if vehicle_id > 0:
        body["vehicleId"] = vehicle_id
    if start_time:
        body["startTime"] = start_time
    return await _execute(
        lambda: backend.request("POST", "/api/ev/bookings", json=body)
    )


async def launch_autopilot_trip(
    vehicle_id: int,
    origin: str,
    destination: str,
    current_battery_percent: float,
    minimum_arrival_battery_percent: float = 15,
    maximum_charging_budget: float = 1000,
    optimize_for: str = "TIME",
    autonomy_mode: str = "ASK_BEFORE_ACTIONS",
    trip_purpose: str = "GENERAL",
    goal: str = "",
    arrival_deadline: str = "",
) -> dict[str, Any]:
    """Plan and reserve an Autopilot trip after explicit user authorization.

    Args:
        vehicle_id: Vehicle to use for the journey.
        origin: Starting location.
        destination: Destination location.
        current_battery_percent: Current vehicle battery percentage.
        minimum_arrival_battery_percent: Required battery at destination.
        maximum_charging_budget: Maximum allowed charging budget in INR.
        optimize_for: TIME, COST, or BALANCED.
        autonomy_mode: RECOMMEND_ONLY, ASK_BEFORE_ACTIONS, or FULL_AUTOPILOT.
        trip_purpose: GENERAL, MALL_VISIT, REST_STOP, COMMUTE, or DESTINATION_CHARGING.
        goal: Optional user journey goal.
        arrival_deadline: Optional ISO-8601 arrival deadline.
    """
    if not _explicitly_requested(
        "launch autopilot",
        "start autopilot",
        "reserve trip",
        "book trip",
        "explicitly confirm launching this autopilot trip",
        "confirm tentative bookings",
        "authorize full autopilot",
    ):
        return _confirmation_required("launching and reserving the Autopilot trip")
    body: dict[str, Any] = {
        "vehicleId": vehicle_id,
        "origin": origin,
        "destination": destination,
        "currentBatteryPercent": current_battery_percent,
        "minimumArrivalBatteryPercent": minimum_arrival_battery_percent,
        "maximumChargingBudget": maximum_charging_budget,
        "optimizeFor": optimize_for,
        "autonomyMode": autonomy_mode,
        "tripPurpose": trip_purpose,
        "idempotencyKey": _idempotency_key("autopilot", vehicle_id),
    }
    if goal:
        body["goal"] = goal
    if arrival_deadline:
        body["arrivalDeadline"] = arrival_deadline
    return await _execute(
        lambda: backend.request("POST", "/api/ev/autopilot/trips", json=body)
    )


async def get_current_autopilot_trip() -> dict[str, Any]:
    """Get the authenticated user's current Autopilot trip and action state."""
    return await _execute(
        lambda: backend.request("GET", "/api/ev/autopilot/trips/current")
    )


async def start_autopilot_monitoring(
    trip_id: int, battery_drop_percent: float = 0
) -> dict[str, Any]:
    """Start live journey monitoring after the driver explicitly starts the trip.

    Args:
        trip_id: Existing authenticated Autopilot trip ID.
        battery_drop_percent: Optional demo telemetry battery drop.
    """
    if not _explicitly_requested(
        "start journey", "start trip", "begin journey", "begin trip"
    ):
        return _confirmation_required("starting journey monitoring")
    body = (
        {"batteryDropPercent": battery_drop_percent}
        if battery_drop_percent > 0
        else {}
    )
    return await _execute(
        lambda: backend.request(
            "POST", f"/api/ev/autopilot/trips/{trip_id}/start", json=body
        )
    )


async def handle_charger_unavailable(trip_id: int) -> dict[str, Any]:
    """Recover from a charger-unavailable event by cancelling and rebooking.

    Use this for an explicit CHARGER_UNAVAILABLE event, or after the user asks
    to run the failure-recovery demo. In Ask Before Actions mode, first explain
    the proposed recovery and obtain confirmation. Full Autopilot may execute
    the event inside the limits already authorized for the trip.

    Args:
        trip_id: Existing authenticated Autopilot trip ID.
    """
    if not _explicitly_requested(
        "charger_unavailable",
        "charger unavailable",
        "station unavailable",
        "station offline",
        "charger offline",
        "simulate fault",
        "confirm reroute",
    ):
        return _confirmation_required(
            "cancelling the unavailable reservation and booking a replacement"
        )
    return await _execute(
        lambda: backend.request(
            "POST", f"/api/ev/autopilot/trips/{trip_id}/simulate-fault", json={}
        )
    )


async def complete_autopilot_charging(trip_id: int) -> dict[str, Any]:
    """Complete the active stop and run its approved wallet AutoPay.

    Args:
        trip_id: Existing authenticated Autopilot trip ID.
    """
    if not _explicitly_requested(
        "complete charging", "finish charging", "run autopay", "pay charging"
    ):
        return _confirmation_required("completing charging and running AutoPay")
    return await _execute(
        lambda: backend.request(
            "POST",
            f"/api/ev/autopilot/trips/{trip_id}/complete-charging",
            json={},
        )
    )


async def get_autopilot_stop_alternatives(
    trip_id: int, stop_id: int
) -> dict[str, Any]:
    """Get compatible, timing-scored alternatives for one planned stop.

    Args:
        trip_id: Existing authenticated Autopilot trip ID.
        stop_id: Planned stop ID whose alternatives should be compared.
    """
    return await _execute(
        lambda: backend.request(
            "GET", f"/api/agent/plans/{trip_id}/legs/{stop_id}/alternatives"
        )
    )


async def swap_autopilot_stop(
    trip_id: int, stop_id: int, station_id: int
) -> dict[str, Any]:
    """Swap a planned stop and recalculate downstream timing after confirmation.

    Args:
        trip_id: Existing authenticated Autopilot trip ID.
        stop_id: Planned stop ID to replace.
        station_id: Compatible alternative station selected by the driver.
    """
    if not _explicitly_requested("swap", "change stop", "use alternative"):
        return _confirmation_required("swapping the planned charging stop")
    return await _execute(
        lambda: backend.request(
            "POST",
            f"/api/agent/plans/{trip_id}/legs/{stop_id}/swap",
            json={"stationId": station_id},
        )
    )


async def simulate_autopilot_delay(
    trip_id: int, delay_minutes: int = 30
) -> dict[str, Any]:
    """Run the demo delay scenario and request a fresh trip plan.

    Args:
        trip_id: Existing authenticated Autopilot trip ID.
        delay_minutes: Simulated delay in minutes.
    """
    if not _explicitly_requested("simulate delay", "delay trip", "replan delay"):
        return _confirmation_required("simulating a delay and replanning the trip")
    return await _execute(
        lambda: backend.request(
            "POST",
            f"/api/agent/plans/{trip_id}/simulate-delay",
            json={"delayMinutes": delay_minutes},
        )
    )


async def get_autopilot_trip_summary(trip_id: int) -> dict[str, Any]:
    """Get the shareable distance, charging, cost, and timing trip summary.

    Args:
        trip_id: Existing authenticated Autopilot trip ID.
    """
    return await _execute(
        lambda: backend.request("GET", f"/api/agent/trips/{trip_id}/summary")
    )


async def reroute(booking_id: int, alternative_station_id: int) -> dict[str, Any]:
    """Divert a booking to an alternative station after explicit confirmation.

    Args:
        booking_id: Existing booking ID affected by the diversion.
        alternative_station_id: Alternative station chosen by the user.
    """
    if not _explicitly_requested("reroute", "re-route", "divert", "change station"):
        return _confirmation_required("the route diversion")
    return await _execute(
        lambda: backend.request(
            "POST",
            f"/api/routing/divert/{booking_id}",
            json={"alternativeStationId": alternative_station_id},
        )
    )


async def cancel_booking(booking_id: int) -> dict[str, Any]:
    """Cancel a charging reservation after explicit user confirmation.

    Args:
        booking_id: Existing authenticated booking ID to cancel.
    """
    if not _explicitly_requested("cancel booking", "cancel reservation"):
        return _confirmation_required("cancelling the charger reservation")
    return await _execute(
        lambda: backend.request("POST", f"/api/ev/bookings/{booking_id}/cancel")
    )


async def get_wallet_status() -> dict[str, Any]:
    """Get the authenticated user's Vidyut wallet and vehicle wallet balances."""

    async def fetch() -> dict[str, Any]:
        wallet = await backend.request("GET", "/api/ev/wallet")
        vehicle_wallets = await backend.request("GET", "/api/ev/wallet/vehicles")
        return {"wallet": wallet, "vehicleWallets": vehicle_wallets}

    return await _execute(fetch)


async def top_up_wallet(amount_inr: float, payment_method: str = "UPI") -> dict[str, Any]:
    """Top up the Vidyut wallet only after the user explicitly requests it.

    Args:
        amount_inr: Amount in Indian rupees, minimum 10.
        payment_method: Existing payment method label, such as UPI or CARD.
    """
    if not _explicitly_requested("top up", "top-up", "add money", "recharge wallet"):
        return _confirmation_required(f"a wallet top-up of INR {amount_inr:.2f}")
    return await _execute(
        lambda: backend.request(
            "POST",
            "/api/ev/wallet/topup",
            json={"amount": amount_inr, "paymentMethod": payment_method},
        )
    )
