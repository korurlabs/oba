package com.korur.oba.apps.kazan.ticket.entity;

// 1. Framework-level
import jakarta.persistence.OrderBy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for the Ticket entity mapping.
 *
 * The sub-ticket order is produced by the database through the mapping on the
 * collection, so these tests pin the mapping itself.
 */
class TicketUnitTest {

    private static final String SUB_TICKETS_FIELD = "subTickets";

    @Nested
    class SubTickets {

        @Test
        void mapping_orderClause_sortsByDisplayOrderThenCreatedAtThenId() throws NoSuchFieldException {
            // Arrange
            Field subTickets = Ticket.class.getDeclaredField(SUB_TICKETS_FIELD);

            // Act
            OrderBy orderBy = subTickets.getAnnotation(OrderBy.class);

            // Assert
            assertThat(orderBy)
                    .as("subTickets must declare an order, otherwise the database returns heap order")
                    .isNotNull();
            assertThat(orderBy.value()).isEqualTo("displayOrder ASC, createdAt ASC, id ASC");
        }

        @Test
        void mapping_orderClauseProperties_existOnTicket() throws NoSuchFieldException {
            // Arrange
            Field subTickets = Ticket.class.getDeclaredField(SUB_TICKETS_FIELD);
            String orderClause = subTickets.getAnnotation(OrderBy.class).value();

            // Act
            List<String> properties = Arrays.stream(orderClause.split(","))
                    .map(String::trim)
                    .map(item -> item.split("\\s+")[0])
                    .toList();
            List<String> unresolved = properties.stream()
                    .filter(property -> !isFieldOf(Ticket.class, property))
                    .toList();

            // Assert
            assertThat(properties)
                    .as("the order clause must name at least one property")
                    .isNotEmpty()
                    .doesNotContain("");
            assertThat(unresolved)
                    .as("a property that is no field of Ticket makes Hibernate fail at startup")
                    .isEmpty();
        }
    }

    /**
     * Tells whether the class or one of its superclasses declares a field with the given name.
     *
     * @param type the class to start the search at
     * @param fieldName the field name to look for
     * @return true when the field is declared somewhere in the class hierarchy
     */
    private static boolean isFieldOf(Class<?> type, String fieldName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            boolean declared = Arrays.stream(current.getDeclaredFields())
                    .anyMatch(field -> field.getName().equals(fieldName));
            if (declared) {
                return true;
            }
        }
        return false;
    }
}
