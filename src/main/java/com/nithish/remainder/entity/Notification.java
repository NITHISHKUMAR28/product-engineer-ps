package com.nithish.remainder.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "notification",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_delivery_key",
                        columnNames = "delivery_key"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_key", nullable = false, unique = true)
    private String deliveryKey;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public Notification(String deliveryKey, String content, Instant receivedAt) {
        this.deliveryKey = deliveryKey;
        this.content = content;
        this.receivedAt = receivedAt;
    }
}