package com.korur.oba.shrine.identity.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Named;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;

/**
 * Shared MapStruct utility that builds the full name of a user, so that every platform response labels a
 * user the same way.
 *
 * Reference this class in the uses clause of a mapper and qualify a mapping by the {@link Named} value
 * of the method. The methods are static and need no Spring bean, so a mapper that uses this class gets no
 * extra constructor argument and no dependency on the user mappers, which read app access.
 */
public final class UserFullNameMapper {

    private UserFullNameMapper() {
        throw new UnsupportedOperationException(CommonConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * Builds the full name of a user from the profile, or the email when the profile has no name.
     *
     * @param user the user, may be null
     * @return the full name, or null when the user is null
     */
    @Named("toUserFullName")
    public static String toUserFullName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getProfile() == null) {
            return fullNameOf(null, null, user.getEmail());
        }
        return fullNameOf(user.getProfile().getFirstName(), user.getProfile().getLastName(), user.getEmail());
    }

    /**
     * Builds the full name of an organization member, or the email when the member has no name.
     *
     * @param member the organization member, may be null
     * @return the full name, or null when the member is null
     */
    @Named("toMemberFullName")
    public static String toMemberFullName(OrganizationMemberResponse member) {
        if (member == null) {
            return null;
        }
        return fullNameOf(member.firstName(), member.lastName(), member.email());
    }

    /**
     * Joins the parts of a name that are not blank. A user who never filled in a profile still needs a
     * readable label in the admin lists, so the email stands in for an empty name.
     *
     * @param firstName the first name, may be null
     * @param lastName  the last name, may be null
     * @param email     the email, used when both names are blank
     * @return the full name, or the email
     */
    public static String fullNameOf(String firstName, String lastName, String email) {
        List<String> parts = new ArrayList<>();
        if (firstName != null && !firstName.isBlank()) {
            parts.add(firstName.strip());
        }
        if (lastName != null && !lastName.isBlank()) {
            parts.add(lastName.strip());
        }
        if (parts.isEmpty()) {
            return email;
        }
        return String.join(UserConstant.FULL_NAME_SEPARATOR, parts);
    }
}
