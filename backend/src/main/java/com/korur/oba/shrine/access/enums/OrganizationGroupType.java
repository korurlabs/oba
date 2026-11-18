package com.korur.oba.shrine.access.enums;

/**
 * The kind of an organization group. The constant names are the values of the PostgreSQL type
 * shrine_organization_group_type_enum.
 */
public enum OrganizationGroupType {

    // The one group of an organization that stands for all of its members. It has no member rows, and its
    // name, members and existence cannot be changed.
    EVERYONE,

    // A group that an owner or admin creates and fills with member rows.
    CUSTOM
}
