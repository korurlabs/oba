package com.korur.oba.connect.azure.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.entity.AzureConnectionSubscription;

/**
 * Repository interface for {@link AzureConnectionSubscription} entity database operations.
 *
 * The list of a connection is only ever replaced as a whole: {@link #deleteByConnectionId} and
 * the inserts of the new list run in the transaction that holds the connection's row lock.
 */
@Repository
public interface AzureConnectionSubscriptionRepository extends JpaRepository<AzureConnectionSubscription, UUID>,
        JpaSpecificationExecutor<AzureConnectionSubscription> {

    /**
     * Lists the subscriptions of one connection in display name order.
     *
     * @param connectionId ID of the connection
     * @return the connection's subscriptions
     */
    @Query("SELECT s FROM AzureConnectionSubscription s WHERE s.connection.id = :connectionId "
            + "ORDER BY s.displayName, s.subscriptionId")
    List<AzureConnectionSubscription> findByConnectionId(@Param("connectionId") UUID connectionId);

    /**
     * Lists the subscriptions of several connections in one query, so a list of connections does
     * not read each connection's subscriptions separately.
     *
     * @param connectionIds IDs of the connections, not empty
     * @return the subscriptions, in connection and display name order
     */
    @Query("SELECT s FROM AzureConnectionSubscription s WHERE s.connection.id IN :connectionIds "
            + "ORDER BY s.connection.id, s.displayName, s.subscriptionId")
    List<AzureConnectionSubscription> findByConnectionIdIn(@Param("connectionIds") Collection<UUID> connectionIds);

    /**
     * Tells whether the identity of a connection could read the subscription at the last
     * successful check.
     *
     * @param connectionId   ID of the connection
     * @param subscriptionId the Azure subscription id
     * @return true when the subscription is listed for the connection
     */
    @Query("SELECT COUNT(s) > 0 FROM AzureConnectionSubscription s "
            + "WHERE s.connection.id = :connectionId AND s.subscriptionId = :subscriptionId")
    boolean existsByConnectionIdAndSubscriptionId(@Param("connectionId") UUID connectionId,
                                                  @Param("subscriptionId") UUID subscriptionId);

    /**
     * Removes every subscription of a connection, before a successful check writes the new list or
     * when the connection's identity changes.
     *
     * @param connectionId ID of the connection
     * @return the number of subscriptions removed
     */
    @Modifying
    @Query("DELETE FROM AzureConnectionSubscription s WHERE s.connection.id = :connectionId")
    int deleteByConnectionId(@Param("connectionId") UUID connectionId);
}
