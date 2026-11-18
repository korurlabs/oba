package com.korur.oba.apps.kazan.board.constant.service;

/**
 * Log message constants for board access service operations.
 * <p>
 * These constants provide consistent logging at the service layer,
 * tracking access control checks and their outcomes.
 */
public final class BoardAccessServiceLogConstant {

    private BoardAccessServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String BOARD_MEMBER_NOT_FOUND = "Board member not found during access check: boardId={}, userId={}";
    public static final String INSUFFICIENT_PERMISSIONS = "Insufficient board permissions: boardId={}, userId={}, requiredMinimumRole={}";
}
