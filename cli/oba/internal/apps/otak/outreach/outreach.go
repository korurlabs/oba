// Package outreach prepares reach-outs to one contact of a relation and lists them with their
// status. A MAIL outreach is a DRAFT with its own mail, copied from a mail template or written in
// a .md file, and the attachments of that mail; a WHATSAPP or CALL outreach happens outside Oba
// and is recorded as done with Complete.
package outreach

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

func optional(value *string) string {
	if value == nil {
		return "-"
	}
	return orDash(*value)
}
