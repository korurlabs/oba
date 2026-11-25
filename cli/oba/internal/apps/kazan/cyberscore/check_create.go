package cyberscore

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// CreateInput is a new check. Name, Expected and Weight are required; the others are sent only
// when given. Without Outcome the check starts as TODO, and without Status the API derives it
// from the outcome.
type CreateInput struct {
	Name        string
	Expected    string
	Weight      *int
	Description string
	SortOrder   *int
	Category    string
	Outcome     string
	Status      string
}

// request builds the API body and refuses a missing or out-of-range value, before any call.
func (in CreateInput) request() (client.CyberscoreCheckRequest, error) {
	if strings.TrimSpace(in.Name) == "" {
		return client.CyberscoreCheckRequest{}, errors.New("--name is required")
	}
	if strings.TrimSpace(in.Expected) == "" {
		return client.CyberscoreCheckRequest{}, errors.New("--expected is required")
	}
	if in.Weight == nil {
		return client.CyberscoreCheckRequest{}, errors.New("--weight is required")
	}
	req := client.CyberscoreCheckRequest{
		Name: in.Name, Expected: in.Expected, Weight: *in.Weight,
		Description: optionalText(in.Description), SortOrder: in.SortOrder,
	}
	if err := checkFields(req); err != nil {
		return client.CyberscoreCheckRequest{}, err
	}
	category, err := optionalCategory(in.Category)
	if err != nil {
		return client.CyberscoreCheckRequest{}, err
	}
	req.Category = category
	if strings.TrimSpace(in.Outcome) != "" {
		outcome, err := ParseOutcome(in.Outcome)
		if err != nil {
			return client.CyberscoreCheckRequest{}, err
		}
		req.Outcome = &outcome
	}
	if strings.TrimSpace(in.Status) != "" {
		status, err := ParseStatus(in.Status)
		if err != nil {
			return client.CyberscoreCheckRequest{}, err
		}
		req.Status = &status
	}
	return req, nil
}

// Check refuses a missing or out-of-range value without a call.
func (in CreateInput) Check() error {
	_, err := in.request()
	return err
}

// CreateCheck creates a check in the organization and returns it.
func CreateCheck(c *client.Client, in CreateInput) (client.CyberscoreCheck, error) {
	req, err := in.request()
	if err != nil {
		return client.CyberscoreCheck{}, err
	}
	return c.CreateCyberscoreCheck(req)
}

// checkFields refuses a body whose text or number fields break the API's limits.
func checkFields(req client.CyberscoreCheckRequest) error {
	if err := checkRequiredText("--name", req.Name, maxNameLength); err != nil {
		return err
	}
	if err := checkRequiredText("--expected", req.Expected, maxTextLength); err != nil {
		return err
	}
	if req.Description != nil {
		if err := checkLength("--description", *req.Description, maxTextLength); err != nil {
			return err
		}
	}
	if err := checkRange("--weight", req.Weight, minWeight, maxWeight); err != nil {
		return err
	}
	if req.SortOrder != nil {
		return checkRange("--sort-order", *req.SortOrder, minSortOrder, maxSortOrder)
	}
	return nil
}
