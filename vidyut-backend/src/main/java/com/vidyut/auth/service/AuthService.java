package com.vidyut.auth.service;

import com.vidyut.account.entity.AccessMode;
import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.entity.EvUserProfile;
import com.vidyut.account.entity.HostProfile;
import com.vidyut.account.entity.HostVerificationStatus;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.auth.dto.AuthResponse;
import com.vidyut.auth.dto.LoginRequest;
import com.vidyut.auth.dto.RegisterCompanyRequest;
import com.vidyut.auth.dto.RegisterUserRequest;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.common.exception.UnauthorizedException;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.VerificationStatus;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.security.JwtService;
import com.vidyut.user.dto.UserResponse;
import com.vidyut.wallet.entity.EvWallet;
import com.vidyut.wallet.repository.EvWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final HostProfileRepository hostProfileRepository;
    private final CompanyRepository companyRepository;
    private final EvWalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (Exception exception) {
            throw new UnauthorizedException("Invalid email or password");
        }

        Account account = findByEmail(email);
        return buildAuthResponse(account, defaultMode(account));
    }

    @Transactional
    public AuthResponse registerUser(RegisterUserRequest request) {
        Account account = createAccount(request.getEmail(), request.getPassword(),
                AccountType.INDIVIDUAL, Set.of(AccountRole.ROLE_EV_USER));
        evUserProfileRepository.save(EvUserProfile.builder()
                .account(account)
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .build());
        walletRepository.save(EvWallet.builder()
                .userId(account.getId())
                .balance(0.0)
                .build());
        return buildAuthResponse(account, AccessMode.EV_USER);
    }

    @Transactional
    public AuthResponse registerHost(RegisterUserRequest request) {
        Account account = createAccount(request.getEmail(), request.getPassword(),
                AccountType.INDIVIDUAL, Set.of(AccountRole.ROLE_HOST));
        hostProfileRepository.save(HostProfile.builder()
                .account(account)
                .displayName(request.getFullName().trim())
                .phone(request.getPhone())
                .verified(false)
                .verificationStatus(HostVerificationStatus.PENDING)
                .build());
        return buildAuthResponse(account, AccessMode.HOST);
    }

    @Transactional
    public AuthResponse registerCompany(RegisterCompanyRequest request) {
        if (companyRepository.existsByCompanyName(request.getCompanyName())) {
            throw new DuplicateResourceException("Company name already exists: " + request.getCompanyName());
        }
        if (companyRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException("Registration number already exists: " + request.getRegistrationNumber());
        }

        Account account = createAccount(request.getAdminEmail(), request.getAdminPassword(),
                AccountType.COMPANY, Set.of(AccountRole.ROLE_COMPANY));
        companyRepository.save(Company.builder()
                .account(account)
                .companyName(request.getCompanyName().trim())
                .registrationNumber(request.getRegistrationNumber().trim())
                .supportEmail(account.getEmail())
                .supportPhone(request.getSupportPhone())
                .contactName(request.getAdminFullName().trim())
                .verificationStatus(VerificationStatus.PENDING)
                .active(true)
                .build());
        return buildAuthResponse(account, AccessMode.COMPANY);
    }

    public AuthResponse switchMode(String email, AccessMode requestedMode) {
        Account account = findByEmail(email);
        if (!account.allows(requestedMode)) {
            throw new ForbiddenException("This account is not allowed to use " + requestedMode + " mode");
        }
        return buildAuthResponse(account, requestedMode);
    }

    @Transactional
    public UserResponse applyForHost(String email, String displayName) {
        Account account = findByEmail(email);
        if (account.getAccountType() != AccountType.INDIVIDUAL
                || !account.getRoles().contains(AccountRole.ROLE_EV_USER)) {
            throw new ForbiddenException("Only an individual EV account can apply for host access");
        }
        if (account.getRoles().contains(AccountRole.ROLE_HOST)) {
            return mapToUserResponse(account, AccessMode.EV_USER);
        }

        HostProfile profile = hostProfileRepository.findById(account.getId())
                .orElseGet(() -> HostProfile.builder().account(account).build());
        profile.setDisplayName(displayName == null || displayName.isBlank()
                ? displayName(account, AccessMode.EV_USER)
                : displayName.trim());
        profile.setVerified(false);
        profile.setVerificationStatus(HostVerificationStatus.PENDING);
        hostProfileRepository.save(profile);
        return mapToUserResponse(account, AccessMode.EV_USER);
    }

    @Transactional
    public UserResponse approveHost(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        if (account.getAccountType() != AccountType.INDIVIDUAL) {
            throw new BadRequestException("Company and admin accounts cannot receive HOST mode");
        }
        HostProfile profile = hostProfileRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("No pending host application for account: " + accountId));
        profile.setVerified(true);
        profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
        hostProfileRepository.save(profile);
        account.getRoles().add(AccountRole.ROLE_HOST);
        accountRepository.save(account);
        return mapToUserResponse(account, defaultMode(account));
    }

    private Account createAccount(String rawEmail, String password, AccountType accountType,
                                  Set<AccountRole> roles) {
        String email = normalizeEmail(rawEmail);
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Account already exists with email: " + email);
        }
        return accountRepository.save(Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .accountType(accountType)
                .roles(new LinkedHashSet<>(roles))
                .enabled(true)
                .build());
    }

    private AuthResponse buildAuthResponse(Account account, AccessMode activeMode) {
        return AuthResponse.builder()
                .token(jwtService.generateModeToken(account, activeMode))
                .activeMode(activeMode)
                .user(mapToUserResponse(account, activeMode))
                .build();
    }

    public UserResponse mapToUserResponse(Account account, AccessMode activeMode) {
        Set<AccessMode> allowedModes = account.getRoles().stream()
                .map(AccountRole::mode)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return UserResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .fullName(displayName(account, activeMode))
                .phone(phone(account))
                .role(activeMode.role())
                .accountType(account.getAccountType())
                .roles(new LinkedHashSet<>(account.getRoles()))
                .allowedModes(allowedModes)
                .defaultMode(defaultMode(account))
                .enabled(account.isEnabled())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private String displayName(Account account, AccessMode mode) {
        if (account.getAccountType() == AccountType.COMPANY) {
            return companyRepository.findByAccount_Id(account.getId())
                    .map(Company::getCompanyName)
                    .orElse(account.getEmail());
        }
        if (mode == AccessMode.HOST) {
            return hostProfileRepository.findById(account.getId())
                    .map(HostProfile::getDisplayName)
                    .orElse(account.getEmail());
        }
        return evUserProfileRepository.findById(account.getId())
                .map(EvUserProfile::getFullName)
                .or(() -> hostProfileRepository.findById(account.getId()).map(HostProfile::getDisplayName))
                .orElse(account.getEmail());
    }

    private String phone(Account account) {
        if (account.getAccountType() == AccountType.COMPANY) {
            return companyRepository.findByAccount_Id(account.getId()).map(Company::getSupportPhone).orElse(null);
        }
        return evUserProfileRepository.findById(account.getId()).map(EvUserProfile::getPhone).orElse(null);
    }

    private AccessMode defaultMode(Account account) {
        if (account.allows(AccessMode.EV_USER)) return AccessMode.EV_USER;
        if (account.allows(AccessMode.HOST)) return AccessMode.HOST;
        if (account.allows(AccessMode.COMPANY)) return AccessMode.COMPANY;
        if (account.allows(AccessMode.ADMIN)) return AccessMode.ADMIN;
        throw new IllegalStateException("Account has no valid access mode");
    }

    private Account findByEmail(String email) {
        return accountRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
