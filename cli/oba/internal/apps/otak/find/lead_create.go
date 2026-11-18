package find

import (
	"encoding/json"
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// CreateLeads creates each picked candidate as a lead, one call each in pick order, and prints
// one line per pick. After a lead, its contacts are created on it, one call and one line each.
// Only a failed call counts as a failed lead: a lead whose response cannot be read was still
// created, but its contacts cannot be, as its id is unknown, and they count as failed. The
// contacts of a lead whose create failed are not counted at all. A failure is reported and the
// rest still run; there is no rollback, since a lead without its contact is still worth keeping,
// and any failure makes the command fail.
func CreateLeads(c *client.Client, out io.Writer, candidates []Candidate, picks []int, icp client.Icp, enums LeadEnums) error {
	allowed := newAllowedValues(enums)
	failed, contactsTotal, contactsFailed := 0, 0, 0
	for _, number := range picks {
		candidate := candidates[number-1]
		request := leadRequest(candidate, icp.ID, allowed)
		name := guardrail.OneLine(request.Name)
		body, err := c.Post(relationsPath, request)
		if err != nil {
			failed++
			_, _ = fmt.Fprintf(out, "  ✗ %s: %v\n", name, err)
			continue
		}
		contactsTotal += len(candidate.Contacts)
		var resp client.APIResponse[client.RelationListItem]
		err = json.Unmarshal(body, &resp)
		switch {
		case err != nil:
			_, _ = fmt.Fprintf(out, "  ✓ %s (created, but the response could not be read: %v)\n", name, err)
		case resp.Data.ID == "":
			_, _ = fmt.Fprintf(out, "  ✓ %s (created, but the response holds no id)\n", name)
		default:
			_, _ = fmt.Fprintf(out, "  ✓ %s (%s)\n", name, guardrail.OneLine(resp.Data.ID))
			contactsFailed += contact.CreateFound(c, out, resp.Data.ID, candidate.Contacts)
			continue
		}
		if len(candidate.Contacts) > 0 {
			contactsFailed += len(candidate.Contacts)
			_, _ = fmt.Fprintf(out, "    ✗ %d contacts not created: the lead id is unknown\n", len(candidate.Contacts))
		}
	}
	created := len(picks) - failed
	where := fmt.Sprintf("They are in Otak as %s, stage %s, linked to %q.", leadType, pipelineStageNew, guardrail.OneLine(icp.Name))
	_, _ = fmt.Fprintf(out, "Created %s. %s\n", createdCounts(created, len(picks), contactsTotal-contactsFailed, contactsTotal), where)
	if contactsFailed > 0 {
		_, _ = fmt.Fprintln(out, "Add the missing contacts with 'oba otak contact create <relationId>'.")
	}
	if failed == 0 && contactsFailed == 0 {
		return nil
	}
	var parts []string
	if failed > 0 {
		parts = append(parts, fmt.Sprintf("%d of %d leads", failed, len(picks)))
	}
	if contactsFailed > 0 {
		parts = append(parts, fmt.Sprintf("%d of %d contacts", contactsFailed, contactsTotal))
	}
	return fmt.Errorf("%s failed; see the lines above", strings.Join(parts, " and "))
}

// createdCounts is "K leads", or "K of N leads" when a lead failed, followed by " and C
// contacts" or " and C of M contacts" when there were contacts to create.
func createdCounts(leads, leadsTotal, contacts, contactsTotal int) string {
	text := fmt.Sprintf("%d leads", leads)
	if leads < leadsTotal {
		text = fmt.Sprintf("%d of %d leads", leads, leadsTotal)
	}
	switch {
	case contactsTotal == 0:
	case contacts < contactsTotal:
		text += fmt.Sprintf(" and %d of %d contacts", contacts, contactsTotal)
	default:
		text += fmt.Sprintf(" and %d contacts", contacts)
	}
	return text
}
