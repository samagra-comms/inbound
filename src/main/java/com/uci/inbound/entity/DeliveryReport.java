package com.uci.inbound.entity;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Getter
@Setter
@AllArgsConstructor
@Entity
@Builder
@NoArgsConstructor
@ToString
@Table(value = "delivery_report")
public class DeliveryReport {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(value = "external_id")
    private String externalId;
    @Column(value = "user_id")
    private String userId; // destAdd
    @Column(value = "bot_id")
    private String botId;
    @Column(value = "bot_name")
    private String botName;
    @Column(value = "fcm_token")
    private String fcmToken; // fcmDestAdd
    @Column(value = "message_state")
    private String messageState;
    @Column(value = "created_on")
    private LocalDateTime createdOn;
    @Column(value = "cass_id")
    private String cassId;
}
