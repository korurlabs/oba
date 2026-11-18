// Package dates turns the dates a user types into the values the Oba API takes, and the instants
// the API returns into what a terminal shows. An instant goes over the wire as RFC 3339 in UTC
// and is shown in the zone of the machine the CLI runs on, the viewer's zone; a calendar day is
// YYYY-MM-DD and never shifts between zones.
package dates
