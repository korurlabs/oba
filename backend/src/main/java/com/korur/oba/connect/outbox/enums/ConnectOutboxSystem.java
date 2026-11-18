package com.korur.oba.connect.outbox.enums;

/**
 * Identifies the external system targeted by an outbox event.
 *
 * <p>Each value corresponds to a connect layer integration that receives
 * fire-and-forget operations via the transactional outbox pattern.</p>
 */
public enum ConnectOutboxSystem {

    NOTIFICATION,
    AZURE_BLOB_STORAGE,
    KUBERNETES,
    INTELLIGENCE
}
