package com.korur.oba.shrine.access.enums;

/**
 * The kind of target of an app grant on the wire. It is not stored: a grant with a user is a
 * {@link #USER} grant, a grant with a group a {@link #GROUP} grant.
 */
public enum AppGrantTargetType {

    // The grant names one member of the organization.
    USER,

    // The grant names a group of the organization, the Everyone group included.
    GROUP
}
