package shrine

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// NewWhoamiCmd builds oba whoami, which shows the signed-in user and the organization the
// commands act in.
func NewWhoamiCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "whoami",
		Short: "Show the signed-in user and the current organization",
		Long: "Shows the signed-in user (email, name, id), the organization the commands act in and every " +
			"organization the user belongs to, with the user's role in it.",
		Args: cobra.NoArgs,
		RunE: runWhoami,
	}
}

func runWhoami(cmd *cobra.Command, _ []string) error {
	creds, user, err := session.CurrentUser()
	if err != nil {
		return err
	}
	current, err := session.CurrentOrgID(creds.DefaultOrgID, user.Organizations)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	_, _ = fmt.Fprintf(out, "Email: %s\n", guardrail.OneLine(user.Email))
	if user.Profile != nil {
		if name := strings.TrimSpace(user.Profile.FirstName + " " + user.Profile.LastName); name != "" {
			_, _ = fmt.Fprintf(out, "Name:  %s\n", guardrail.OneLine(name))
		}
	}
	_, _ = fmt.Fprintf(out, "ID:    %s\n", user.ID)
	if current != "" {
		label := current
		for _, org := range user.Organizations {
			if org.ID == current {
				label = fmt.Sprintf("%s (%s)", guardrail.OneLine(org.Name), org.ID)
			}
		}
		_, _ = fmt.Fprintf(out, "Org:   %s\n", label)
	}
	if len(user.Organizations) > 0 {
		_, _ = fmt.Fprintln(out, "Organizations:")
		for _, org := range user.Organizations {
			_, _ = fmt.Fprintf(out, "  - %s (%s, role: %s)\n", guardrail.OneLine(org.Name), org.ID, org.Role)
		}
	}
	return nil
}
