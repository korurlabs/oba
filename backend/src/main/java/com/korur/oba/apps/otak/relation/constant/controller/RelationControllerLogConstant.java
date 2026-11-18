package com.korur.oba.apps.otak.relation.constant.controller;

/**
 * Log message constants for relation controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RelationControllerLogConstant {

    private RelationControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RELATION_LIST_REQUEST = "Received request to list relations";
    public static final String RELATION_LIST_COMPLETED = "List relations request completed";

    public static final String RELATION_OWNER_CANDIDATE_LIST_REQUEST =
            "Received request to list relation owner candidates";
    public static final String RELATION_OWNER_CANDIDATE_LIST_COMPLETED = "List relation owner candidates request completed";

    public static final String RELATION_CREATE_REQUEST = "Received request to create relation";
    public static final String RELATION_CREATE_COMPLETED = "Create relation request completed";

    public static final String RELATION_GET_REQUEST = "Received request to get relation";
    public static final String RELATION_GET_COMPLETED = "Get relation request completed";

    public static final String RELATION_UPDATE_REQUEST = "Received request to update relation";
    public static final String RELATION_UPDATE_COMPLETED = "Update relation request completed";

    public static final String RELATION_STAGE_REQUEST = "Received request to change relation stage";
    public static final String RELATION_STAGE_COMPLETED = "Change relation stage request completed";

    public static final String RELATION_TYPE_REQUEST = "Received request to change relation type";
    public static final String RELATION_TYPE_COMPLETED = "Change relation type request completed";

    public static final String RELATION_DELETE_REQUEST = "Received request to delete relation";
    public static final String RELATION_DELETE_COMPLETED = "Delete relation request completed";
}
