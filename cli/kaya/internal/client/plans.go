package client

import (
	"context"
	"net/http"
	"net/url"
)

// planFeedbackPath is the machine-token resource of reviewer feedback that waits for a session.
const planFeedbackPath = "/kaya/delivery/plan-feedback"

// PlanFeedback is one item of reviewer feedback on a plan that no session has received yet.
// SessionID is the Claude Code session that wrote the plan's latest version; Path is empty for
// the plan of a session (kind SESSION).
type PlanFeedback struct {
	ID                  string `json:"id"`
	PlanID              string `json:"planId"`
	PlanURL             string `json:"planUrl"`
	Kind                string `json:"kind"`
	Path                string `json:"path,omitempty"`
	VersionNumber       int    `json:"versionNumber"`
	LatestVersionNumber int    `json:"latestVersionNumber"`
	SessionID           string `json:"sessionId"`
	ReviewerName        string `json:"reviewerName"`
	Decision            string `json:"decision"`
	Comment             string `json:"comment,omitempty"`
	LineStart           int    `json:"lineStart,omitempty"`
	LineEnd             int    `json:"lineEnd,omitempty"`
	Quote               string `json:"quote,omitempty"`
	CreatedAt           string `json:"createdAt"`
}

// The decisions a reviewer gives.
const (
	DecisionApprove        = "APPROVE"
	DecisionRequestChanges = "REQUEST_CHANGES"
	DecisionComment        = "COMMENT"
)

// PendingFeedback reads the pending feedback of the plans whose latest version came from one of
// these sessions of the token's user (1 to 20 ids), oldest first, at most 50.
func (c *Client) PendingFeedback(ctx context.Context, sessionIDs []string) ([]PlanFeedback, error) {
	query := url.Values{"sessionId": sessionIDs}
	var items []PlanFeedback
	return items, c.doJSON(ctx, http.MethodGet, planFeedbackPath+"?"+query.Encode(), nil, &items)
}

// PendingFeedbackForBranch reads the pending feedback of the plans whose latest session ended on
// this branch of the repository with this remote, for a new session that takes the work over.
func (c *Client) PendingFeedbackForBranch(ctx context.Context, remoteURL, branch string) ([]PlanFeedback, error) {
	query := url.Values{"remoteUrl": {remoteURL}, "branch": {branch}}
	var items []PlanFeedback
	return items, c.doJSON(ctx, http.MethodGet, planFeedbackPath+"?"+query.Encode(), nil, &items)
}

// AckFeedback reports that the session received the feedback (1 to 50 ids) and answers how many
// rows this call marked; already delivered and foreign ids are skipped, so a resend is harmless.
func (c *Client) AckFeedback(ctx context.Context, sessionID string, feedbackIDs []string) (int, error) {
	var answer struct {
		Acknowledged int `json:"acknowledged"`
	}
	err := c.doJSON(ctx, http.MethodPost, planFeedbackPath+"/acks",
		map[string]any{"sessionId": sessionID, "feedbackIds": feedbackIDs}, &answer)
	return answer.Acknowledged, err
}
