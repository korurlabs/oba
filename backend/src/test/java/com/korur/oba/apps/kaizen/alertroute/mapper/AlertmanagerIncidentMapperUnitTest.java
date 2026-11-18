package com.korur.oba.apps.kaizen.alertroute.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.mapper.AlertmanagerIncidentMapper;
import com.korur.oba.apps.kaizen.ticket.dto.command.IncidentReport;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerAlertRequest;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;

class AlertmanagerIncidentMapperUnitTest {

    private static final Instant STARTS = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-10-01T11:30:00Z");

    private final AlertmanagerIncidentMapper mapper = new AlertmanagerIncidentMapper();

    private static AlertmanagerAlertRequest alert(Map<String, String> labels, Map<String, String> annotations) {
        return new AlertmanagerAlertRequest("firing", labels, annotations, STARTS, ENDS, "http://prom/graph", "fp1");
    }

    private static AlertmanagerWebhookRequest request(String externalUrl, AlertmanagerAlertRequest alert) {
        return new AlertmanagerWebhookRequest("4", "gk", 0, "firing", "kaizen", null, null, null,
                externalUrl, List.of(alert));
    }

    private IncidentReport report(String externalUrl, AlertmanagerAlertRequest alert) {
        Optional<IncidentReport> report = mapper.toIncidentReport(request(externalUrl, alert), alert);
        assertThat(report).isPresent();
        return report.get();
    }

    @Nested
    class Status {
        @Test
        void firingAlert_mapsWithFingerprintAsKey() {
            IncidentReport report = report("http://am", alert(Map.of("alertname", "X"), null));

            assertThat(report.key()).isEqualTo("fp1");
        }

        @Test
        void resolvedAlert_returnsEmpty() {
            AlertmanagerAlertRequest resolved = new AlertmanagerAlertRequest("resolved", null, null, null, null, null, "fp1");

            assertThat(mapper.toIncidentReport(request(null, resolved), resolved)).isEmpty();
        }

        @Test
        void unknownStatus_returnsEmpty() {
            AlertmanagerAlertRequest weird = new AlertmanagerAlertRequest("weird", null, null, null, null, null, "fp1");

            assertThat(mapper.toIncidentReport(request(null, weird), weird)).isEmpty();
        }
    }

    @Nested
    class Title {
        @Test
        void alertnameAndSummary_joinsBoth() {
            IncidentReport report = report(null,
                    alert(Map.of("alertname", "KeycloakDown"), Map.of("summary", "Keycloak is down")));

            assertThat(report.title()).isEqualTo("KeycloakDown: Keycloak is down");
        }

        @Test
        void summaryAbsent_usesAlertname() {
            assertThat(report(null, alert(Map.of("alertname", "KeycloakDown"), Map.of())).title())
                    .isEqualTo("KeycloakDown");
        }

        @Test
        void alertnameAbsent_usesSummary() {
            assertThat(report(null, alert(Map.of(), Map.of("summary", "Just summary"))).title())
                    .isEqualTo("Just summary");
        }

        @Test
        void bothAbsent_usesFingerprintFallback() {
            assertThat(report(null, alert(null, null)).title()).isEqualTo("Alert fp1");
        }

        @Test
        void longTitle_cutTo255() {
            assertThat(report(null, alert(Map.of("alertname", "a".repeat(300)), null)).title()).hasSize(255);
        }
    }

    @Nested
    class Description {
        @Test
        void allParts_inOrderWithSortedLabels() {
            AlertmanagerAlertRequest alert = alert(
                    Map.of("severity", "critical", "alertname", "X"),
                    Map.of("description", "Desc text", "runbook", "http://runbook"));

            assertThat(report("http://am", alert).description()).isEqualTo(String.join("\n\n",
                    "Desc text",
                    "http://runbook",
                    "Started at 2026-10-01T10:00:00Z",
                    "alertname: X\nseverity: critical",
                    "Source: http://prom/graph",
                    "Alertmanager: http://am"));
        }

        @Test
        void nullLabelsAndAnnotations_leavesSectionsOut() {
            AlertmanagerAlertRequest alert = new AlertmanagerAlertRequest("firing", null, null, null, null, null, "fp1");

            assertThat(report(null, alert).description()).isEmpty();
        }

        @Test
        void longDescription_cutTo10000() {
            AlertmanagerAlertRequest alert = alert(null, Map.of("description", "d".repeat(12000)));

            assertThat(report("http://am", alert).description()).hasSize(10000);
        }
    }
}
