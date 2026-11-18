package com.korur.oba.apps.otak.event.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.dto.request.EventCreateRequest;
import com.korur.oba.apps.otak.event.dto.request.EventUpdateRequest;
import com.korur.oba.apps.otak.event.entity.Event;
import com.korur.oba.apps.otak.event.enums.EventType;
import com.korur.oba.apps.otak.relation.enums.Country;

class EventMapperUnitTest {

    private EventMapper eventMapper;

    @BeforeEach
    void setUp() {
        eventMapper = new EventMapperImpl();
    }

    private static Event stored() {
        Event event = Event.builder()
                .name("Old")
                .type(EventType.EXPO)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 3))
                .city("Istanbul")
                .country(Country.TR)
                .website("https://old.example.com")
                .notes("Old notes")
                .organizationId(UUID.randomUUID())
                .build();
        event.setId(UUID.randomUUID());
        return event;
    }

    @Test
    void toEntity_request_copiesTheFields() {
        // Act
        Event event = eventMapper.toEntity(new EventCreateRequest("IGEF", EventType.EXPO, LocalDate.of(2026, 11, 19), null,
                "Istanbul", Country.TR, "https://igef.com.tr", "Hall 4"));

        // Assert
        assertThat(event.getName()).isEqualTo("IGEF");
        assertThat(event.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(event.getEndDate()).isNull();
        assertThat(event.getCity()).isEqualTo("Istanbul");
        assertThat(event.getNotes()).isEqualTo("Hall 4");
        assertThat(event.getId()).isNull();
        assertThat(event.getOrganizationId()).isNull();
    }

    @Test
    void updateEntity_nullInEveryNullableField_clearsTheStoredValue() {
        // Arrange
        Event event = stored();
        UUID id = event.getId();
        UUID organizationId = event.getOrganizationId();

        // Act
        eventMapper.updateEntity(event, new EventUpdateRequest("New", EventType.WEBINAR, LocalDate.of(2026, 5, 1), null,
                null, null, null, null));

        // Assert
        assertThat(event.getName()).isEqualTo("New");
        assertThat(event.getType()).isEqualTo(EventType.WEBINAR);
        assertThat(event.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(event.getEndDate()).isNull();
        assertThat(event.getCity()).isNull();
        assertThat(event.getCountry()).isNull();
        assertThat(event.getWebsite()).isNull();
        assertThat(event.getNotes()).isNull();
        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getOrganizationId()).isEqualTo(organizationId);
    }
}
