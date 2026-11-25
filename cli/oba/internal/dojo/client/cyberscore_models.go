package client

import "encoding/json"

// Cyberscore is the live security score of the organization (CyberscoreResponse). ScoreLevel is
// UNKNOWN, with ScorePercentage 0, until a check has a scorable outcome. BreakdownByCategory maps
// a check category to its own percentage. LastCalculatedAt is an RFC 3339 instant, nil before
// the first calculation.
type Cyberscore struct {
	ScorePercentage     int            `json:"scorePercentage"`
	ScoreLevel          string         `json:"scoreLevel"`
	BreakdownByCategory map[string]int `json:"breakdownByCategory"`
	ScorableCheckCount  int            `json:"scorableCheckCount"`
	LastCalculatedAt    *string        `json:"lastCalculatedAt"`
}

// CyberscoreTrendPoint is the score of one day (CyberscoreTrendPointResponse); Date is a
// YYYY-MM-DD day.
type CyberscoreTrendPoint struct {
	Date            string `json:"date"`
	ScorePercentage int    `json:"scorePercentage"`
	ScoreLevel      string `json:"scoreLevel"`
}

// CyberscoreCheck is one check behind the score (CheckResponse). Description and Category are
// nil when the check has none. Raw keeps the check as the API sent it, for the --json result of
// a check picked from the list.
type CyberscoreCheck struct {
	ID          string          `json:"id"`
	Name        string          `json:"name"`
	Description *string         `json:"description"`
	Expected    string          `json:"expected"`
	Weight      int             `json:"weight"`
	SortOrder   int             `json:"sortOrder"`
	Category    *string         `json:"category"`
	Outcome     string          `json:"outcome"`
	Status      string          `json:"status"`
	UpdatedAt   string          `json:"updatedAt"`
	Raw         json.RawMessage `json:"-"`
}

// UnmarshalJSON reads the typed fields and keeps the whole object in Raw.
func (ch *CyberscoreCheck) UnmarshalJSON(data []byte) error {
	type plain CyberscoreCheck
	var typed plain
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	typed.Raw = append(json.RawMessage(nil), data...)
	*ch = CyberscoreCheck(typed)
	return nil
}

// CyberscoreCheckRequest is the body of POST /kazan/cyberscore/checks and of PUT
// /kazan/cyberscore/checks/{id} (CheckRequest). The PUT replaces every field: a nil Description
// or Category clears it and a nil SortOrder becomes 0. A nil Outcome is TODO on create and keeps
// the stored outcome on change; a nil Status is derived from the outcome.
type CyberscoreCheckRequest struct {
	Name        string  `json:"name"`
	Description *string `json:"description,omitempty"`
	Expected    string  `json:"expected"`
	Weight      int     `json:"weight"`
	SortOrder   *int    `json:"sortOrder,omitempty"`
	Category    *string `json:"category,omitempty"`
	Outcome     *string `json:"outcome,omitempty"`
	Status      *string `json:"status,omitempty"`
}

// CyberscoreCheckOutcomeRequest is the body of PUT /kazan/cyberscore/checks/{id}/outcome.
type CyberscoreCheckOutcomeRequest struct {
	Outcome string `json:"outcome"`
}

// CyberscoreCheckStatusRequest is the body of PUT /kazan/cyberscore/checks/{id}/status.
type CyberscoreCheckStatusRequest struct {
	Status string `json:"status"`
}
