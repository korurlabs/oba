package com.korur.oba.apps.kaya.repository.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the repository controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RepositoryControllerLogConstant {

    private RepositoryControllerLogConstant() {
    }

    public static final String REPOSITORY_LIST_REQUEST = "Received request to list the repositories of organization {}";
    public static final String REPOSITORY_LIST_COMPLETED = "Listed {} repositories of organization {}";
    public static final String REPOSITORY_UPDATE_REQUEST = "Received request to update repository {} of organization {}";
    public static final String REPOSITORY_UPDATE_COMPLETED = "Updated repository {} of organization {}";
}
