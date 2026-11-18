package com.korur.oba.apps.kaizen.dailyupdate.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateNudgeResponse;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateNudge;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;

/**
 * Maps daily update nudges between their inputs, the entity and the response,
 * and names the sender as the nudge push shows it.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DailyUpdateNudgeMapper {

    /**
     * Builds a new nudge row.
     *
     * @param organizationId organization in which the recipient is nudged
     * @param recipientId    the nudged member
     * @param senderId       the member who nudges
     * @param day            the business day the nudge is for
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "recipientId", source = "recipientId")
    @Mapping(target = "senderId", source = "senderId")
    @Mapping(target = "day", source = "day")
    DailyUpdateNudge toEntity(UUID organizationId, UUID recipientId, UUID senderId, LocalDate day);

    /**
     * Maps a saved nudge to its response.
     */
    DailyUpdateNudgeResponse toResponse(DailyUpdateNudge nudge);

    /**
     * Names a user as the nudge push shows it: first and last name, either
     * alone when the other is missing, else the email, so the recipient always
     * learns who reminded them.
     *
     * @param user the sender
     * @return the display name
     */
    default String toSenderName(User user) {
        UserProfile profile = user.getProfile();
        String firstName = profile == null ? null : trimToNull(profile.getFirstName());
        String lastName = profile == null ? null : trimToNull(profile.getLastName());
        if (firstName != null && lastName != null) {
            return firstName + DailyUpdateConstant.SENDER_NAME_SEPARATOR + lastName;
        }
        if (firstName != null) {
            return firstName;
        }
        if (lastName != null) {
            return lastName;
        }
        return user.getEmail();
    }

    /**
     * Turns a blank text into null and trims any other text.
     */
    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
