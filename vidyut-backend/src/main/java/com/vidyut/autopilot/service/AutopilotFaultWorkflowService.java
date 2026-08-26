package com.vidyut.autopilot.service;

import com.vidyut.admin.entity.IncidentSeverity;
import com.vidyut.admin.entity.NetworkIncident;
import com.vidyut.admin.service.AdminControlService;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.entity.AutopilotStop;
import com.vidyut.autopilot.entity.AutopilotStopStatus;
import com.vidyut.autopilot.entity.AutopilotTripStatus;
import com.vidyut.autopilot.repository.AutopilotStopRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutopilotFaultWorkflowService {

    private static final int DEMO_DOWNTIME_MINUTES = 180;

    private final AutopilotService autopilotService;
    private final AutopilotStopRepository stopRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final AdminControlService adminControlService;

    /**
     * Runs the complete demo event as one transaction: hardware state, journey
     * recovery, Company work order, Host/Company notification and Admin incident.
     */
    @Transactional
    public AutopilotTripResponse simulateAndPropagate(Long tripId, Long userId) {
        AutopilotStop stop = stopRepository
                .findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED)
                .orElseThrow(() -> new BadRequestException("This trip has no active charger reservation"));
        ChargingStation station = stationRepository.findById(stop.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserved charging station not found"));
        ChargingConnector connector = matchingConnector(station, stop);

        String reason = "Autopilot demo detected a sudden charger heartbeat failure during journey #" + tripId;
        connector.setAvailable(false);
        connector.setMaintenanceMode(true);
        connector.setStatus(ChargerStatus.FAULT);
        connector.setFaultCode("AUTOPILOT_HEARTBEAT_LOSS");
        connector.setHealthScore(Math.min(connector.getHealthScore(), 25));
        connector.setCurrentPowerKw(0);
        connector.setLastHeartbeat(LocalDateTime.now());
        connectorRepository.save(connector);

        boolean stationHasHealthyConnector = station.getConnectors().stream().anyMatch(candidate ->
                !candidate.getId().equals(connector.getId())
                        && candidate.isAvailable()
                        && !candidate.isMaintenanceMode()
                        && candidate.getStatus() == ChargerStatus.ONLINE);
        if (!stationHasHealthyConnector) {
            station.setAvailability(StationAvailability.UNAVAILABLE);
            stationRepository.save(station);
        }

        AutopilotTripResponse recovered = autopilotService.simulateChargerFault(tripId, userId);
        Map<String, Object> impact = impact(recovered.getStatus());
        NetworkIncident incident = adminControlService.recordDetectedIncident(station, connector, IncidentSeverity.CRITICAL,
                reason, DEMO_DOWNTIME_MINUTES, impact);
        return autopilotService.recordOperationalPropagation(tripId, userId,
                incident.getIncidentCode(), incident.getMaintenanceTicketId());
    }

    private ChargingConnector matchingConnector(ChargingStation station, AutopilotStop stop) {
        return station.getConnectors().stream()
                .filter(connector -> connector.getType().name().equalsIgnoreCase(stop.getConnectorType()))
                .filter(ChargingConnector::isAvailable)
                .filter(connector -> !connector.isMaintenanceMode() && connector.getStatus() == ChargerStatus.ONLINE)
                .min(Comparator.comparingDouble(connector -> Math.abs(connector.getPowerKw() - stop.getPowerKw())))
                .orElseThrow(() -> new BadRequestException(
                        "The reserved station has no online " + stop.getConnectorType() + " connector to simulate"));
    }

    private Map<String, Object> impact(AutopilotTripStatus status) {
        int automatic = status == AutopilotTripStatus.REROUTED ? 1 : 0;
        int approval = status == AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED ? 1 : 0;
        int manual = status == AutopilotTripStatus.REPLAN_REQUIRED ? 1 : 0;
        return Map.of(
                "affectedJourneys", 1,
                "automaticReroutes", automatic,
                "driverApprovals", approval,
                "replanRequired", manual,
                "backupConnectorAvailable", false
        );
    }
}
