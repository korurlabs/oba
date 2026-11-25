package ticket

import (
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestExplainAgentReviewerNamesTheAgentAndWhatToDo(t *testing.T) {
	apiErr := &client.APIError{Status: http.StatusUnprocessableEntity, Code: "AGT_VAL_001",
		Message: "This action does not apply to an agent.",
		Fields:  map[string]string{"action": "TICKET_REVIEWER", "userId": "u-agent"}}

	err := explainAgentReviewer(apiErr)

	for _, part := range []string{"user u-agent is an agent", "Name a person with --reviewer", "--assignee",
		"oba kazan member list", "AGT_VAL_001"} {
		if !strings.Contains(err.Error(), part) {
			t.Errorf("err lacks %q: %v", part, err)
		}
	}
	var unwrapped *client.APIError
	if !errors.As(err, &unwrapped) || unwrapped != apiErr {
		t.Errorf("err does not unwrap to the API error: %v", err)
	}
}

func TestExplainAgentReviewerLeavesOtherErrorsAsTheyAre(t *testing.T) {
	otherAction := &client.APIError{Status: http.StatusUnprocessableEntity, Code: "AGT_VAL_001",
		Fields: map[string]string{"action": "BOARD_ROLE"}}
	otherCode := &client.APIError{Status: http.StatusUnprocessableEntity, Code: "GEN_VAL_001",
		Fields: map[string]string{"action": "TICKET_REVIEWER"}}
	plain := errors.New("boom")

	for _, err := range []error{otherAction, otherCode, plain} {
		if got := explainAgentReviewer(err); got != err {
			t.Errorf("explainAgentReviewer(%v) = %v", err, got)
		}
	}
}
