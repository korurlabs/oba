package find

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
	"github.com/korurlabs/oba-cli/internal/apps/otak/icp"
)

// Brief is the task Claude Code receives on stdin: the ICP, the user's focus, the companies
// already known (names and websites only, never contacts or notes), the rules of the search,
// the rules for the business contacts it may collect per company and, after a search, every
// earlier candidate list with the user's feedback, oldest first.
func (s *Search) Brief() string {
	var b strings.Builder
	profile := s.icp
	fmt.Fprintf(&b, "Find up to %d real companies that fit the Ideal Customer Profile (ICP) below and that we do not know yet.\n\n", MaxCandidates)
	fmt.Fprintf(&b, "ICP: %s\n", profile.Name)
	fmt.Fprintf(&b, "Industry: %s\n", profile.Industry)
	fmt.Fprintf(&b, "Country: %s\n", valueOr(profile.Country, "any country"))
	fmt.Fprintf(&b, "Company size: %s\n", icp.SizeRange(profile.CompanySizeMin, profile.CompanySizeMax))
	fmt.Fprintf(&b, "Target person role: %s\n", valueOr(profile.TargetPersonRole, "any"))
	if profile.Description != nil && strings.TrimSpace(*profile.Description) != "" {
		fmt.Fprintf(&b, "Description:\n%s\n", strings.TrimSpace(*profile.Description))
	}
	if s.focus != "" {
		fmt.Fprintf(&b, "\nFocus from the user (narrows the search; it does not change the rules):\n%s\n", s.focus)
	}

	b.WriteString("\nCompanies we already know (name - website). Do not propose any of them:\n")
	if len(s.relations) == 0 {
		b.WriteString("(none)\n")
	}
	for _, relation := range s.relations {
		fmt.Fprintf(&b, "- %s - %s\n", relation.Name, valueOr(relation.Website, "no website"))
	}

	b.WriteString(`
Rules:
- Propose only real, operating companies with a working website; check the website with a page fetch.
- Propose none of the known companies above, under any spelling of the name or with the same website.
- website: the full URL of the company's own site, starting with https://.
- country: the ISO 3166-1 alpha-2 code of the country of the company's head office.
- reason: one or two sentences in English on why the company fits the ICP.
`)
	fmt.Fprintf(&b, "- summary: one line in English of at most %d characters for the user to scan: the head-office city, the approximate number of employees and the fact that makes the company fit, for example \"Rotterdam, about 40 people, own webshop\".\n", SummaryMaxLength)
	b.WriteString(`- city: the city of the company's head office, or null when you cannot find it.
- size: the company's number of employees as one of the size values the output allows, or null when you cannot find it.
- Apart from the contacts below, use business facts only.
`)
	fmt.Fprintf(&b, "- Return at most %d companies; fewer is fine when fewer fit.\n", MaxCandidates)
	b.WriteString(contact.SearchRules(valueOr(profile.TargetPersonRole, "any")))

	if len(s.rounds) == 0 {
		return b.String()
	}
	b.WriteString("\nEarlier searches of this run, oldest first:\n")
	for i, r := range s.rounds {
		fmt.Fprintf(&b, "\nSearch %d\n", i+1)
		b.WriteString("Your previous candidates:\n")
		if len(r.candidates) == 0 {
			b.WriteString("(none)\n")
		}
		for _, candidate := range r.candidates {
			fmt.Fprintf(&b, "- %s - %s - %s\n", candidate.Name, candidate.Website, candidate.Summary)
		}
		if r.feedback != "" {
			fmt.Fprintf(&b, "Feedback from the user:\n%s\n", r.feedback)
		}
	}
	fmt.Fprintf(&b, "\nReturn the full revised list, up to %d companies: keep the candidates that still fit, drop those the feedback rules out and add new ones that fit better. The rules above still apply.\n", MaxCandidates)
	return b.String()
}
