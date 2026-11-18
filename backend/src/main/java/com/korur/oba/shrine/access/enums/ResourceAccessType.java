package com.korur.oba.shrine.access.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * The kind of entity whose access is checked when an endpoint names it only by type and id, such as the
 * parent of an attachment.
 *
 * Each type belongs to one app and carries the organization role a write needs. A read needs only the app's
 * {@link App#getMinimumRole()}, so a guest who may open the app can read what the type's own rules let the
 * guest see. A new constant does not compile until the switch of
 * {@link com.korur.oba.shrine.access.service.impl.ResourceAccessQueryServiceImpl} handles it, so no type is
 * reachable without access rules.
 */
public enum ResourceAccessType {

    // A Kaizen ticket, reached through the organization of its board.
    TICKET(App.KAIZEN, OrganizationMemberRole.MEMBER),

    // A Kaizen chat message, reached through its channel.
    CHAT_MESSAGE(App.KAIZEN, OrganizationMemberRole.MEMBER),

    // A Kalkan documentation page, reached through its root's level.
    KALKAN_PAGE(App.KALKAN, OrganizationMemberRole.MEMBER),

    // An Otak mail, reached through the outreach that sends it. Only an admin writes, as only an admin
    // creates or edits an outreach.
    MAIL(App.OTAK, OrganizationMemberRole.ADMIN),

    // An Otak relation; every member who may open Otak edits it, so every such member writes its files too.
    RELATION(App.OTAK, OrganizationMemberRole.MEMBER);

    private final App app;
    private final OrganizationMemberRole writeRole;

    /**
     * @param app       the app the entity belongs to
     * @param writeRole the lowest organization role that may write or delete
     * @throws IllegalArgumentException if the write role is below the app's minimum role, which would let a
     *                                  member write data of an app the member cannot open
     */
    ResourceAccessType(App app, OrganizationMemberRole writeRole) {
        if (!writeRole.isAtLeast(app.getMinimumRole())) {
            throw new IllegalArgumentException(
                    AccessExceptionLogConstant.RESOURCE_WRITE_ROLE_BELOW_APP_MINIMUM.formatted(writeRole, app));
        }
        this.app = app;
        this.writeRole = writeRole;
    }

    /**
     * Returns the app the entity belongs to.
     *
     * @return the app
     */
    public App getApp() {
        return app;
    }

    /**
     * Returns the lowest organization role the action needs on this type.
     *
     * @param action the action
     * @return the app's minimum role for {@link ResourceAccessAction#READ}, the write role otherwise
     */
    public OrganizationMemberRole minimumRole(ResourceAccessAction action) {
        return switch (action) {
            case READ -> app.getMinimumRole();
            case WRITE, DELETE -> writeRole;
        };
    }
}
