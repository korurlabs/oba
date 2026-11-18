package com.korur.oba.connect.push.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.enums.PushPlatform;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A phone that receives push notifications for one user, one row per Expo push token.
 *
 * The token is unique: registering a token that another user holds moves the row to the caller, so
 * a phone handed to someone else never receives the previous user's pushes. The row is removed with
 * the user.
 */
@Entity
@Table(name = "push_devices", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PushDevice extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Token Expo addresses the device by, in the form ExponentPushToken[...]. Anyone who holds it can
     * push to the device, so it is never returned by the API or written to a log.
     */
    @Column(name = "expo_push_token", length = 255, nullable = false, unique = true)
    private String expoPushToken;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "platform", nullable = false)
    private PushPlatform platform;

    /**
     * Name the user gave the phone, shown in the list of devices; null when the platform reports none.
     */
    @Column(name = "device_name", length = 100, nullable = true)
    private String deviceName;

    @Column(name = "app_version", length = 32, nullable = false)
    private String appVersion;

    /**
     * Language of the push copy for this device: en, nl or tr.
     */
    @Column(name = "locale", length = 2, nullable = false)
    private String locale;

    /**
     * Last time the app registered the token; the app registers on every launch, so an old value
     * marks a device that is no longer used.
     */
    @Column(name = "last_registered_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private Instant lastRegisteredAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
