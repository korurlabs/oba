package com.korur.oba.apps.kaya.identity.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;

/**
 * Builds the name Kaya shows for a platform user: the profile's first and last name, or the sign-in
 * email while the profile holds no name yet, as for a user imported before its first sign-in.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class UserDisplayNameHelper {

    private static final String NAME_SEPARATOR = " ";

    private UserDisplayNameHelper() {
    }

    /**
     * Returns the display name of a user.
     *
     * @param user the platform user
     * @return the first and last name joined by a space, or the email when both are blank
     */
    public static String displayNameOf(User user) {
        UserProfile profile = user.getProfile();
        String firstName = profile == null ? "" : trimToEmpty(profile.getFirstName());
        String lastName = profile == null ? "" : trimToEmpty(profile.getLastName());
        String name = (firstName + NAME_SEPARATOR + lastName).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
