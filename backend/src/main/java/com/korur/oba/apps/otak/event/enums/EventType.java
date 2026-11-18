package com.korur.oba.apps.otak.event.enums;

/**
 * Kind of event where an organization meets companies. Mapped by name to the PostgreSQL
 * enum otak_event_type_enum, in its order.
 */
public enum EventType {

    EXPO,

    CONFERENCE,

    MEETUP,

    WEBINAR,

    OTHER
}
