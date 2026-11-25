package ticket

import (
	"errors"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// The API refuses an agent named as a ticket reviewer with this code and action: reviewing is a
// person's decision, so an agent works a ticket only as its assignee.
const (
	codeAgentTargetRefused = "AGT_VAL_001"
	actionTicketReviewer   = "TICKET_REVIEWER"
	fieldAction            = "action"
	fieldUserID            = "userId"
)

// agentReviewerRefusal is the API's refusal of an agent reviewer with a text that says what to do
// instead. It unwraps to the API error, so the exit status and the --json error stay the API's.
type agentReviewerRefusal struct {
	err    *client.APIError
	userID string
}

func (r *agentReviewerRefusal) Error() string {
	return fmt.Sprintf("user %s is an agent, and an agent cannot review a ticket. Name a person with --reviewer; "+
		"an agent works a ticket as its assignee (--assignee). 'oba kazan member list' shows who is an agent "+
		"in its KIND column (%s)", r.userID, r.err.Code)
}

func (r *agentReviewerRefusal) Unwrap() error {
	return r.err
}

// explainAgentReviewer replaces the API's refusal of an agent reviewer with agentReviewerRefusal.
// Any other error is returned as it is.
func explainAgentReviewer(err error) error {
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) || apiErr.Code != codeAgentTargetRefused || apiErr.Fields[fieldAction] != actionTicketReviewer {
		return err
	}
	return &agentReviewerRefusal{err: apiErr, userID: guardrail.OneLine(apiErr.Fields[fieldUserID])}
}
