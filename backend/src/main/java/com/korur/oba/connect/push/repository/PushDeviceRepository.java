package com.korur.oba.connect.push.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.entity.PushDevice;

/**
 * Repository interface for {@link PushDevice} entity database operations.
 *
 * A token is registered once, enforced by the unique constraint push_devices_expo_push_token_uk.
 * The app registers on every launch and on every token change, which can arrive at the same time,
 * so the first registration of a token creates its row through {@link #insertIfAbsent} in one
 * statement instead of a read followed by an insert, and a concurrent registration never fails on
 * the constraint.
 */
@Repository
public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID>, JpaSpecificationExecutor<PushDevice> {

    /**
     * Finds the device of a token, whoever owns it.
     *
     * @param expoPushToken the Expo push token
     * @return the device, empty when the token is not registered
     */
    Optional<PushDevice> findByExpoPushToken(String expoPushToken);

    /**
     * Finds a device of one user, so another user's device reads as absent.
     *
     * @param id     ID of the device
     * @param userId ID of the owner
     * @return the device, empty when it does not exist or belongs to another user
     */
    Optional<PushDevice> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Finds every device of the given users.
     *
     * @param userIds IDs of the users
     * @return the devices, in no particular order
     */
    List<PushDevice> findAllByUserIdIn(Collection<UUID> userIds);

    /**
     * Creates the row of a token that is not registered yet and leaves an existing row unchanged; the
     * caller then loads the row and applies the registration to it.
     *
     * @param expoPushToken the Expo push token
     * @param platform      the platform name, cast to shrine_push_platform_enum
     * @param deviceName    the device name, or null
     * @param appVersion    the app version
     * @param locale        the push copy language
     * @param registeredAt  the registration time
     * @param userId        ID of the registering user
     * @return 1 when the row was created, 0 when the token was already registered
     */
    @Modifying
    @Query(value = """
            INSERT INTO shrine.push_devices
                (id, expo_push_token, platform, device_name, app_version, locale, last_registered_at, user_id,
                 created_at, updated_at)
            VALUES (gen_random_uuid(), :expoPushToken, CAST(:platform AS shrine_push_platform_enum),
                    CAST(:deviceName AS VARCHAR), :appVersion, :locale, :registeredAt, :userId, now(), now())
            ON CONFLICT ON CONSTRAINT push_devices_expo_push_token_uk DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("expoPushToken") String expoPushToken,
                       @Param("platform") String platform,
                       @Param("deviceName") String deviceName,
                       @Param("appVersion") String appVersion,
                       @Param("locale") String locale,
                       @Param("registeredAt") Instant registeredAt,
                       @Param("userId") UUID userId);

    /**
     * Removes the devices of tokens Expo no longer accepts.
     *
     * @param expoPushTokens the tokens to remove
     * @return the number of devices removed
     */
    @Modifying
    @Query("DELETE FROM PushDevice d WHERE d.expoPushToken IN :expoPushTokens")
    int deleteAllByExpoPushTokenIn(@Param("expoPushTokens") Collection<String> expoPushTokens);
}
