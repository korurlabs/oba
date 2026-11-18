package com.korur.oba.shrine.access.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.shrine.access.constant.service.AppAccessServiceLogConstant;
import com.korur.oba.shrine.access.constant.service.OrganizationGroupServiceLogConstant;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;

/**
 * Collects the apps, users and audit values that the group and grant writes of
 * {@link com.korur.oba.shrine.access.service.impl.OrganizationGroupServiceImpl} and
 * {@link com.korur.oba.shrine.access.service.impl.AppAccessServiceImpl} work with.
 *
 * Every method works only on the values it is given. Reads of the database and access decisions belong
 * to the access services, not here.
 */
@Component
public class AccessHelper {

    /**
     * Collects the apps of a group's grants, in app order and without duplicates.
     *
     * @param grants the grants of one group
     * @return the apps
     */
    public List<App> appsOf(List<AppGrant> grants) {
        EnumSet<App> apps = EnumSet.noneOf(App.class);
        grants.forEach(grant -> apps.add(grant.getApp()));
        return List.copyOf(apps);
    }

    /**
     * Collects the user ids of group member rows, in row order.
     *
     * @param memberRows the member rows
     * @return the user ids
     */
    public Set<UUID> userIdsOf(List<OrganizationGroupMember> memberRows) {
        return memberRows.stream()
                .map(memberRow -> memberRow.getUser().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Describes a group for an audit entry. The description may be null, so the map accepts null values,
     * and the caller may add further values to it.
     *
     * @param group the group
     * @return the audit values
     */
    public Map<String, Object> describeGroup(OrganizationGroup group) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(OrganizationGroupServiceLogConstant.AUDIT_FIELD_NAME, group.getName());
        values.put(OrganizationGroupServiceLogConstant.AUDIT_FIELD_DESCRIPTION, group.getDescription());
        return values;
    }

    /**
     * Describes a grant for an audit entry: the app and the id of its one target.
     *
     * @param grant the grant
     * @return the audit values
     */
    public Map<String, Object> describeGrant(AppGrant grant) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(AppAccessServiceLogConstant.AUDIT_FIELD_APP, grant.getApp().name());
        if (grant.getUser() != null) {
            values.put(AppAccessServiceLogConstant.AUDIT_FIELD_USER_ID, grant.getUser().getId());
        } else {
            values.put(AppAccessServiceLogConstant.AUDIT_FIELD_GROUP_ID, grant.getGroup().getId());
        }
        return values;
    }
}
