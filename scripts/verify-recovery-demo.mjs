// Local-only cross-role rehearsal. No cloud URLs, persisted tokens, or real payments.
// node scripts/verify-recovery-demo.mjs prepare|pending|approve|check|cleanup
import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';

const base = 'http://127.0.0.1:8080/api';
const { values, positionals } = parseArgs({ args: process.argv.slice(2), allowPositionals: true, options: {
  vehicle: { type: 'string', default: 'DEMO-EV-004' }, soc: { type: 'string', default: '92' },
  drop: { type: 'string', default: '20' }, budget: { type: 'string', default: '2000' },
} });
const phase = positionals[0] ?? 'prepare';
const startSoc = Number(values.soc), batteryDrop = Number(values.drop), budget = Number(values.budget);
assert(Number.isFinite(startSoc) && startSoc > 15 && startSoc <= 100, 'Starting SoC must be between 15 and 100');
assert(Number.isFinite(batteryDrop) && batteryDrop > 0 && batteryDrop <= 25 && startSoc-batteryDrop > 15, 'Demo drop must be 1–25% and preserve the reserve');
assert(Number.isFinite(budget) && budget > 0, 'Budget must be positive');
assert(['prepare', 'pending', 'approve', 'check', 'cleanup'].includes(phase), 'Unknown phase');
const stateFile = new URL('../tmp/recovery-demo.json', import.meta.url);
await mkdir(new URL('../tmp/', import.meta.url), { recursive: true });
async function api(path, token, body, method = body === undefined ? 'GET' : 'POST') {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(300000),
  });
  const result = await response.json();
  assert(response.ok && result.success, `${method} ${path}: ${result.message ?? JSON.stringify(result)}`);
  return result.data;
}
async function login(email) {
  return (await api('/auth/login', null, { email, password: 'VidyutDemo@2026' })).token;
}
const driver = await login('demo.driver@vidyut.com');
const company = await login('demo.company@vidyut.com');
let evidence;
const save = async () => writeFile(stateFile, JSON.stringify(evidence, null, 2));
const bookingState = bookings => bookings.map(b => ({ id: b.id, connectorId: b.connectorId, status: b.status,
  startTime: b.startTime, endTime: b.endTime })).sort((a,b) => a.id-b.id);
