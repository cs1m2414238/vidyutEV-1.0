# Connector-fault recovery: local verification, 31 August 2026

The latest requested architecture was retained. The attached older plan was treated as background, not as instructions to release reservations immediately or exclude a whole station.

## Fixes

- Company station/charger search now scopes the operational banner to the displayed results. Mathura shows its own connector alert, not an unrelated nationwide alert. Text contrast is fixed, and the Company Agent prompt names the exact displayed connector.
- Recovery checks reservations for each expected arrival window before offering a plan. It can select a free compatible sibling at the same station. Preparation and execution revalidate the selected exact connector; a changed reservation cannot silently change the approved connector.
- A retained reservation on failed hardware no longer consumes a healthy sibling's capacity. Legacy station-only bookings still consume capacity. The planner ignores only this journey's bookings that approval would replace; no booking is cancelled during this check.
- Concurrent authorized EV Agent requests for one incident share the in-flight operation within an agent process. Each caller must first pass backend ownership validation. This prevents duplicate long candidate transactions from two tabs.
- Map endpoints use the actual backend road geometry, replacing fabricated offsets from charging stations. Fault markers match connector IDs rather than station names. Removed downstream stops no longer count as active waypoints, and the fictitious straight-line failed detour is removed.
- The recovery panel shows captured coordinates and SoC. The post-recovery timeline starts at recovery/current position, with only remaining stops; cancelled stops remain in history. Pending UI text no longer claims a replacement was already applied.
- Recovery searches the existing remaining charging corridor before the direct highway corridor. It retains the planned onward chain when limiting the road matrix and prioritizes a healthy sibling at the failed station after a reachable next planned connector. Hard connector exclusion and full road/energy/booking/budget checks still apply.
- Failed searches report reachable-site counts and rejection reasons. The UI labels an older GPS/SoC evaluation as stale and hides approval until it is recalculated.
- Company booking responses now include the stored exact connector ID, end time and duration in minutes, matching EV-owner booking metadata.

## Tata journey investigation

The deployed Tata Nexon Delhi–Bhopal journey had a ₹1,000 budget and used Agra, Gwalior, Jhansi and Bina. Its own recorded discovery evidence placed those stations 114.8, 179.0, 232.6 and 121.5 km from the direct road corridor. The old recovery search used only a 100 km corridor around that direct road, incorrectly discarding the working charging chain and Agra's healthy `DEMO-AGRA-CCS2-02` sibling.

The vehicle had advanced to 79% SoC and 18.4091 km travelled, while the failed recovery panel still showed its earlier 85% origin snapshot. Both issues are addressed above; the user's budget and bookings were not changed during investigation.

Offline arithmetic using the recorded route distances supports the existing chain with Agra's healthy sibling: approximately 189.09 km to Agra, 17.37% arrival, 57% first departure target, ₹908.14 total remaining charging cost and 15.40% destination SoC. This is **not a fresh road or reservation quote**. A live replay was blocked by auto-review because sending the deployed vehicle's precise position to public OSRM requires explicit approval. No such request was sent by the blocked replay. Synthetic corridor regression tests validate the search fix independently.

An additional offline replay through the actual backend planner, using the recorded corridor distances and read-only booking copies with mocked road responses, selected `DEMO-AGRA-CCS2-02` and the onward Gwalior/Jhansi/Bina chain at ₹908.34. The optimizer's targets were 57%, 51%, 76%, 68%, with destination arrival 15.3956%. Its mocked durations are test data, not a live ETA. Neither offline check changed production bookings, vehicle, budget or route.

## Observed demo result

Two local Delhi → Bhopal rehearsals completed using seeded MG Windsor EV `DEMO-EV-004`, Ask Before Actions, 15% reserve and a ₹2,000 cap. This cap was supplied at trip creation and was never relaxed during recovery. These are synthetic stations and simulated vehicle progress, not a real vehicle safety certification.

| Evidence | Verified value |
|---|---|
| Vehicle progress before fault | 52.4138 km from Delhi |
| Captured position | 28.198199961484264, 77.3096639828944 |
| Current SoC | 72%, reduced from the original 92% |
| Failed reserved connector | `DEMO-MATHURA-CCS2-01` |
| Company operation | Exact connector ONLINE → FAULT; healthy sibling remains available |
| Tools | `get_recovery_context` → `get_safe_recovery_candidates` → `prepare_safe_reroute` |
| Prepared state | `AWAITING_APPROVAL` |
| Bridge | Vidyut Palwal District Demo Hub, `DIST-SOI-06-089-CCS2-01` |
| Verified road distance to bridge | 11.5978 km |
| Bridge arrival / minimum departure target | 67.5745% / 82% |
| Remaining route | 828.1193 km, 872 minutes, 5 charging stops |
| Remaining charging cost | ₹1,312.31 |
| Destination SoC | 15.6557% |
| Road source | OSRM reference engine; no estimated recovery matrix accepted |
| Selection source | `AGENT_POLICY`; not presented as Gemini |

The first rehearsal used the browser confirmation: Review → Cancel, verified unchanged bookings/navigation, then Review → Approve Reroute. The second also submitted two simultaneous agent requests and received the same proposal. Both completed successfully.

