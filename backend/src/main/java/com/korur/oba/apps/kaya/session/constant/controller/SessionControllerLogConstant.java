package com.korur.oba.apps.kaya.session.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the session controllers.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SessionControllerLogConstant {

    private SessionControllerLogConstant() {
    }

    public static final String SESSION_LIST_REQUEST = "Received request to list the sessions of organization {}";
    public static final String SESSION_LIST_COMPLETED = "Listed {} sessions of organization {}";
    public static final String FILTERS_REQUEST = "Received request for the session filters of organization {}";
    public static final String FILTERS_COMPLETED = "Read the session filters of organization {}";
    public static final String SESSION_GET_REQUEST = "Received request to read session {} of organization {}";
    public static final String SESSION_GET_COMPLETED = "Read session {} of organization {}";
    public static final String EVENTS_REQUEST = "Received request for the events of session {} after seq {} in organization {}";
    public static final String EVENTS_COMPLETED = "Read {} events of session {} in organization {}";
    public static final String HANDOFFS_REQUEST = "Received request for the handoffs of session {} in organization {}";
    public static final String HANDOFFS_COMPLETED = "Read {} handoffs of session {} in organization {}";
    public static final String LINK_ADD_REQUEST = "Received request to add a {} link to session {} of organization {}";
    public static final String LINK_ADD_COMPLETED = "Added link {} to session {} of organization {}";
    public static final String LINK_REMOVE_REQUEST = "Received request to remove link {} of session {} in organization {}";
    public static final String LINK_REMOVE_COMPLETED = "Removed link {} of session {} in organization {}";
    public static final String SESSION_DELETE_REQUEST = "Received request to delete session {} of organization {}";
    public static final String SESSION_DELETE_COMPLETED = "Deleted session {} of organization {}";
    public static final String STREAM_REQUEST = "Received request from user {} to stream the sessions of organization {}";
}
