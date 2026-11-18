// Package mailtemplate lists, pushes and pulls the mail templates of the organization: the
// reusable mails a MAIL outreach copies its own mail from. Each is a .md file, the subject on the
// first line and the body after it.
package mailtemplate

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
