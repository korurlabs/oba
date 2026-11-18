package com.korur.oba.connect.retry.enums;

/**
 * Identifies the external system targeted by a synchronous retry operation.
 *
 * <p>Each value corresponds to a connect layer integration that can
 * experience transient failures requiring immediate retry.</p>
 */
public enum ConnectRetrySystem {

    AZURE_BLOB_STORAGE,
    INTELLIGENCE,
    KUBERNETES,
    NOTIFICATION,
    EXPO_PUSH
}
