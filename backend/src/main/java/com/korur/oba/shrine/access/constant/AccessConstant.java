package com.korur.oba.shrine.access.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.EnumSet;
import java.util.Set;

// 4. Internal
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Shared app access constants: the package of the apps, the name of the Everyone group, the roles whose
 * app access depends on grants and the field keys of error payloads.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AccessConstant {

    private AccessConstant() {
    }

    // Package of every app; the segment after it names the app of a request handler.
    public static final String APP_PACKAGE_PREFIX = SecurityConstant.OBA_PACKAGE_PREFIX + "apps.";

    // Name of the group that every organization has for all of its members.
    public static final String EVERYONE_GROUP_NAME = "Everyone";

    // Roles whose app access depends on grants. An OWNER holds every app through the role, so no grant write
    // can take an app from an OWNER.
    public static final Set<OrganizationMemberRole> GRANT_DEPENDENT_ROLES =
            Set.copyOf(EnumSet.complementOf(EnumSet.of(OrganizationMemberRole.OWNER)));

    // Longest group name, as the name column allows.
    public static final int GROUP_NAME_MAX_LENGTH = 100;
    // Longest group description, as the description column allows.
    public static final int GROUP_DESCRIPTION_MAX_LENGTH = 500;
    // Most users one request may add to a group, so one call cannot load an unbounded member list.
    public static final int MAX_GROUP_MEMBERS_PER_REQUEST = 100;

    // Action named in the AGT_VAL_001 refusal of an agent added to a custom group: an agent's apps are set
    // on the agent itself, so no group may carry access to it.
    public static final String AGENT_ACTION_GROUP_MEMBER_ADD = "GROUP_MEMBER_ADD";

    // Field key used in error payloads for the app.
    public static final String FIELD_APP = "App";
    // Field key used in error payloads for the group id.
    public static final String FIELD_GROUP_ID = "GroupId";
    // Field key used in error payloads for the group name.
    public static final String FIELD_GROUP_NAME = "GroupName";
    // Field key used in error payloads for the app grant id.
    public static final String FIELD_GRANT_ID = "GrantId";
    // Field key used in error payloads for the type of an entity addressed by type and id.
    public static final String FIELD_RESOURCE_TYPE = "ResourceType";
    // Field key used in error payloads for the id of an entity addressed by type and id.
    public static final String FIELD_RESOURCE_ID = "ResourceId";
    // Field key used in error payloads for the user id.
    public static final String FIELD_USER_ID = "UserId";
}
