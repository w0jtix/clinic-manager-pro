package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "trusted_devices")
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private TrustedDeviceKind kind;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "previous_token_hash")
    private String previousTokenHash;

    @Column(name = "label")
    private String label;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "last_ip")
    private String lastIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "registered_by")
    private String registeredBy;

    @Column(name = "last_seen_username")
    private String lastSeenUsername;

    @Column(name = "suspected_copy_at")
    private LocalDateTime suspectedCopyAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "last_user_agent_hash", length = 64)
    private String lastUserAgentHash;

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}
