package cyberscore

import (
	"errors"
	"fmt"
	"net/http"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// errNoCheck refuses a command that names no check. A picker is not offered: the command must
// run the same way in a script and on a terminal.
var errNoCheck = errors.New("check name or id is required")

// ResolveCheck finds the check arg names among the checks of the organization. The list read is
// a lookup and stays out of --json.
func ResolveCheck(c *client.Client, arg string) (client.CyberscoreCheck, error) {
	if strings.TrimSpace(arg) == "" {
		return client.CyberscoreCheck{}, errNoCheck
	}
	checks, err := c.Unrecorded().ListCyberscoreChecks("")
	if err != nil {
		return client.CyberscoreCheck{}, err
	}
	return matchCheck(checks, arg)
}

// matchCheck picks the check arg names: an id; else the check with exactly that name; else the
// one whose name matches with case ignored. An id that is not listed belongs to no check of this
// organization and exits 2, as the API's 404 would. Several case-ignored matches are refused with
// their ids, since only the id tells them apart.
func matchCheck(checks []client.CyberscoreCheck, arg string) (client.CyberscoreCheck, error) {
	arg = strings.TrimSpace(arg)
	if arg == "" {
		return client.CyberscoreCheck{}, errNoCheck
	}
	if userinput.IsID(arg) {
		for _, ch := range checks {
			if strings.EqualFold(ch.ID, arg) {
				return ch, nil
			}
		}
		return client.CyberscoreCheck{}, &client.APIError{
			Status:  http.StatusNotFound,
			Message: fmt.Sprintf("check %s not found in this organization", arg),
		}
	}
	for _, ch := range checks {
		if ch.Name == arg {
			return ch, nil
		}
	}
	var matches []client.CyberscoreCheck
	for _, ch := range checks {
		if strings.EqualFold(ch.Name, arg) {
			matches = append(matches, ch)
		}
	}
	switch len(matches) {
	case 1:
		return matches[0], nil
	case 0:
		if len(checks) == 0 {
			return client.CyberscoreCheck{}, fmt.Errorf("check %q not found: the organization has no checks", arg)
		}
		names := make([]string, len(checks))
		for i, ch := range checks {
			names[i] = guardrail.OneLine(ch.Name)
		}
		return client.CyberscoreCheck{}, fmt.Errorf("check %q not found. Available checks: %s", arg, strings.Join(names, ", "))
	default:
		lines := make([]string, len(matches))
		for i, ch := range matches {
			lines[i] = fmt.Sprintf("  %s  %s", ch.ID, guardrail.OneLine(ch.Name))
		}
		return client.CyberscoreCheck{}, fmt.Errorf("%d checks are named %q; pass the check id instead:\n%s",
			len(matches), arg, strings.Join(lines, "\n"))
	}
}
