package com.korur.oba.apps.otak.outreach.constant.controller;

/**
 * Log message constants for outreach controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OutreachControllerLogConstant {

    private OutreachControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String OUTREACH_LIST_REQUEST = "Received request to list outreaches";
    public static final String OUTREACH_LIST_COMPLETED = "List outreaches request completed";

    public static final String OUTREACH_CREATE_REQUEST = "Received request to create outreach";
    public static final String OUTREACH_CREATE_COMPLETED = "Create outreach request completed";

    public static final String OUTREACH_GET_REQUEST = "Received request to get outreach";
    public static final String OUTREACH_GET_COMPLETED = "Get outreach request completed";

    public static final String OUTREACH_UPDATE_REQUEST = "Received request to update outreach";
    public static final String OUTREACH_UPDATE_COMPLETED = "Update outreach request completed";


    public static final String OUTREACH_SEND_REQUEST = "Received request to send outreach";
    public static final String OUTREACH_SEND_COMPLETED = "Send outreach request completed";
    public static final String OUTREACH_COMPLETE_REQUEST = "Received request to record outreach as done";
    public static final String OUTREACH_COMPLETE_COMPLETED = "Record outreach as done request completed";
    public static final String OUTREACH_DELETE_REQUEST = "Received request to delete outreach";
    public static final String OUTREACH_DELETE_COMPLETED = "Delete outreach request completed";
}
