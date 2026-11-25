package com.korur.oba.apps.kazan.cyberscore.enums;

/**
 * Area of the organization's IT a check belongs to.
 *
 * The declaration order is the display order. Mapped to the PostgreSQL enum
 * kazan_check_category_enum, whose values must stay in the same order.
 */
public enum CheckCategory {

    // Network and office.
    NETWORK_OFFICE,

    // Cloudflare and DNS.
    CLOUDFLARE_DNS,

    // Microsoft 365.
    MICROSOFT_365,

    // SharePoint and OneDrive.
    SHAREPOINT_ONEDRIVE,

    // Passwords and role-based access control.
    PASSWORDS_RBAC,

    // Accounting software.
    ACCOUNTING_SOFTWARE,

    // Devices and laptops.
    DEVICES,

    // Storage, backups and retention.
    STORAGE_BACKUPS_RETENTION,

    // GDPR (AVG in Dutch).
    GDPR_AVG
}
