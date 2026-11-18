// Package attachment reads, lists, finds, uploads and deletes the attachments the API stores for
// any parent, named by its storage entity type and id, for every app whose records hold files.
// Local files are checked as the API would check them, so that a bad file fails before anything
// is sent.
package attachment

import (
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

func orDash(value string) string {
	value = guardrail.OneLine(value)
	if value == "" {
		return "-"
	}
	return value
}
