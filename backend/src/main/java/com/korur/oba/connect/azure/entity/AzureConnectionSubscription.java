package com.korur.oba.connect.azure.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A subscription the identity of an {@link AzureConnection} could read at the last successful
 * check. A successful check replaces the whole list; a failed check keeps it, so a short Azure
 * outage does not make the organization's sources look unbound.
 *
 * A subscription is listed once per connection, enforced by a unique constraint.
 */
@Entity
@Table(name = "azure_connection_subscriptions", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AzureConnectionSubscription extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Azure subscription id.
     */
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    /**
     * Name Azure reports for the subscription at the last check.
     */
    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    /**
     * State Azure reports for the subscription, such as Enabled or Warned. Kept as text, not an
     * enum, because Azure owns the list of states and may add one.
     */
    @Column(name = "state", length = 32, nullable = false)
    private String state;

    // -------------Relations-----------//

    /**
     * Connection whose identity can read the subscription.
     *
     * No cascade: the subscription does not own the connection. Deleting a connection removes its
     * subscriptions through the foreign key's ON DELETE CASCADE.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false, updatable = false)
    private AzureConnection connection;
}
