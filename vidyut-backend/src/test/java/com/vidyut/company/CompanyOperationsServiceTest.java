package com.vidyut.company;

import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.dto.ChargerRequest;
import com.vidyut.company.dto.EmployeeRequest;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.EmployeeRole;
import com.vidyut.company.entity.VerificationStatus;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.service.CompanyOperationsService;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ConnectorType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CompanyOperationsServiceTest {
    @Autowired private CompanyOperationsService operations;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CompanyRepository companyRepository;

    @Test
    void verifiedCompanyCanManageNetworkEmployeesAssistantAndReports() {
        Account account = saveCompanyAccount("ops@company.test", true, VerificationStatus.VERIFIED);
        var station = operations.createStation(account.getId(), stationRequest("Central Hub"));

        ChargerRequest chargerRequest = new ChargerRequest();
        chargerRequest.setStationId(station.getId());
        chargerRequest.setChargerCode("COMPANY-CCS2-01");
        chargerRequest.setConnectorType(ConnectorType.CCS2);
        chargerRequest.setPowerKw(120);
        chargerRequest.setStatus(ChargerStatus.ONLINE);
        chargerRequest.setFirmwareVersion("2.4.1");
        chargerRequest.setHealthScore(96);
        var charger = operations.createCharger(account.getId(), chargerRequest);

        EmployeeRequest employeeRequest = new EmployeeRequest();
        employeeRequest.setName("Network Operator");
        employeeRequest.setEmail("operator@company.test");
        employeeRequest.setRole(EmployeeRole.OPERATOR);
        employeeRequest.setActive(true);
        employeeRequest.setPermissions("stations:read,bookings:write");
        var employee = operations.createEmployee(account.getId(), employeeRequest);

        Map<String, Object> dashboard = operations.dashboard(account.getId());
        Map<String, Object> assistant = operations.askAssistant(account.getId(), "Which chargers need maintenance?");
        byte[] pdf = operations.exportReport(account.getId(), "ANALYTICS", "PDF");
        byte[] xlsx = operations.exportReport(account.getId(), "REVENUE", "XLSX");

        assertThat(charger.getStationId()).isEqualTo(station.getId());
        assertThat(employee.getCompanyId()).isNotNull();
        assertThat(dashboard).containsEntry("totalStations", 1).containsEntry("totalChargers", 2);
        assertThat(operations.getActivityLogs(account.getId())).isNotEmpty()
                .allMatch(log -> log.getActorAccountId().equals(account.getId()));
        assertThat(assistant.get("answer").toString()).contains("chargers need attention");
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        assertThat(new String(xlsx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
    }

    @Test
    void pendingOrUnverifiedCompanyCannotMutateOperations() {
        Account pending = saveCompanyAccount("pending@company.test", false, VerificationStatus.PENDING);
        assertThatThrownBy(() -> operations.createStation(pending.getId(), stationRequest("Blocked Hub")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("email must be verified");
    }

    @Test
    void companyCannotReadOrMutateAnotherCompanyStation() {
        Account owner = saveCompanyAccount("owner@company.test", true, VerificationStatus.VERIFIED);
        Account other = saveCompanyAccount("other@company.test", true, VerificationStatus.VERIFIED);
        var station = operations.createStation(owner.getId(), stationRequest("Owner Hub"));

        ChargerRequest request = new ChargerRequest();
        request.setStationId(station.getId());
        request.setChargerCode("CROSS-ACCESS-01");
        request.setConnectorType(ConnectorType.TYPE2);
        request.setPowerKw(22);
        request.setHealthScore(100);

        assertThatThrownBy(() -> operations.createCharger(other.getId(), request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("this company");
    }

    private Account saveCompanyAccount(String email, boolean emailVerified, VerificationStatus status) {
        Account account = accountRepository.save(Account.builder()
                .email(email)
                .passwordHash("test-hash")
                .accountType(AccountType.COMPANY)
                .roles(new HashSet<>(Set.of(AccountRole.ROLE_COMPANY)))
                .enabled(true)
                .emailVerified(emailVerified)
                .build());
        companyRepository.save(Company.builder()
                .account(account)
                .companyName("Company " + email)
                .registrationNumber("REG-" + email)
                .supportEmail(email)
                .contactName("Admin")
                .verificationStatus(status)
                .active(true)
                .build());
        return account;
    }

    private StationCreateRequest stationRequest(String name) {
        return StationCreateRequest.builder()
                .name(name)
                .address("1 Company Road")
                .city("Lucknow")
                .latitude(26.8467)
                .longitude(80.9462)
                .pricePerKwh(18.0)
                .connectorType(ConnectorType.CCS2)
                .powerKw(60)
                .amenities("Parking, Restroom")
                .workingHours("Open 24 hours")
                .build();
    }
}
