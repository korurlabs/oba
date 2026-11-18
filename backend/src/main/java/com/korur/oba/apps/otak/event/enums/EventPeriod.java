package com.korur.oba.apps.otak.event.enums;

/**
 * Part of the event list to read, relative to the viewer's calendar day. Only a filter of
 * the list; no column stores it.
 */
public enum EventPeriod {

    /**
     * Events whose last day (the end date, else the start date) is the viewer's today or later.
     */
    UPCOMING,

    /**
     * Events whose last day is before the viewer's today.
     */
    PAST
}
