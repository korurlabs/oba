package cyberscore

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// UpdateInput holds the content fields to change; a nil field keeps its value. An empty
// Description or Category clears it. The outcome and the status have their own commands.
type UpdateInput struct {
	Name        *string
	Expected    *string
	Weight      *int
	Description *string
	SortOrder   *int
	Category    *string
}

// CheckUpdate refuses an update with no field (terminal.ErrNothingToUpdate), a blank name or
// expected result, or a value out of range, before any call.
func (in UpdateInput) CheckUpdate() error {
	if in.Name == nil && in.Expected == nil && in.Weight == nil &&
		in.Description == nil && in.SortOrder == nil && in.Category == nil {
		return terminal.ErrNothingToUpdate
	}
	if in.Name != nil {
		if err := checkRequiredText("--name", *in.Name, maxNameLength); err != nil {
			return err
		}
	}
	if in.Expected != nil {
		if err := checkRequiredText("--expected", *in.Expected, maxTextLength); err != nil {
			return err
		}
	}
	if in.Description != nil {
		if err := checkLength("--description", *in.Description, maxTextLength); err != nil {
			return err
		}
	}
	if in.Weight != nil {
		if err := checkRange("--weight", *in.Weight, minWeight, maxWeight); err != nil {
			return err
		}
	}
	if in.SortOrder != nil {
		if err := checkRange("--sort-order", *in.SortOrder, minSortOrder, maxSortOrder); err != nil {
			return err
		}
	}
	if in.Category != nil {
		if _, err := optionalCategory(*in.Category); err != nil {
			return err
		}
	}
	return nil
}

// UpdateCheck changes only the given fields of the check arg names. The API replaces every
// field, so UpdateCheck sends the stored value of every field that was not given, the outcome
// included, so that a content edit never resets a recorded outcome.
func UpdateCheck(c *client.Client, arg string, in UpdateInput) (client.CyberscoreCheck, error) {
	if err := in.CheckUpdate(); err != nil {
		return client.CyberscoreCheck{}, err
	}
	current, err := ResolveCheck(c, arg)
	if err != nil {
		return client.CyberscoreCheck{}, err
	}
	req, err := merge(current, in)
	if err != nil {
		return client.CyberscoreCheck{}, err
	}
	return c.UpdateCyberscoreCheck(current.ID, req)
}

// merge is the PUT body: the stored values of current with the given fields of in, checked by
// CheckUpdate, laid over them. It never sends a status, so the API keeps the stored one while
// the outcome is unchanged.
func merge(current client.CyberscoreCheck, in UpdateInput) (client.CyberscoreCheckRequest, error) {
	sortOrder := current.SortOrder
	req := client.CyberscoreCheckRequest{
		Name:        current.Name,
		Description: current.Description,
		Expected:    current.Expected,
		Weight:      current.Weight,
		SortOrder:   &sortOrder,
		Category:    current.Category,
		Outcome:     optionalText(current.Outcome),
	}
	if in.Name != nil {
		req.Name = *in.Name
	}
	if in.Expected != nil {
		req.Expected = *in.Expected
	}
	if in.Weight != nil {
		req.Weight = *in.Weight
	}
	if in.SortOrder != nil {
		req.SortOrder = in.SortOrder
	}
	if in.Description != nil {
		req.Description = optionalText(*in.Description)
	}
	if in.Category != nil {
		category, err := optionalCategory(*in.Category)
		if err != nil {
			return client.CyberscoreCheckRequest{}, err
		}
		req.Category = category
	}
	return req, nil
}
