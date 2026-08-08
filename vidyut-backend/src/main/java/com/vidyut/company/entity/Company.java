package com.vidyut.company.entity;

import com.vidyut.account.entity.Account;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(nullable = false, unique = true)
    private String companyName;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    private String supportEmail;

    private String supportPhone;

    private String gstNumber;

    private String kycDocumentUrl;

    private String businessAddress;

    private String website;

    @Column(name = "contact_name", nullable = false, length = 150)
    private String contactName;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.VERIFIED;

    @Builder.Default
    private boolean emailNotifications = true;

    @Builder.Default
    private boolean pushNotifications = true;

    @Builder.Default
    private String timezone = "Asia/Kolkata";

    private LocalDateTime verificationRequestedAt;

    private String emailVerificationCodeHash;

    private LocalDateTime emailVerificationExpiresAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
