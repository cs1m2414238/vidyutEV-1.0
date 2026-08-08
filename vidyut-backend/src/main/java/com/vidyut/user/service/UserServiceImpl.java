package com.vidyut.user.service;

import com.vidyut.account.entity.AccessMode;
import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.entity.EvUserProfile;
import com.vidyut.account.entity.HostProfile;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.entity.Company;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.user.dto.UpdateUserRequest;
import com.vidyut.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final AccountRepository accountRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final HostProfileRepository hostProfileRepository;
    private final CompanyRepository companyRepository;

    @Override
    public UserResponse getUserById(Long id) {
        return map(accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id)));
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        return map(accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with email: " + email)));
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return accountRepository.findAll().stream().map(this::map).toList();
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
        if (account.getAccountType() == AccountType.COMPANY) {
            Company company = companyRepository.findByAccount_Id(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Company profile not found"));
            if (request.getFullName() != null) company.setContactName(request.getFullName());
            if (request.getPhone() != null) company.setSupportPhone(request.getPhone());
            companyRepository.save(company);
        } else {
            evUserProfileRepository.findById(id).ifPresent(profile -> {
                if (request.getFullName() != null) profile.setFullName(request.getFullName());
                if (request.getPhone() != null) profile.setPhone(request.getPhone());
                evUserProfileRepository.save(profile);
            });
            hostProfileRepository.findById(id).ifPresent(profile -> {
                if (request.getFullName() != null) profile.setDisplayName(request.getFullName());
                hostProfileRepository.save(profile);
            });
        }
        return map(account);
    }

    @Override
    public void deleteUser(Long id) {
        throw new UnsupportedOperationException("Account deletion requires the dedicated audited deletion workflow");
    }

    private UserResponse map(Account account) {
        AccessMode mode = defaultMode(account);
        String name;
        String phone = null;
        if (account.getAccountType() == AccountType.COMPANY) {
            Company company = companyRepository.findByAccount_Id(account.getId()).orElse(null);
            name = company == null ? account.getEmail() : company.getCompanyName();
            phone = company == null ? null : company.getSupportPhone();
        } else {
            EvUserProfile ev = evUserProfileRepository.findById(account.getId()).orElse(null);
            HostProfile host = hostProfileRepository.findById(account.getId()).orElse(null);
            name = ev != null ? ev.getFullName() : host != null ? host.getDisplayName() : account.getEmail();
            phone = ev == null ? null : ev.getPhone();
        }
        return UserResponse.builder()
                .id(account.getId()).email(account.getEmail()).fullName(name).phone(phone)
                .role(mode.role()).accountType(account.getAccountType())
                .roles(new LinkedHashSet<>(account.getRoles()))
                .allowedModes(account.getRoles().stream().map(AccountRole::mode)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))
                .defaultMode(mode).enabled(account.isEnabled()).createdAt(account.getCreatedAt()).build();
    }

    private AccessMode defaultMode(Account account) {
        if (account.allows(AccessMode.EV_USER)) return AccessMode.EV_USER;
        if (account.allows(AccessMode.HOST)) return AccessMode.HOST;
        if (account.allows(AccessMode.COMPANY)) return AccessMode.COMPANY;
        return AccessMode.ADMIN;
    }
}