Assertions verified:

1. Company fault updates the exact hardware and appears on the active journey.
2. Fault detection and preparation leave every booking ID/status/time and the route geometry unchanged.
3. Recovery coordinates and SoC equal the current telemetry, not the original Delhi snapshot.
4. The offered and executed stops exclude the failed connector and preserve the reserve.
5. Approval reserves the exact replacement connector IDs and rebuilds navigation from the current position, allowing the router's small road snap.
6. Failed hardware remains FAULT after execution; repeated approval creates no extra bookings.
7. The browser displays the approved state, current-position map title and five active waypoints. Company Mathura search displays one affected connector and two stations.

The minimum-charge formula is validated with unrounded energy values and actual next-leg distances. The separate deterministic BMW test rejects a 160 km jump at 39% SoC and calculates a 49% target for its 22 km bridge / 120 km next-leg fixture. Those fixture distances are not claimed as Delhi–Bhopal measurements.

## Repeat the demo

Run each service from the repository root in a separate terminal. Dependencies must already be installed. The backend creates a disposable H2 database and takes roughly two minutes to seed the local network. Do not use this launcher for production.

```powershell
./scripts/start-recovery-demo.ps1 backend
./scripts/start-recovery-demo.ps1 agent
./scripts/start-recovery-demo.ps1 web
```

Open `http://127.0.0.1:4173` for EV Owner and `http://localhost:4173` for Company. These separate local origins allow separate logins in the same browser. Use their Quick Demo Access buttons. The seeded local password is `VidyutDemo@2026`.

```powershell
node scripts/verify-recovery-demo.mjs prepare
```

To rehearse Tata with the same budget and battery settings as the reported case, after ending the previous local rehearsal:

```powershell
node scripts/verify-recovery-demo.mjs prepare --vehicle DEMO-EV-001 --soc 85 --drop 6 --budget 1000
```

This still faults whichever exact connector is actually reserved; it does not force Agra or silently increase the budget.

This creates the trip, moves the simulated vehicle, finds and faults its actual reserved connector, and runs two concurrent recovery requests. It stops at approval. It refuses to replace an existing active journey. Open Vidyut Autopilot and show the coordinates, current SoC, bridge, minimum target, costs and approval boundary.

```powershell
# After cancelling the UI confirmation, or before approving:
node scripts/verify-recovery-demo.mjs pending

# Approve in the UI, then verify:
node scripts/verify-recovery-demo.mjs check

# Alternatively approve and verify through the real local API:
node scripts/verify-recovery-demo.mjs approve

# When finished, restore only this test connector and end only this test trip:
node scripts/verify-recovery-demo.mjs cleanup
```

The script is pinned to localhost and writes token-free evidence to `tmp/recovery-demo.json`. Current screenshots are `tmp/company-mathura-fault.png`, `tmp/recovery-approved.png`, and `tmp/recovery-map.png`. These local scratch artifacts are git-ignored.

## Validation and limits

Production rollout on 31 August 2026, in the requested backend → agent → frontend order:

- Backend: `vidyut-backend-00045-bx4`, ready with 100% public traffic.
- Agent: `vidyut-agent-00020-crb`, ready with 100% public traffic; `/health` reports configured authentication and the correct backend URL. This health check does not prove a successful Gemini generation.
- Frontend: Firebase Hosting updated at `https://vidyut-autopilot.web.app`.
- Both Cloud Run services retained their public URLs and the same runtime-configuration fingerprints, including environment/secrets. No `--set-env-vars` or `--set-secrets` was used.
- Existing traffic was pinned to old tagged revisions, so source deployment alone did not activate the code. Traffic was explicitly moved to each newly created revision after the build. Existing review tags were preserved; they still refer to their old review revisions.
- Authenticated reads after backend rollout confirmed the active Tata trip's budget, telemetry, stops, route geometry and every booking remained identical. Company booking responses now expose the stored connector/time metadata correctly.

- Backend: 134 tests across 37 suites, zero failures/errors.
- Python agent: 46 tests, all passing, including concurrent requests and authorization checks.
- Web: TypeScript/production build and lint passed. Vite still warns about the existing large application bundle.
- `git diff --check` passed.
- The local rehearsal disables Flyway and creates H2 tables; migration regression tests ran separately. The subsequent Cloud Run backend rollout started successfully against the existing production configuration and passed authenticated trip/booking reads. No fresh production reroute has been executed.
- Live Gemini selection was not verified. The sandbox first blocked the provider connection; an authorized network-enabled probe then received HTTP 429 from Gemini. The agent correctly used its labeled policy fallback. Gemini selection must be rehearsed again after provider capacity/rate limits permit it before claiming it in a video.
- Position is explicitly labeled demo simulation. Real journeys still require fresh GPS plus current vehicle SoC and can reject approval if telemetry changes or becomes stale.
- Recovery is triggered by the active journey UI or a request, not an always-on fleet worker. Duplicate-request sharing is per agent process; backend locking and transactional validation remain necessary across replicas.
- A safe route is not guaranteed if road routing, reserve, connector, booking, deadline or budget checks fail. Reservations remain intact until execution is permitted.
