package client

import (
	"context"
	"errors"
	"net/http"
	"net/url"
)

// Pack is a pack of the organization, as the pack list gives it. ParentPackID is empty for a root
// pack, CurrentVersionID until a first version is approved.
type Pack struct {
	ID               string   `json:"id"`
	Name             string   `json:"name"`
	Description      string   `json:"description"`
	ParentPackID     string   `json:"parentPackId"`
	CurrentVersionID string   `json:"currentVersionId"`
	Required         bool     `json:"required"`
	MountPaths       []string `json:"mountPaths"`
	CreatedAt        string   `json:"createdAt"`
	UpdatedAt        string   `json:"updatedAt"`
}

// PackRequired is the answer of PUT /kaya/packs/{id}/required: the flag after the change and the
// releases it created, one per active repository whose composition changed. An unchanged flag
// creates none.
type PackRequired struct {
	PackID       string `json:"packId"`
	Required     bool   `json:"required"`
	ReleaseCount int    `json:"releaseCount"`
}

// packRequiredRequest is the body of PUT /kaya/packs/{id}/required.
type packRequiredRequest struct {
	Required bool `json:"required"`
}

// Packs lists the packs of the organization, ordered by name (MEMBER).
func (c *Client) Packs(ctx context.Context) ([]Pack, error) {
	var out []Pack
	err := c.doJSON(ctx, http.MethodGet, "/kaya/packs", nil, &out)
	return out, err
}

// SetPackRequired sets whether every repository of the organization composes the pack (OWNER).
// Kaya releases every active repository whose composition changes, in one transaction, and
// refuses the change when a changed composition breaks a rule.
func (c *Client) SetPackRequired(ctx context.Context, packID string, required bool) (PackRequired, error) {
	var out PackRequired
	err := c.doJSON(ctx, http.MethodPut, "/kaya/packs/"+url.PathEscape(packID)+"/required",
		packRequiredRequest{Required: required}, &out)
	return out, err
}

// RefusalViolations reads errorValues.violations of a refusal: the rules a changed composition
// breaks, or the descendant packs that override an item of a pack about to be required. It is
// empty for any other error and for a refusal without violations.
func RefusalViolations(err error) []Violation {
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		return nil
	}
	raw, ok := apiErr.ErrorValues["violations"].([]any)
	if !ok {
		return nil
	}
	violations := make([]Violation, 0, len(raw))
	for _, value := range raw {
		entry, ok := value.(map[string]any)
		if !ok {
			continue
		}
		violation := Violation{
			Path:         stringValue(entry["path"]),
			Rule:         stringValue(entry["rule"]),
			Message:      stringValue(entry["message"]),
			RepositoryID: stringValue(entry["repositoryId"]),
			RootPath:     stringValue(entry["rootPath"]),
		}
		if owners, ok := entry["ownerIds"].([]any); ok {
			for _, owner := range owners {
				if id := stringValue(owner); id != "" {
					violation.OwnerIDs = append(violation.OwnerIDs, id)
				}
			}
		}
		violations = append(violations, violation)
	}
	return violations
}

// stringValue is value when it is a string, else empty: an error value of another shape only
// drops that detail of the refusal.
func stringValue(value any) string {
	text, _ := value.(string)
	return text
}
