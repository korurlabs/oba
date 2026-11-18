package pr

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ParseReviewers splits a reviewer list and refuses more than the limit or an entry given twice,
// before any request. flag names the flag in messages.
func ParseReviewers(flag, raw string) ([]string, error) {
	refs := userinput.SplitCSV(raw)
	if len(refs) > MaxReviewers {
		return nil, fmt.Errorf("--%s names %d reviewers; a share can name at most %d", flag, len(refs), MaxReviewers)
	}
	if err := refuseTwice(flag, refs); err != nil {
		return nil, err
	}
	return refs, nil
}

// refuseTwice refuses a list that names one entry twice, case ignored.
func refuseTwice(flag string, refs []string) error {
	seen := map[string]bool{}
	for _, ref := range refs {
		key := strings.ToLower(ref)
		if seen[key] {
			return fmt.Errorf("--%s names %s twice", flag, ref)
		}
		seen[key] = true
	}
	return nil
}
