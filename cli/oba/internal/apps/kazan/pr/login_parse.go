package pr

import (
	"fmt"
	"regexp"

	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// loginPattern is the API's GitHub login check. A login becomes part of a GitHub search query,
// so nothing else may reach it.
var loginPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9-]{0,38}(\[bot\])?$`)

// ParseLogins splits the --author list of pull requests and refuses more than the limit or an
// entry that is not a GitHub login, before any request. Every entry counts, as the API counts
// them, so a repeated login counts twice.
func ParseLogins(raw string) ([]string, error) {
	logins := userinput.SplitCSV(raw)
	if len(logins) > MaxPullAuthors {
		return nil, fmt.Errorf("--author names %d logins; a filter can name at most %d", len(logins), MaxPullAuthors)
	}
	for _, login := range logins {
		if !loginPattern.MatchString(login) {
			return nil, fmt.Errorf("--author: invalid GitHub login %q", login)
		}
	}
	return logins, nil
}
