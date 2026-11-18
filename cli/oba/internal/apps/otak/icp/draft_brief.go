package icp

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// DraftSchema is the JSON Schema of the draft Claude Code returns: exactly the request fields,
// with the types, allowed values and limits of the live API.
func DraftSchema(fields []apidocs.Field) (string, error) {
	return apidocs.Schema(fields)
}

// Brief is the task Claude Code receives on stdin: the user's description of their ideal
// customer, the request fields as a template and the rules of the draft.
func Brief(description string, fields []apidocs.Field) string {
	var b strings.Builder
	b.WriteString("Draft one Ideal Customer Profile (ICP) for our company from the description below. ")
	b.WriteString("An ICP describes the kind of company we want to win as a customer.\n\n")
	b.WriteString("Description, as the user typed it:\n")
	b.WriteString(strings.TrimSpace(description))
	b.WriteString("\n\nReturn one JSON object with these fields; null leaves a field open:\n\n")
	b.WriteString(apidocs.Template(fields, true))
	b.WriteString("\n\nRules:\n")
	b.WriteString(profileRules)
	b.WriteString("- Search the web only when a term in the description needs it to pick a value.\n")
	return b.String()
}

// profileRules hold for every ICP Claude Code writes, as a draft or as a brainstorm proposal.
const profileRules = `- Return only the fields above, with exactly the allowed values listed for a field that has them.
- name: a short, specific name for the profile, such as "Dutch textile SMEs".
- Pick the closest allowed value where the description implies one. Use null where it leaves a field open; do not guess a country, size or role it does not imply.
- A minimum size must not be above the maximum size.
- description: two to four sentences in English that state who the profile targets and why they fit, in the user's terms.
- Include no personal data about people: no names, e-mail addresses or phone numbers of persons.
`
