package ticket

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
)

const updateHelp = `Changes only the fields whose flags are given; every other field keeps its value. --milestone ""
detaches the ticket from its milestone and --parent "" from its parent ticket. The sprint or
backlog is changed with 'oba kazan ticket move'.

` + ticketRefHelp + `

  oba kazan ticket update OPS-42 --status IN_REVIEW --reviewer b@example.com`

func newUpdateCmd() *cobra.Command {
	var title, description, typ, status, priority, rank, project, milestone, assignee, reviewer, label string
	var dueDate, rca, lessons, parent, sprintRetired, backlogRetired string
	var storyPoints, percentage int
	cmd := &cobra.Command{
		Use:   "update <ticket>",
		Short: "Change the given fields of a ticket",
		Long:  updateHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if cmd.Flags().Changed("sprint") || cmd.Flags().Changed("backlog") {
				return ticket.ErrUpdatePlacement
			}
			in := ticket.UpdateInput{
				Title:          changed(cmd, "title", title),
				Description:    changed(cmd, "description", description),
				Type:           changed(cmd, "type", typ),
				Status:         changed(cmd, "status", status),
				Priority:       changed(cmd, "priority", priority),
				MissionRank:    changed(cmd, "mission-rank", rank),
				Project:        changed(cmd, "project", project),
				Milestone:      changed(cmd, "milestone", milestone),
				Assignee:       changed(cmd, "assignee", assignee),
				Reviewer:       changed(cmd, "reviewer", reviewer),
				Label:          changed(cmd, "label", label),
				DueDate:        changed(cmd, "due-date", dueDate),
				RCA:            changed(cmd, "rca", rca),
				LessonsLearned: changed(cmd, "lessons-learned", lessons),
				Parent:         changed(cmd, "parent", parent),
				StoryPoints:    changedInt(cmd, "story-points", storyPoints),
				Percentage:     changedInt(cmd, "percentage", percentage),
			}
			return runUpdate(cmd, args, in)
		},
	}
	f := cmd.Flags()
	f.StringVar(&title, "title", "", "new title")
	f.StringVar(&description, "description", "", "new description")
	f.StringVar(&typ, "type", "", "new type: "+strings.Join(ticket.Types, ", "))
	f.StringVar(&status, "status", "", "new status: "+strings.Join(ticket.Statuses, ", "))
	f.StringVar(&priority, "priority", "", "new priority: LOWEST, LOW, MEDIUM, HIGH, HIGHEST (all types except INCIDENT) or P1, P2, P3 (INCIDENT)")
	f.StringVar(&rank, "mission-rank", "", "new mission rank: "+strings.Join(ticket.MissionRanks, ", "))
	f.StringVar(&project, "project", "", "project id")
	f.StringVar(&milestone, "milestone", "", `milestone id or name ("" detaches the ticket)`)
	f.StringVar(&assignee, "assignee", "", "assignee emails or user ids (comma separated)")
	f.StringVar(&reviewer, "reviewer", "", "reviewer emails or user ids (comma separated)")
	f.StringVar(&label, "label", "", "label ids (comma separated)")
	f.IntVar(&storyPoints, "story-points", 0, "story points (weight)")
	f.StringVar(&dueDate, "due-date", "", "due date (YYYY-MM-DD, local midnight, or an RFC 3339 timestamp)")
	f.IntVar(&percentage, "percentage", 0, "completion percentage")
	f.StringVar(&parent, "parent", "", `parent ticket key or id on the ticket's board ("" detaches the ticket from its parent)`)
	f.StringVar(&rca, "rca", "", "root cause analysis")
	f.StringVar(&lessons, "lessons-learned", "", "lessons learned")
	f.StringVar(&sprintRetired, "sprint", "", "removed: use 'oba kazan ticket move'")
	f.StringVar(&backlogRetired, "backlog", "", "removed: use 'oba kazan ticket move'")
	_ = f.MarkHidden("sprint")
	_ = f.MarkHidden("backlog")
	return cmd
}

func runUpdate(cmd *cobra.Command, args []string, in ticket.UpdateInput) error {
	ref, err := ticketArg(args)
	if err != nil {
		return err
	}
	prepared, err := ticket.PrepareUpdate(in)
	if err != nil {
		return err
	}
	warnRetiredType(cmd, prepared.RetiredType)
	c, r, err := start(cmd)
	if err != nil {
		return err
	}
	updated, err := ticket.Update(c, r, ref, prepared)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Updated ticket %s: %s\n", updated.Key, updated.Title)
	return nil
}
