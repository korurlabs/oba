package com.korur.oba.apps.otak.relation.constant.controller;

/**
 * Response message constants for relation controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RelationControllerResponseConstant {

    private RelationControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_RELATION_LIST_RETRIEVED = "Relations retrieved successfully";
    public static final String RESPONSE_RELATION_OWNER_CANDIDATES_RETRIEVED =
            "Relation owner candidates retrieved successfully";
    public static final String RESPONSE_RELATION_CREATED = "Relation created successfully";
    public static final String RESPONSE_RELATION_RETRIEVED = "Relation retrieved successfully";
    public static final String RESPONSE_RELATION_UPDATED = "Relation updated successfully";
    public static final String RESPONSE_RELATION_STAGE_CHANGED = "Relation stage changed successfully";
    public static final String RESPONSE_RELATION_TYPE_CHANGED = "Relation type changed successfully";
    public static final String RESPONSE_RELATION_DELETED = "Relation deleted successfully";
}
