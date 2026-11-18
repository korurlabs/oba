package find

import (
	"encoding/json"
	"reflect"
	"strings"
	"testing"
)

func TestSchema(t *testing.T) {
	s, err := Schema(LeadEnums{Countries: []string{"NL", "DE"}, Sizes: []string{"SIZE_1_10"}})
	if err != nil {
		t.Fatal(err)
	}
	var parsed struct {
		Properties struct {
			Candidates struct {
				MaxItems int `json:"maxItems"`
				Items    struct {
					Required   []string `json:"required"`
					Properties struct {
						Contacts struct {
							MaxItems int `json:"maxItems"`
							Items    struct {
								Required             []string `json:"required"`
								AdditionalProperties bool     `json:"additionalProperties"`
							} `json:"items"`
						} `json:"contacts"`
						Country struct {
							Enum []string `json:"enum"`
						} `json:"country"`
						Summary struct {
							MaxLength int `json:"maxLength"`
						} `json:"summary"`
						City struct {
							Type []string `json:"type"`
						} `json:"city"`
						Size struct {
							Type []string `json:"type"`
							Enum []any    `json:"enum"`
						} `json:"size"`
					} `json:"properties"`
				} `json:"items"`
			} `json:"candidates"`
		} `json:"properties"`
	}
	if err := json.Unmarshal([]byte(s), &parsed); err != nil {
		t.Fatal(err)
	}
	c := parsed.Properties.Candidates
	if c.MaxItems != MaxCandidates || !reflect.DeepEqual(c.Items.Properties.Country.Enum, []string{"NL", "DE"}) ||
		!reflect.DeepEqual(c.Items.Required, []string{"name", "website", "country", "reason", "summary", "city", "size", "contacts"}) {
		t.Errorf("schema = %s", s)
	}
	p := c.Items.Properties
	if p.Summary.MaxLength != SummaryMaxLength || !reflect.DeepEqual(p.City.Type, []string{"string", "null"}) ||
		!reflect.DeepEqual(p.Size.Type, []string{"string", "null"}) || !reflect.DeepEqual(p.Size.Enum, []any{"SIZE_1_10", nil}) {
		t.Errorf("schema = %s", s)
	}
	ct := p.Contacts
	if ct.MaxItems != 2 || ct.Items.AdditionalProperties ||
		!reflect.DeepEqual(ct.Items.Required, []string{"name", "role", "email", "phone", "sourceUrl"}) {
		t.Errorf("contacts schema = %s", s)
	}
	open, err := Schema(LeadEnums{})
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(open, `"enum"`) {
		t.Errorf("schema without countries has an enum: %s", open)
	}
}
