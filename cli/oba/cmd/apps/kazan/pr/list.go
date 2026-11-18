package pr

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

func newListCmd() *cobra.Command {
	var status, author string
	filter := pr.ListFilter{}
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List the PR channel's shares, newest first",
		Long: `List the shares of your organization's PR channel, newest first; an edit does
not move a share. Each share shows the pull request, the team's status, who
shared it and when, the title, the reviewers with their approvals, the related
tickets, the GitHub link, the link that opens its card in Kazan (left out when
the card is gone, or when ` + pr.EnvWebURL + ` does not name the origin of the
Kazan web app) and the share ID.

--status takes statuses, comma-separated. --author takes "me", emails or user
UUIDs, comma-separated. --pending keeps the shares that wait for your approval:
you are a reviewer, have not approved, and the status is not Merged.`,
		Example: `  oba kazan pr list
  oba kazan pr list --pending
  oba kazan pr list --status in-review,discuss --author me
  oba kazan pr list --page 2 --amount 10 --json`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := filter.CheckPaging(); err != nil {
				return err
			}
			filter.Statuses = nil
			for _, raw := range userinput.SplitCSV(status) {
				s, err := pr.ParseStatus(raw)
				if err != nil {
					return fmt.Errorf("--status: %w", err)
				}
				filter.Statuses = append(filter.Statuses, s)
			}
			c, err := session.OrganizationClient()
			if err != nil {
				return err
			}
			filter.AuthorIDs = nil
			if refs := userinput.SplitCSV(author); len(refs) > 0 {
				if filter.AuthorIDs, err = organization.ResolveAuthorIDs(c, refs, meFunc()); err != nil {
					return fmt.Errorf("--author: %w", err)
				}
			}
			page, err := pr.List(c, filter)
			if err != nil {
				return err
			}
			pr.PrintList(cmd.OutOrStdout(), page)
			return nil
		},
	}
	f := cmd.Flags()
	f.StringVar(&status, "status", "", "Statuses (comma-separated): "+strings.Join(pr.Statuses, ", "))
	f.StringVar(&author, "author", "", "Authors: me, emails or user UUIDs (comma-separated)")
	f.BoolVar(&filter.Pending, "pending", false, "Only shares waiting for your approval")
	f.IntVar(&filter.Page, "page", pr.DefaultSharePage, "Page number, from 1")
	f.IntVar(&filter.Amount, "amount", pr.DefaultShareCount, fmt.Sprintf("Shares per page, at most %d", pr.MaxSharePageSize))
	return cmd
}
