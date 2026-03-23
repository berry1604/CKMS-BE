package com.swp.ckms.entity;

import com.swp.ckms.enums.NotificationStatus;
import com.swp.ckms.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType eventType;

    @Column(nullable = false)
    private String templateName;

    @Column(nullable = false)
    private String recipientEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Builder.Default
    @Column(nullable = false)
    private Integer retryCount = 0;

    private LocalDateTime nextRetryAt;

    @Column(unique = true)
    private String deduplicationKey;

    private String errorMessage;

    private LocalDateTime sentAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
