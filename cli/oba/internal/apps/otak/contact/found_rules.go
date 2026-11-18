package contact

import (
	"fmt"
	"strings"
)

// SearchRules is the part of a brief that says which people Claude Code may collect for a
// company and what it may record of them. oba otak find and oba otak contact fill share it, so
// that both collect only business contacts from the company's own site or a public business
// register. targetRole is the role to prefer.
func SearchRules(targetRole string) string {
	var b strings.Builder
	fmt.Fprintf(&b, "\nContacts (the contacts of each company: at most %d people; an empty list is fine when you find no one):\n", MaxFound)
	fmt.Fprintf(&b, "- Look for people named, in a business role, on the company's own website (team, about, contact or imprint page) or in a public business register. Prefer people in the target person role (%s); else the owner or a director.\n", targetRole)
	b.WriteString(`- Per person give only: name; role, the work role the source states, or null; email, the business e-mail address the source publishes for that person, or null; phone, the business phone number the source publishes for that person, or null; sourceUrl, the full URL of the exact page you found the person on, starting with https://.
- Do not collect: home or private addresses, private e-mail addresses or domains, private or personal phone numbers, dates of birth, photos, social media profiles (LinkedIn included), family, health, opinions, or any other special category of personal data.
- Do not use people-search sites, data brokers, leak sites or paid databases. Do not guess or build an e-mail address from a pattern. Do not include anyone who is not shown in a business role.
`)
	return b.String()
}
