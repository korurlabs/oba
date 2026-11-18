package com.korur.oba.apps.otak.relation.constant.service;

/**
 * Log message constants for relation service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RelationServiceLogConstant {

    private RelationServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RELATION_CREATED = "Relation created: id={}, organizationId={}, type={}, createdBy={}";
    public static final String RELATION_UPDATED = "Relation updated: id={}, organizationId={}";
    public static final String RELATION_STAGE_CHANGED = "Relation stage changed: id={}, from={}, to={}";
    public static final String RELATION_CLOSED_AS_CUSTOMER = "Relation closed as customer: id={}, from={}";
    public static final String RELATION_TYPE_CHANGED = "Relation type changed: id={}, from={}, to={}, changedBy={}";
    public static final String RELATION_DELETED = "Relation deleted: id={}, organizationId={}";
    public static final String LISTING_RELATIONS = "Listing relations: organizationId={}, request={}";
    public static final String LISTING_OWNER_CANDIDATES = "Listing relation owner candidates: organizationId={}";
    public static final String LISTING_PIPELINE = "Listing relations with a stage: organizationId={}";
    public static final String READING_RELATION = "Reading relation: id={}, organizationId={}";
}
