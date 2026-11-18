package com.korur.oba.apps.kaya.identity.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Where a git email of a user came from: the user's sign-in, a recorder batch, or a user adding it
 * by hand. New emails come from sign-in or by hand; RECORDER marks emails a recorder reported before
 * ingest stopped adding emails, and stays so their history reads correctly.
 */
public enum UserGitEmailSource {
    SIGN_IN,
    RECORDER,
    MANUAL
}
