package milestone

import (
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Get reads the milestone arg names, reachable from board boardID, with its description.
func Get(c *client.Client, boardID, arg string) (client.Milestone, error) {
	m, err := Resolve(c, boardID, arg)
	if err != nil {
		return client.Milestone{}, err
	}
	return c.GetMilestone(owner(m, boardID), m.ID)
}

// PrintDetails writes one milestone as labelled lines; the optional ones only when it has them.
func PrintDetails(out io.Writer, m client.Milestone) {
	line := func(label, value string) {
		_, _ = fmt.Fprintf(out, "%-12s %s\n", label, value)
	}
	line("Name:", guardrail.OneLine(m.Name))
	line("ID:", m.ID)
	line("Board:", BoardLabel(m))
	line("Status:", m.Status)
	line("Health:", m.Health)
	if m.Color != nil && *m.Color != "" {
		line("Color:", guardrail.OneLine(*m.Color))
	}
	if m.StartDate != nil {
		line("Start Date:", *m.StartDate)
	}
	line("Target Date:", m.TargetDate)
	line("Progress:", fmt.Sprintf("%d/%d done", m.DoneCount, m.TotalCount))
	PrintDependsOn(out, m.DependsOn)
	if m.Description != nil && *m.Description != "" {
		line("Description:", guardrail.OneLine(*m.Description))
	}
	if m.CreatedAt != "" {
		line("Created:", dates.FormatInstant(m.CreatedAt))
	}
	if m.UpdatedAt != "" {
		line("Updated:", dates.FormatInstant(m.UpdatedAt))
	}
}

// PrintDependsOn writes a "Depends on:" block, one dependency per line, when there are any.
func PrintDependsOn(out io.Writer, deps []client.MilestoneDependency) {
	if len(deps) == 0 {
		return
	}
	_, _ = fmt.Fprintln(out, "Depends on:")
	for _, d := range deps {
		_, _ = fmt.Fprintf(out, "  %s\n", FormatDependency(d))
	}
}

// FormatDependency is the one-line form of a dependency: its name, target date and id (names are
// not unique), and CONFLICT when the dependent starts before the dependency is due.
func FormatDependency(d client.MilestoneDependency) string {
	line := fmt.Sprintf("%s (target %s, %s)", guardrail.OneLine(d.Name), d.TargetDate, d.MilestoneID)
	if d.Conflict {
		line += " [CONFLICT]"
	}
	return line
}
