package com.korur.oba.connect.azure.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.dto.AzureResourceId;

class AzureResourceIdParserUnitTest {

    private static final UUID SUBSCRIPTION_ID = UUID.fromString("291bba3f-e0a5-47bc-a099-3bdcb2a50a05");

    @Test
    void parse_postgresqlFlexibleServer_givesTopLevelParts() {
        String resourceId = "/subscriptions/" + SUBSCRIPTION_ID
                + "/resourceGroups/rg-data/providers/Microsoft.DBforPostgreSQL/flexibleServers/pg-main";

        Optional<AzureResourceId> result = AzureResourceIdParser.parse(resourceId);

        assertThat(result).contains(new AzureResourceId(SUBSCRIPTION_ID, "rg-data", "Microsoft.DBforPostgreSQL",
                "flexibleServers", "pg-main", null));
    }

    @Test
    void parse_backupVaultInstance_givesChildTypeAndParent() {
        String resourceId = "/subscriptions/" + SUBSCRIPTION_ID + "/resourceGroups/rg-backup/providers/"
                + "Microsoft.DataProtection/backupVaults/vault-1/backupInstances/instance-1";

        Optional<AzureResourceId> result = AzureResourceIdParser.parse(resourceId);

        assertThat(result).contains(new AzureResourceId(SUBSCRIPTION_ID, "rg-backup", "Microsoft.DataProtection",
                "backupVaults/backupInstances", "instance-1", "vault-1"));
    }

    @Test
    void parse_keywordsInOtherCase_areAccepted() {
        String resourceId = "/SUBSCRIPTIONS/" + SUBSCRIPTION_ID
                + "/resourcegroups/rg/PROVIDERS/Microsoft.DBforPostgreSQL/flexibleServers/pg";

        assertThat(AzureResourceIdParser.parse(resourceId)).isPresent();
    }

    @Test
    void parse_subscriptionNotUuid_isEmpty() {
        String resourceId = "/subscriptions/not-a-uuid/resourceGroups/rg/providers/Microsoft.DBforPostgreSQL/"
                + "flexibleServers/pg";

        assertThat(AzureResourceIdParser.parse(resourceId)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "subscriptions/291bba3f-e0a5-47bc-a099-3bdcb2a50a05/resourceGroups/rg/providers/A.B/type/name",
            "/subscriptions/291bba3f-e0a5-47bc-a099-3bdcb2a50a05/resourceGroups/rg/providers/A.B/type/name/",
            "/subscriptions/291bba3f-e0a5-47bc-a099-3bdcb2a50a05/resourceGroups//providers/A.B/type/name",
            "/subscriptions/291bba3f-e0a5-47bc-a099-3bdcb2a50a05/groups/rg/providers/A.B/type/name",
            "/subscriptions/291bba3f-e0a5-47bc-a099-3bdcb2a50a05/resourceGroups/rg/vendors/A.B/type/name",
            "/subscriptions/291bba3f-e0a5-47bc-a099-3bdcb2a50a05/resourceGroups/rg/providers/A.B/type",
            "/subscriptions/291bba3f-e0a5-47bc-a099-3bdcb2a50a05/resourceGroups/rg/providers/A.B/t/n/c/n/g/n"
    })
    void parse_malformed_isEmpty(String resourceId) {
        assertThat(AzureResourceIdParser.parse(resourceId)).isEmpty();
    }
}
