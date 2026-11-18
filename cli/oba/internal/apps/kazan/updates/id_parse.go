package updates

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ParseID checks an update id argument before any request: updates have no key, so only their
// id names one.
func ParseID(arg string) (string, error) {
	id := strings.TrimSpace(arg)
	if !userinput.IsID(id) {
		return "", fmt.Errorf("invalid update ID %q: use the ID that 'oba kazan updates list' prints", arg)
	}
	return id, nil
}
