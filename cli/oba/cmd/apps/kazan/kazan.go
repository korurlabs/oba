// Package kazan builds the oba kazan command and adds the commands of each Kazan domain, one
// package per domain under cmd/apps/kazan.
package kazan

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/attachment"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/backlog"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/board"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/comment"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/cyberscore"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/label"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/member"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/milestone"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/project"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/roadmap"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/sprint"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan/updates"
)

// NewCmd builds the oba kazan command with its subcommands and the persistent --board flag.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "kazan",
		Short: "Kazan: the boards, tickets and sprints of your organization",
		Long: "Kazan plans the work of the current organization on boards: tickets, sprints, milestones, the " +
			"roadmap, the daily updates of its members and the pull requests shared in its PR channel.\n\n" +
			"Commands that act on one board take it from --board, else from the default board chosen with " +
			"'oba kazan board use <board>' for the current organization. The cyberscore belongs to the " +
			"organization, not to a board: 'oba kazan cyberscore' reads it and manages its checks, and ignores --board.",
	}
	boardflag.Add(cmd)
	cmd.AddCommand(
		board.NewCmd(),
		ticket.NewCmd(),
		comment.NewCmd(),
		attachment.NewCmd(),
		label.NewCmd(),
		backlog.NewCmd(),
		project.NewCmd(),
		member.NewCmd(),
		sprint.NewCmd(),
		milestone.NewCmd(),
		roadmap.NewCmd(),
		updates.NewCmd(),
		pr.NewCmd(),
		cyberscore.NewCmd(),
	)
	return cmd
}
