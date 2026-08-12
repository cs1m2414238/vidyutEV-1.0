package com.vidyut.admin.config;

import com.vidyut.account.entity.*;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.admin.entity.AdminAccount;
import com.vidyut.admin.entity.AdminRole;
import com.vidyut.admin.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class AdminDevInitializer implements ApplicationRunner {
    private final AccountRepository accountRepository;
    private final AdminAccountRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${vidyut.admin.bootstrap.enabled:true}")
    private boolean enabled;
    @Value("${vidyut.admin.bootstrap.email:admin@vidyut.local}")
    private String email;
    @Value("${vidyut.admin.bootstrap.password:VidyutAdmin123!}")
    private String password;
    @Value("${vidyut.admin.bootstrap.name:Vidyut Super Admin}")
    private String name;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled)
            return;
        String normalized = email.trim().toLowerCase();
        Account account = accountRepository.findByEmailIgnoreCase(normalized)
                .orElseGet(() -> accountRepository.save(Account.builder()
                        .email(normalized).passwordHash(passwordEncoder.encode(password)).accountType(AccountType.ADMIN)
                        .roles(new LinkedHashSet<>(Set.of(AccountRole.ROLE_ADMIN))).enabled(true).emailVerified(true)
                        .build()));
        if (account.getAccountType() == AccountType.ADMIN && adminRepository.findById(account.getId()).isEmpty()) {
            adminRepository.save(
                    AdminAccount.builder().account(account).displayName(name).adminRole(AdminRole.SUPER_ADMIN).build());
        }
    }
}
