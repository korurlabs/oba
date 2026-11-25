package com.korur.oba.apps.otak.relation.enums;

/**
 * How the organization came to know a relation. Mapped by name to the PostgreSQL enum
 * otak_relation_source_enum.
 */
public enum RelationSource {

    NETWORK,

    LINKEDIN,

    REFERRAL,

    INBOUND,

    PARTNER,

    EVENT,

    CAMPAIGN,

    RESEARCH,

    OTHER
}
