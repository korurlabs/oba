package com.korur.oba.shrine.access.enums;

/**
 * What a caller wants to do with an entity addressed by type and id, or with the files attached to it.
 *
 * The action picks the organization role of the check: {@link #READ} needs the app's minimum role,
 * {@link #WRITE} and {@link #DELETE} the write role of the {@link ResourceAccessType}.
 */
public enum ResourceAccessAction {

    // List or read the entity's data.
    READ,

    // Add to the entity's data, such as an upload of a file.
    WRITE,

    // Remove part of the entity's data, such as one attached file.
    DELETE
}
