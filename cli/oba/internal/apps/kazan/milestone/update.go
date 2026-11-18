package milestone

import (
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// UpdateInput holds the fields to change; a nil field keeps its value. An empty Description or
// StartDate clears it; a color cannot be cleared.
type UpdateInput struct {
	Name        *string
	Description *string
	StartDate   *string
	TargetDate  *string
	Color       *string
}

// Request builds the API body and refuses an empty or malformed value, or no field at all
// (terminal.ErrNothingToUpdate), before any call.
func (in UpdateInput) Request() (client.MilestoneUpdateRequest, error) {
	req := client.MilestoneUpdateRequest{}
	clearField := true
	if in.Name == nil && in.Description == nil && in.StartDate == nil && in.TargetDate == nil && in.Color == nil {
		return req, terminal.ErrNothingToUpdate
	}
	if in.Name != nil {
		if strings.TrimSpace(*in.Name) == "" {
			return req, errors.New("--name cannot be empty")
		}
		req.Name = in.Name
	}
	if in.Description != nil {
		if *in.Description == "" {
			req.ClearDescription = &clearField
		} else {
			req.Description = in.Description
		}
	}
	if in.StartDate != nil {
		if *in.StartDate == "" {
			req.ClearStartDate = &clearField
		} else {
			start, err := dates.ToAPIDate(*in.StartDate)
			if err != nil {
				return req, fmt.Errorf("--start-date: %w", err)
			}
			req.StartDate = &start
		}
	}
	if in.TargetDate != nil {
		target, err := dates.ToAPIDate(*in.TargetDate)
		if err != nil {
			return req, fmt.Errorf("--target-date: %w", err)
		}
		req.TargetDate = &target
	}
	if in.Color != nil {
		if err := CheckColor(*in.Color); err != nil {
			return req, err
		}
		req.Color = in.Color
	}
	return req, nil
}

// Update changes the given fields of the milestone arg names, reachable from board boardID, and
// returns it.
func Update(c *client.Client, boardID, arg string, in UpdateInput) (client.Milestone, error) {
	req, err := in.Request()
	if err != nil {
		return client.Milestone{}, err
	}
	m, err := Resolve(c, boardID, arg)
	if err != nil {
		return client.Milestone{}, err
	}
	return c.UpdateMilestone(owner(m, boardID), m.ID, req)
}
