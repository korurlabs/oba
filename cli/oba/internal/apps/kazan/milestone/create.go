package milestone

import (
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
)

// CreateInput is a new milestone. Name and TargetDate are required; the others are sent only
// when given. Without Color the API takes the board's color.
type CreateInput struct {
	Name        string
	TargetDate  string
	StartDate   string
	Description string
	Color       string
}

// check builds the request and refuses missing or malformed values, before any call.
func (in CreateInput) check() (client.MilestoneCreateRequest, error) {
	if strings.TrimSpace(in.Name) == "" {
		return client.MilestoneCreateRequest{}, errors.New("--name is required")
	}
	if strings.TrimSpace(in.TargetDate) == "" {
		return client.MilestoneCreateRequest{}, errors.New("--target-date is required")
	}
	target, err := dates.ToAPIDate(in.TargetDate)
	if err != nil {
		return client.MilestoneCreateRequest{}, fmt.Errorf("--target-date: %w", err)
	}
	req := client.MilestoneCreateRequest{Name: in.Name, TargetDate: target}
	if in.Color != "" {
		if err := CheckColor(in.Color); err != nil {
			return client.MilestoneCreateRequest{}, err
		}
		req.Color = &in.Color
	}
	if in.StartDate != "" {
		start, err := dates.ToAPIDate(in.StartDate)
		if err != nil {
			return client.MilestoneCreateRequest{}, fmt.Errorf("--start-date: %w", err)
		}
		req.StartDate = &start
	}
	if in.Description != "" {
		req.Description = &in.Description
	}
	return req, nil
}

// Check refuses missing or malformed values without a call.
func (in CreateInput) Check() error {
	_, err := in.check()
	return err
}

// Create creates a milestone on board boardID and returns it.
func Create(c *client.Client, boardID string, in CreateInput) (client.Milestone, error) {
	req, err := in.check()
	if err != nil {
		return client.Milestone{}, err
	}
	return c.CreateMilestone(boardID, req)
}
