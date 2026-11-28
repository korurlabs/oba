package com.korur.oba.connect.azure.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;
import com.korur.oba.connect.azure.dto.request.AzureConnectionRequest;
import com.korur.oba.connect.azure.dto.response.AzureConnectionResponse;
import com.korur.oba.connect.azure.dto.response.AzureConnectionSetupResponse;
import com.korur.oba.connect.azure.dto.response.AzureConnectionSubscriptionResponse;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.entity.AzureConnectionSubscription;

/**
 * Maps connection requests to entities and connections to their response DTOs.
 *
 * The subject is set by the entity when the row is first persisted and never changes, so no
 * mapping writes it. Status and check results belong to the check, and the organization and the
 * creator come from the checked request, never from a body.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AzureConnectionMapper {

    /**
     * Maps a create request to a new, PENDING connection.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lastVerifiedAt", ignore = true)
    @Mapping(target = "lastError", ignore = true)
    AzureConnection toEntity(AzureConnectionRequest request);

    /**
     * Replaces the name, tenant id and client id of a connection. A null client id clears it.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "createdById", ignore = true)
    @Mapping(target = "subject", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lastVerifiedAt", ignore = true)
    @Mapping(target = "lastError", ignore = true)
    void updateEntity(@MappingTarget AzureConnection connection, AzureConnectionRequest request);

    /**
     * Maps a connection with its subscriptions and the deployment's issuer URL to its response.
     *
     * @param connection    the connection
     * @param subscriptions the subscriptions of its last successful check
     * @param issuerUrl     the issuer URL, null when no issuer is configured
     * @return the response
     */
    default AzureConnectionResponse toResponse(AzureConnection connection,
                                               List<AzureConnectionSubscription> subscriptions, String issuerUrl) {
        return toResponse(connection, subscriptions, toSetupResponse(issuerUrl, connection.getSubject()));
    }

    /**
     * Maps a connection, its subscriptions and its federated credential values to its response.
     */
    @Mapping(target = "id", source = "connection.id")
    @Mapping(target = "name", source = "connection.name")
    @Mapping(target = "tenantId", source = "connection.tenantId")
    @Mapping(target = "clientId", source = "connection.clientId")
    @Mapping(target = "status", source = "connection.status")
    @Mapping(target = "lastVerifiedAt", source = "connection.lastVerifiedAt")
    @Mapping(target = "lastError", source = "connection.lastError")
    @Mapping(target = "createdAt", source = "connection.createdAt")
    @Mapping(target = "subscriptions", source = "subscriptions")
    @Mapping(target = "setup", source = "setup")
    AzureConnectionResponse toResponse(AzureConnection connection, List<AzureConnectionSubscription> subscriptions,
                                       AzureConnectionSetupResponse setup);

    /**
     * Builds the values of the federated credential: the issuer, the connection's subject and the
     * audience Entra ID requires.
     */
    @Mapping(target = "audience", constant = AzureConnectionConstant.TOKEN_EXCHANGE_AUDIENCE)
    AzureConnectionSetupResponse toSetupResponse(String issuer, String subject);

    /**
     * Maps one subscription to its response.
     */
    AzureConnectionSubscriptionResponse toSubscriptionResponse(AzureConnectionSubscription subscription);
}
