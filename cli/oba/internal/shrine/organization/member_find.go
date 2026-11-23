package organization

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// FindMember picks the member whose user id is value exactly, else the one whose e-mail is value
// with case ignored. No match is a not-found error naming orgName, so that the exit status is the
// one the API gives for an unknown member and nothing is written.
func FindMember(members []client.Member, orgName, value string) (client.Member, error) {
	value = strings.TrimSpace(value)
	for _, m := range members {
		if m.UserID == value {
			return m, nil
		}
	}
	for _, m := range members {
		if strings.EqualFold(m.Email, value) {
			return m, nil
		}
	}
	return client.Member{}, &client.APIError{
		Status: http.StatusNotFound,
		Message: fmt.Sprintf("no member of %s matches %q; run 'oba ulak member list'",
			guardrail.OneLine(orgName), guardrail.OneLine(value)),
	}
}
