package ticket

import (
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
)

// Get reads the ticket ref names with all its fields.
func Get(c *client.Client, r *Resolver, ref string) (client.TicketDetail, error) {
	target, err := r.Ticket(ref)
	if err != nil {
		return client.TicketDetail{}, err
	}
	detail, err := c.GetTicket(target.BoardID, target.ID)
	if err != nil {
		return client.TicketDetail{}, fmt.Errorf("failed to get ticket: %w", err)
	}
	return detail, nil
}

// PrintDetails writes one ticket as labelled lines; a field the ticket lacks is left out.
func PrintDetails(out io.Writer, t client.TicketDetail) {
	line := func(label, value string) {
		_, _ = fmt.Fprintf(out, "%s %s\n", label, value)
	}
	line("Key:     ", cell(t.Key))
	line("Title:   ", cell(t.Title))
	line("Type:    ", t.Type)
	line("Status:  ", t.Status)
	line("Priority:", t.Priority)
	if t.Description != nil {
		line("Description:", text(*t.Description))
	}
	if len(t.Assignees) > 0 {
		line("Assignees:", people(t.Assignees))
	}
	if len(t.Reviewers) > 0 {
		line("Reviewers:", people(t.Reviewers))
	}
	if t.MissionRank != nil {
		line("Mission Rank:", *t.MissionRank)
	}
	if t.Project != nil {
		line("Project: ", cell(t.Project.Name))
	}
	if t.Sprint != nil {
		line("Sprint:  ", cell(t.Sprint.Name))
	}
	if t.Backlog != nil {
		line("Backlog: ", cell(t.Backlog.Name))
	}
	if m := t.Milestone; m != nil {
		line("Milestone:", fmt.Sprintf("%s (id %s, target %s, %s)", cell(m.Name), m.ID, m.TargetDate, m.Status))
	}
	if t.Weight != nil {
		line("Story Points:", fmt.Sprint(*t.Weight))
	}
	if t.DueDate != nil {
		line("Due Date:", dates.FormatInstant(*t.DueDate))
	}
	if len(t.Labels) > 0 {
		names := make([]string, len(t.Labels))
		for i, l := range t.Labels {
			names[i] = cell(l.Name)
		}
		line("Labels:  ", strings.Join(names, ", "))
	}
	if t.RCA != nil {
		line("RCA:", text(*t.RCA))
	}
	if t.LessonsLearned != nil {
		line("Lessons Learned:", text(*t.LessonsLearned))
	}
	line("Created: ", dates.FormatInstant(t.CreatedAt))
	line("Created By:", creatorName(t.Ticket))
}

func people(list []client.TicketPerson) string {
	names := make([]string, len(list))
	for i, p := range list {
		names[i] = personName(p)
	}
	return strings.Join(names, ", ")
}

// creatorName names whoever created the ticket: a user, or a system actor such as Alertmanager,
// which has no user record. It is empty when the API sends neither.
func creatorName(t client.Ticket) string {
	if t.CreatedBy != nil {
		return personName(*t.CreatedBy)
	}
	if t.CreatedBySystemActor != nil {
		return cell(t.CreatedBySystemActor.Name) + " (System)"
	}
	return ""
}
