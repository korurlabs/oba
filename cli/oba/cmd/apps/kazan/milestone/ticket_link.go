package milestone

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/milestone"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

const ticketsHelp = " A ticket is a key, such as OPS-42, whose prefix names its board, so tickets of related " +
	"boards go in one call, or an id.\n\n"

func newLinkCmd() *cobra.Command {
	return newTicketsCmd("link <milestone>", "Attach tickets to a milestone",
		"Attaches the tickets of --tickets to a milestone."+ticketsHelp+
			"  oba kazan milestone link \"Public beta\" --tickets KAGI-12,KAGI-15\n\n"+milestoneRefHelp,
		milestone.LinkTickets, "Linked", "to")
}

func newUnlinkCmd() *cobra.Command {
	return newTicketsCmd("unlink <milestone>", "Detach tickets from a milestone",
		"Detaches the tickets of --tickets from a milestone."+ticketsHelp+
			"  oba kazan milestone unlink \"Public beta\" --tickets KAGI-12\n\n"+milestoneRefHelp,
		milestone.UnlinkTickets, "Unlinked", "from")
}

// newTicketsCmd builds link or unlink; done and preposition word the message.
func newTicketsCmd(use, short, long string,
	change func(*client.Client, string, milestone.TicketsChange) (client.Milestone, int, error),
	done, preposition string) *cobra.Command {
	var ticketsFlag string
	cmd := &cobra.Command{
		Use:   use,
		Short: short,
		Long:  long,
		RunE: func(cmd *cobra.Command, args []string) error {
			c, boardID, err := selectBoard(cmd)
			if err != nil {
				return err
			}
			tickets := ticket.NewResolver(c, boardflag.Value(cmd), cmd.ErrOrStderr())
			m, count, err := change(c, boardID, milestone.TicketsChange{
				Milestone: milestoneArg(args), Tickets: ticketsFlag, ResolveTicket: tickets.TicketID,
			})
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "%s %d tickets %s milestone %s (%d/%d done)\n",
				done, count, preposition, milestone.Label(m), m.DoneCount, m.TotalCount)
			return nil
		},
	}
	cmd.Flags().StringVar(&ticketsFlag, "tickets", "", "comma separated ticket keys (e.g. KAGI-1,KAGI-2) or ids (required)")
	return cmd
}
