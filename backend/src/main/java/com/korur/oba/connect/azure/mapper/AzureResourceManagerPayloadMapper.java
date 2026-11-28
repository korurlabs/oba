package com.korur.oba.connect.azure.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.dto.api.AzureApiSubscription;

/**
 * Maps Azure Resource Manager's JSON payloads to the payloads the rest of the application reads,
 * so no caller depends on Azure's field names.
 */
@Mapper(componentModel = "spring")
public interface AzureResourceManagerPayloadMapper {

    /**
     * Maps one subscription of the subscription list.
     *
     * @param subscription the subscription as Azure returns it
     * @return the payload
     */
    @Mapping(target = "subscriptionId", source = "subscriptionId")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "state", source = "state")
    AzureSubscriptionPayload toSubscriptionPayload(AzureApiSubscription subscription);

    /**
     * Maps the subscriptions of one list page.
     *
     * @param subscriptions the subscriptions as Azure returns them
     * @return the payloads, in the same order
     */
    List<AzureSubscriptionPayload> toSubscriptionPayloads(List<AzureApiSubscription> subscriptions);
}
