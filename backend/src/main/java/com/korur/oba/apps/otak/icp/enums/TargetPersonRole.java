package com.korur.oba.apps.otak.icp.enums;

/**
 * Role of the person an ICP is approached through. Mapped by name to the PostgreSQL
 * enum otak_target_person_role_enum, in its order.
 */
public enum TargetPersonRole {

    OWNER,

    CEO,

    CTO,

    CIO,

    CISO,

    IT_MANAGER,

    OPERATIONS,

    FINANCE,

    OTHER
}
