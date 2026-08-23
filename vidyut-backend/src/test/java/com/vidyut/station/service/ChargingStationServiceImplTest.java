package com.vidyut.station.service;

import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationOwnershipType;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingStationServiceImplTest {

    @Mock
    private ChargingStationRepository stationRepository;

    @InjectMocks
    private ChargingStationServiceImpl service;

    @Test
    void companyControlledSiteDoesNotCreateAFakeHostRelationship() {
        when(stationRepository.save(any(ChargingStation.class))).thenAnswer(invocation -> {
            ChargingStation station = invocation.getArgument(0);
            station.setId(91L);
            return station;
        });
        StationCreateRequest request = StationCreateRequest.builder()
                .name("Tata Kanpur Company Hub")
                .address("NH 19, Kanpur")
                .city("Kanpur")
                .latitude(26.4499)
                .longitude(80.3319)
                .pricePerKwh(16.0)
                .connectorType(ConnectorType.CCS2)
                .powerKw(120)
                .siteOwnershipDocumentUrl("https://evidence.example/site-control.pdf")
                .electricityConnectionDocumentUrl("https://evidence.example/electricity.pdf")
                .build();

        var response = service.createCompanyStation(request, 44L, 12L, "TATA Power");

        ArgumentCaptor<ChargingStation> saved = ArgumentCaptor.forClass(ChargingStation.class);
        org.mockito.Mockito.verify(stationRepository).save(saved.capture());
        assertThat(saved.getValue().getHostUserId()).isNull();
        assertThat(saved.getValue().getPropertyOwnerAccountId()).isEqualTo(44L);
        assertThat(saved.getValue().getOperatorCompanyId()).isEqualTo(12L);
        assertThat(saved.getValue().getOwnershipType()).isEqualTo(StationOwnershipType.COMPANY_OWNED);
        assertThat(saved.getValue().getHostPartnershipId()).isNull();
        assertThat(response.isSiteEvidenceComplete()).isTrue();
    }
}