const tripPath = id => `/ev/autopilot/trips/${id}`;
async function setStatus(charger, status) {
  const live = (await api(`/company/chargers?q=${encodeURIComponent(charger.chargerCode)}`, company)).find(c => c.id === charger.id);
  assert(live, 'Company cannot access the exact planned connector');
  return api(`/company/chargers/${live.id}`, company, { ...live, status, maintenanceMode: status === 'MAINTENANCE',
    expectedStatus: live.status, impactApproved: true, syntheticDemo: false,
    faultReason: status === 'ONLINE' ? null : 'Local cross-role recovery rehearsal: connector communication failure' }, 'PUT');
}
if (phase === 'prepare') {
  assert.equal(await api('/ev/autopilot/trips/current', driver), null, 'End the existing local demo journey before preparing another');
  const vehicles = await api('/ev/vehicles', driver);
  const vehicle = vehicles.find(v => v.registrationNumber === values.vehicle);
  assert(vehicle, `Seeded demo vehicle ${values.vehicle} is required`);
  console.log('Planning Delhi → Bhopal with Ask Before Actions…');
  const original = await api('/ev/autopilot/trips', driver, { vehicleId: vehicle.id, origin: 'Delhi', destination: 'Bhopal',
    currentBatteryPercent: startSoc, minimumArrivalBatteryPercent: 15, maximumChargingBudget: budget,
    optimizeFor: 'TIME', autonomyMode: 'ASK_BEFORE_ACTIONS', idempotencyKey: `RECOVERY-QA-${Date.now()}` });
  evidence = { tripId: original.id, startedAt: new Date().toISOString(), original };
  await save();
  assert(original.stops.length, 'Journey must include charging stops');
  const current = await api(`${tripPath(original.id)}/start`, driver, { batteryDropPercent: batteryDrop });
  assert(current.telemetry.distanceTravelledKm > 1, 'Demo vehicle must move away from Delhi');
  assert(Math.abs(current.telemetry.batteryPercent - (startSoc-batteryDrop)) < 0.001);
  const planned = current.stops.find(s => s.status === 'RESERVED');
  const network = await api(`/company/chargers?q=${encodeURIComponent(planned.chargerCode)}`, company);
  const charger = network.find(c => c.id === planned.connectorId);
  assert(charger, 'Exact connector is not in the authenticated company network');
  evidence.current = current; evidence.failed = charger;
  evidence.bookingsBefore = bookingState(await api('/ev/bookings', driver));
  await save();
  await setStatus(charger, 'FAULT');
  let trip = await api(tripPath(original.id), driver);
  assert.equal(trip.recovery.state, 'INCIDENT_DETECTED');
  assert.equal(trip.recovery.failedConnectorId, planned.connectorId);
  assert.deepEqual(bookingState(await api('/ev/bookings', driver)), evidence.bookingsBefore);
  assert.deepEqual(trip.routeCoordinates, current.routeCoordinates);
  console.log(`Company faulted ${charger.chargerCode}; bookings and route unchanged. Running EV Agent…`);
  const [run, duplicate] = await Promise.all([1, 2].map(() =>
    api(`${tripPath(trip.id)}/recovery/run`, driver, { incidentId: trip.recovery.incidentId })));
  assert.equal(run.journey.recovery.planId, duplicate.journey.recovery.planId, 'Concurrent clients must share the same prepared proposal');
  evidence.concurrentRecoveryPassed = true;
  trip = run.journey;
  evidence.agentTools = run.tools; evidence.prepared = trip; await save();
  assert.equal(trip.recovery.state, 'AWAITING_APPROVAL', trip.recovery.reason);
  assert.deepEqual(bookingState(await api('/ev/bookings', driver)), evidence.bookingsBefore);
  assert.deepEqual(trip.routeCoordinates, current.routeCoordinates);
  assert.equal(trip.recovery.currentSoc, current.telemetry.batteryPercent);
  assert.equal(trip.recovery.currentLatitude, current.telemetry.latitude);
  assert.equal(trip.recovery.currentLongitude, current.telemetry.longitude);
  assert(trip.recovery.proposedStops.every(s => s.connectorId !== charger.id && s.arrivalBatteryPercent >= 15));
  evidence.preApprovalChecksPassed = true; await save();
  console.log(JSON.stringify({ tripId: trip.id, state: trip.recovery.state, tools: run.tools,
    provider: trip.recovery.agentProvider, capturedPosition: [trip.recovery.currentLatitude, trip.recovery.currentLongitude],
    currentSoc: trip.recovery.currentSoc, failed: charger.chargerCode,
    replacement: trip.recovery.proposedStops[0]?.chargerCode, targetSoc: trip.recovery.departureTargetSoc,
    remainingKm: trip.recovery.newRemainingDistanceKm, minutes: trip.recovery.newRemainingMinutes,
    cost: trip.recovery.remainingCost, roadEngine: trip.recovery.routeEngine }, null, 2));
  console.log('Prepared without booking mutations. Approve in the UI, then run check; or run approve.');
} else {
  evidence = JSON.parse(await readFile(stateFile, 'utf8'));
  let trip = await api(tripPath(evidence.tripId), driver);
  if (phase === 'pending') {
    assert.equal(trip.recovery.state, 'AWAITING_APPROVAL');
    assert.deepEqual(bookingState(await api('/ev/bookings', driver)), evidence.bookingsBefore);
    assert.deepEqual(trip.routeCoordinates, evidence.current.routeCoordinates);
    console.log('PASS: approval cancelled/untouched; all bookings and active navigation remain unchanged.');
  } else if (phase === 'cleanup') {
    if (evidence.failed) await setStatus(evidence.failed, evidence.failed.status);
    await api(`${tripPath(trip.id)}/end`, driver, { completed: false });
    console.log('Restored only this rehearsal connector and ended this rehearsal journey.');
  } else {
    const proposal = { incidentId: evidence.prepared.recovery.incidentId, planId: evidence.prepared.recovery.planId };
    if (phase === 'approve') trip = await api(`${tripPath(trip.id)}/approve-reroute`, driver, proposal);
    assert.equal(trip.recovery.state, 'EXECUTED', 'Approve Reroute in the driver UI first');
    const active = trip.stops.filter(s => s.status === 'RESERVED');
    assert(active.every(s => s.connectorId !== evidence.failed.id && s.arrivalBatteryPercent >= 15));
    assert(trip.estimatedArrivalBatteryPercent >= 15);
    const bookings = await api('/ev/bookings', driver);
    for (const stop of active) assert(bookings.some(b => b.id === stop.bookingId && b.connectorId === stop.connectorId && b.status === 'CONFIRMED'));
    assert.equal(trip.telemetry.latitude, evidence.current.telemetry.latitude);
    assert.equal(trip.telemetry.longitude, evidence.current.telemetry.longitude);
    const first = trip.routeCoordinates[0];
    assert(Math.abs(first[0] - trip.telemetry.longitude) < .006 && Math.abs(first[1] - trip.telemetry.latitude) < .006,
      'Rebuilt road route must start at the current vehicle, allowing router snapping');
    assert.notDeepEqual(trip.routeCoordinates, evidence.original.routeCoordinates);
    const fault = (await api(`/company/chargers?q=${encodeURIComponent(evidence.failed.chargerCode)}`, company)).find(c => c.id === evidence.failed.id);
    assert.equal(fault.status, 'FAULT');
    const repeated = await api(`${tripPath(trip.id)}/approve-reroute`, driver, proposal);
    assert.equal(repeated.recovery.planId, trip.recovery.planId);
    assert.deepEqual(bookingState(await api('/ev/bookings', driver)), bookingState(bookings));
    evidence.executed = trip; evidence.verifiedAt = new Date().toISOString(); evidence.executionChecksPassed = true; await save();
    console.log('PASS: exact connector fault → EV Agent → approval → exact reservations → current-position route; reserve maintained, fault excluded, repeated approval idempotent.');
  }
}
