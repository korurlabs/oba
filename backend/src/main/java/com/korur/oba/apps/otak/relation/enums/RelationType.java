package com.korur.oba.apps.otak.relation.enums;

/**
 * Kind of relation an organization keeps. A relation has exactly one type; a {@link #LEAD}
 * and a {@link #CUSTOMER} have a {@link PipelineStage}, a {@link #SUPPLIER} none. Mapped by name to the PostgreSQL enum otak_relation_type_enum.
 */
public enum RelationType {

    // A company the organization is trying to win.
    LEAD,

    // A company the organization buys from.
    SUPPLIER,

    // A company the organization sells to.
    CUSTOMER
}
