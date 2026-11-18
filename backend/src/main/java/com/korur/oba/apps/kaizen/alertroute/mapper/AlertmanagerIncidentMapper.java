package com.korur.oba.apps.kaizen.alertroute.mapper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.constant.AlertIncidentConstant;
import com.korur.oba.apps.kaizen.ticket.dto.command.IncidentReport;
import com.korur.oba.connect.alertmanager.constant.AlertmanagerConstant;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerAlertRequest;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;

/**
 * Translates an Alertmanager alert into the source-neutral incident the ticket
 * domain understands. Only a firing alert is an incident: Alertmanager only opens
 * tickets, and people own a ticket once it exists. The title and description are
 * cut to the limits of the ticket fields, because the alert content comes from
 * rule authors and is not bounded. Labels and annotations may be absent. Label
 * lines are sorted by name so that the same alert always gives the same text.
 */
@Component
public class AlertmanagerIncidentMapper {

    /**
     * Maps one alert of a notification to an incident report.
     *
     * @param request the notification, for the external URL
     * @param alert   the alert
     * @return the incident keyed by the alert fingerprint, or empty when the alert is not firing
     */
    public Optional<IncidentReport> toIncidentReport(AlertmanagerWebhookRequest request,
                                                     AlertmanagerAlertRequest alert) {
        if (!AlertmanagerConstant.STATUS_FIRING.equals(alert.status())) {
            return Optional.empty();
        }
        return Optional.of(new IncidentReport(alert.fingerprint(), title(alert), description(request, alert)));
    }

    /**
     * Renders the ticket title: "alertname: summary", "alertname" when the
     * summary is absent, "summary" when the alertname is absent, and
     * "Alert fingerprint" when both are absent.
     */
    private String title(AlertmanagerAlertRequest alert) {
        String alertname = valueOf(alert.labels(), AlertmanagerConstant.LABEL_ALERTNAME);
        String summary = valueOf(alert.annotations(), AlertmanagerConstant.ANNOTATION_SUMMARY);

        String title;
        if (alertname != null && summary != null) {
            title = alertname + AlertIncidentConstant.TITLE_SEPARATOR + summary;
        } else if (alertname != null) {
            title = alertname;
        } else if (summary != null) {
            title = summary;
        } else {
            title = AlertIncidentConstant.TITLE_FALLBACK_PREFIX + alert.fingerprint();
        }
        return cut(title, AlertIncidentConstant.TITLE_MAX_LENGTH);
    }

    /**
     * Renders the ticket description, its sections separated by a blank line:
     * the description annotation, the runbook annotation, "Started at", the
     * labels one per line, "Source:" with the generator URL and "Alertmanager:"
     * with the external URL. A section whose value is absent is left out.
     */
    private String description(AlertmanagerWebhookRequest request, AlertmanagerAlertRequest alert) {
        List<String> sections = new ArrayList<>();

        addIfPresent(sections, valueOf(alert.annotations(), AlertmanagerConstant.ANNOTATION_DESCRIPTION));
        addIfPresent(sections, valueOf(alert.annotations(), AlertmanagerConstant.ANNOTATION_RUNBOOK));
        if (alert.startsAt() != null) {
            sections.add(AlertIncidentConstant.DESCRIPTION_STARTED_AT_PREFIX + alert.startsAt());
        }

        if (alert.labels() != null && !alert.labels().isEmpty()) {
            List<String> labelLines = new ArrayList<>();
            for (Map.Entry<String, String> label : new TreeMap<>(alert.labels()).entrySet()) {
                labelLines.add(label.getKey() + AlertIncidentConstant.LABEL_SEPARATOR + label.getValue());
            }
            sections.add(String.join(AlertIncidentConstant.LINE_BREAK, labelLines));
        }

        if (isPresent(alert.generatorURL())) {
            sections.add(AlertIncidentConstant.DESCRIPTION_SOURCE_PREFIX + alert.generatorURL());
        }
        if (isPresent(request.externalURL())) {
            sections.add(AlertIncidentConstant.DESCRIPTION_ALERTMANAGER_PREFIX + request.externalURL());
        }

        return cut(String.join(AlertIncidentConstant.SECTION_BREAK, sections),
                AlertIncidentConstant.DESCRIPTION_MAX_LENGTH);
    }

    private static String valueOf(Map<String, String> values, String key) {
        if (values == null) {
            return null;
        }
        String value = values.get(key);
        return isPresent(value) ? value : null;
    }

    private static void addIfPresent(List<String> sections, String value) {
        if (value != null) {
            sections.add(value);
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String cut(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
