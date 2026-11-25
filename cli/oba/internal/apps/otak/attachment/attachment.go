// Package attachment holds what is Otak's own about the attachments of a mail, a relation or a
// campaign; the storage calls themselves are in internal/dojo/attachment. The mail is the own mail
// of a MAIL outreach, found from the outreach; a relation or campaign is addressed directly. An
// attachment is uploaded from local files; a document's PDF reaches a mail through
// 'oba document attach-to-mail'. The API refuses every change to the attachments of a mail once
// its outreach is SENT.
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
