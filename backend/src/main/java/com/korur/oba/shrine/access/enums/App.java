package com.korur.oba.shrine.access.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Locale;
import java.util.Optional;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * An app of the Oba platform that an organization grants to its members.
 *
 * The constant names are the values of the PostgreSQL type shrine_app_grant_app_enum, and each name is
 * the upper-cased package segment of the app under com.korur.oba.apps, so a request handler maps to its
 * app by its package. An app package without a constant has no app, and the access check refuses it, so a
 * new app stays closed until it has a constant and grants.
 *
 * The minimum role keeps an app out of reach of a member below it even when a grant names the member,
 * for example {@link #KAYA} for a {@link OrganizationMemberRole#GUEST}.
 */
public enum App {

    // Workflow management app: boards, tickets, chat and daily updates.
    KAIZEN(OrganizationMemberRole.GUEST),

    // Agents log and agent configuration of Claude Code sessions.
    KAYA(OrganizationMemberRole.MEMBER),

    // ISMS documentation pages; a guest can be given read access to a page.
    KALKAN(OrganizationMemberRole.GUEST),

    // Relations, their contacts and activities; no endpoint is open to a guest.
    OTAK(OrganizationMemberRole.MEMBER);

    private final OrganizationMemberRole minimumRole;

    App(OrganizationMemberRole minimumRole) {
        this.minimumRole = minimumRole;
    }

    /**
     * Returns the lowest organization role that can open this app.
     *
     * @return the minimum role
     */
    public OrganizationMemberRole getMinimumRole() {
        return minimumRole;
    }

    /**
     * Finds the app of a package segment under com.korur.oba.apps, such as "kaizen" for
     * com.korur.oba.apps.kaizen.board.
     *
     * @param segment the package segment after com.korur.oba.apps, in any case
     * @return the app whose name is the upper-cased segment, or empty when the segment is null, blank or no app
     */
    public static Optional<App> fromAppPackage(String segment) {
        if (segment == null || segment.isBlank()) {
            return Optional.empty();
        }
        String name = segment.toUpperCase(Locale.ROOT);
        for (App app : values()) {
            if (app.name().equals(name)) {
                return Optional.of(app);
            }
        }
        return Optional.empty();
    }
}
