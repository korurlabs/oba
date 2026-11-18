package com.korur.oba.apps.otak.event.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema, that the upcoming and past tabs of the events
 * list split on the viewer's day: an event is over only after its last day, a one-day event
 * lasts through its start date, and each tab has its own order.
 */
class EventPeriodIT extends AbstractTenantIT {

    private static final String EVENTS_PATH = "/otak/events";
    private static final String TODAY = "2026-11-14";

    private Tenant tenant;
    private Person user;
    private String suffix;

    @BeforeEach
    void setUpEvents() throws Exception {
        tenant = newTenant();
        user = signIn(tenant, "otakperiod");
        suffix = uniqueSuffix();

        create("A endsToday", "2026-11-10", "2026-11-14");
        create("B endedYesterday", "2026-11-10", "2026-11-13");
        create("C oneDayToday", "2026-11-14", null);
        create("D oneDayYesterday", "2026-11-13", null);
        create("E startsTomorrow", "2026-11-15", "2026-11-16");
        create("F farFuture", "2027-03-01", null);
        // Same start as E; the name breaks the tie in the upcoming order.
        create("A sameStartAsE", "2026-11-15", null);
    }

    private void create(String label, String start, String end) throws Exception {
        String endJson = end == null ? "" : ",\"endDate\":\"" + end + "\"";
        mockMvc.perform(in(tenant, post(EVENTS_PATH), user.jwt(),
                        "{\"name\":\"" + label + " " + suffix + "\",\"type\":\"MEETUP\",\"startDate\":\"" + start
                                + "\"" + endJson + "}"))
                .andExpect(status().isCreated());
    }

    private List<String> names(String query) throws Exception {
        String body = mockMvc.perform(in(tenant, get(EVENTS_PATH + "?search=" + suffix + query), user.jwt(), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> names = com.jayway.jsonpath.JsonPath.read(body, "$.data[*].name");
        return names.stream().map((String name) -> name.replace(" " + suffix, "")).toList();
    }

    @Test
    void upcoming_includesEventEndingTodayAndOneDayEventToday_soonestStartThenName() throws Exception {
        assertThat(names("&period=UPCOMING&today=" + TODAY)).containsExactly(
                "A endsToday", "C oneDayToday", "A sameStartAsE", "E startsTomorrow", "F farFuture");
    }

    @Test
    void past_excludesEventEndingToday_mostRecentStartFirst() throws Exception {
        assertThat(names("&period=PAST&today=" + TODAY)).containsExactly("D oneDayYesterday", "B endedYesterday");
    }

    @Test
    void today_oneDayLater_movesEventsEndingTodayToPast() throws Exception {
        assertThat(names("&period=PAST&today=2026-11-15")).contains("A endsToday", "C oneDayToday");
    }

    @Test
    void withoutPeriod_listsEveryEventMostRecentStartFirst() throws Exception {
        assertThat(names("")).hasSize(7).startsWith("F farFuture");
    }

    @Test
    void periodWithoutToday_answers422() throws Exception {
        mockMvc.perform(in(tenant, get(EVENTS_PATH + "?period=UPCOMING"), user.jwt(), null))
                .andExpect(status().isUnprocessableEntity());
    }
}
