// Package event lists, creates, updates and deletes the events of Otak: the expos, conferences,
// meetups and webinars where the organization meets companies. A create or update takes a JSON
// object checked against the live EventCreateRequest or EventUpdateRequest schema.
package event

// createSchema is the body of a create, updateSchema the body of an update, which replaces every
// field.
const (
	createSchema = "EventCreateRequest"
	updateSchema = "EventUpdateRequest"
)
