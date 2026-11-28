package com.korur.oba.connect.azure.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The parts of an Azure resource id, such as
 * /subscriptions/{id}/resourceGroups/{group}/providers/Microsoft.DBforPostgreSQL/flexibleServers/{name}
 * or, for a resource inside another,
 * /subscriptions/{id}/resourceGroups/{group}/providers/Microsoft.DataProtection/backupVaults/{vault}/backupInstances/{name}.
 *
 * @param subscriptionId the subscription that holds the resource
 * @param resourceGroup  the resource group
 * @param provider       the provider namespace, such as Microsoft.DataProtection
 * @param resourceType   the type below the namespace, such as flexibleServers or
 *                       backupVaults/backupInstances for a resource inside another
 * @param name           the name of the resource
 * @param parentName     the name of the resource that holds it, null for a top-level resource
 */
public record AzureResourceId(
        UUID subscriptionId,
        String resourceGroup,
        String provider,
        String resourceType,
        String name,
        String parentName
) {
}
