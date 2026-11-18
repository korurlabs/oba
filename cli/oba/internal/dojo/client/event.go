package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// EventsPath is the collection of the organization's events.
const EventsPath = "/otak/events"

// Event is one Otak event (an expo, conference, meetup or webinar where leads were met) as
// GET /otak/events lists it (EventListResponse) and GET /otak/events/{id} returns it
// (EventDetailResponse); Notes is set by the detail call only. StartDate and EndDate are
// date-only values, YYYY-MM-DD, never shifted by a time zone. Optional fields are nil when the
// event leaves them empty. Values keeps every property as the API sent it, so that an update
// writes back the unchanged fields byte for byte, since the API replaces them all.
type Event struct {
	ID        string                     `json:"id"`
	Name      string                     `json:"name"`
	Type      string                     `json:"type"`
	StartDate string                     `json:"startDate"`
	EndDate   *string                    `json:"endDate"`
	City      *string                    `json:"city"`
	Country   *string                    `json:"country"`
	Website   *string                    `json:"website"`
	Notes     *string                    `json:"notes"`
	Values    map[string]json.RawMessage `json:"-"`
}

// UnmarshalJSON reads the typed fields and keeps every property in Values.
func (e *Event) UnmarshalJSON(data []byte) error {
	type plain Event
	var typed plain
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	if err := json.Unmarshal(data, &typed.Values); err != nil {
		return err
	}
	*e = Event(typed)
	return nil
}

// ListEvents reads every event of the organization that matches query (period, today, search),
// in the API's order for the period asked.
func (c *Client) ListEvents(query url.Values) ([]Event, error) {
	return ListPages[Event](c, EventsPath, query)
}

// GetEvent reads every field of event id.
func (c *Client) GetEvent(id string) (Event, error) {
	body, err := c.Get(eventPath(id))
	if err != nil {
		return Event{}, err
	}
	event, err := parseEvent(body)
	if err != nil {
		return Event{}, fmt.Errorf("failed to read event %s: %w", id, err)
	}
	return event, nil
}

// CreateEvent sends an EventCreateRequest body and returns the created event.
func (c *Client) CreateEvent(body any) (Event, error) {
	resp, err := c.Post(EventsPath, body)
	if err != nil {
		return Event{}, err
	}
	event, err := parseEvent(resp)
	if err != nil {
		return Event{}, fmt.Errorf("the event was created, but the response could not be read: %w", err)
	}
	return event, nil
}

// UpdateEvent replaces every field of event id with an EventUpdateRequest body and returns the result.
func (c *Client) UpdateEvent(id string, body any) (Event, error) {
	resp, err := c.Put(eventPath(id), body)
	if err != nil {
		return Event{}, err
	}
	event, err := parseEvent(resp)
	if err != nil {
		return Event{}, fmt.Errorf("event %s was updated, but the response could not be read: %w", id, err)
	}
	return event, nil
}

// DeleteEvent deletes event id; the API allows it to organization admins and owners only.
func (c *Client) DeleteEvent(id string) error {
	_, err := c.Delete(eventPath(id))
	return err
}

func eventPath(id string) string {
	return EventsPath + "/" + url.PathEscape(id)
}

func parseEvent(body []byte) (Event, error) {
	var resp APIResponse[Event]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Event{}, err
	}
	return resp.Data, nil
}
