// Package template lists, pushes and pulls the document templates of the organization: the
// reusable proposals, NDAs and other documents whose PDF a mail or a relation gets as an
// attachment ('oba otak attachment add --template'). Each is an HTML file whose PDF the CLI renders on push. Mails are mail templates, in package
// mailtemplate.
package template

import (
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const kind = "template"

func orDash(value string) string {
	value = guardrail.OneLine(value)
	if value == "" {
		return "-"
	}
	return value
}
