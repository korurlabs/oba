package com.korur.oba.apps.kaizen.ticket.dto.command;

/**
 * An incident reported by an external monitoring source, already translated out of
 * that source's payload, so the ticket domain needs no knowledge of the source.
 *
 * @param key         the key the source gives the incident; tickets with the same key
 *                    on a board belong to the same incident
 * @param title       the ticket title, already shortened by the caller
 * @param description the ticket description, already shortened by the caller
 */
public record IncidentReport(String key, String title, String description) {
}
