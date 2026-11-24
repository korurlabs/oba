// Package statusline builds oba kaya statusline, the Claude Code status line that captures the
// usage limits for Kaya and prints the developer's own status line.
package statusline

import (
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/statusline"
)

// NewCmd builds the oba kaya statusline command.
func NewCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "statusline",
		Short: "Claude Code status line: captures the usage limits for Kaya and prints your own status line",
		Long: "Reads the session JSON Claude Code passes to its status line command. When rate_limits holds a " +
			"five_hour or seven_day window, writes it to the queue's usage file, which oba kaya sender adds to the " +
			"session's next batch. Then prints the output of your own statusLine command from the Claude Code " +
			"user settings, run with the same input and " + statusline.Nested + "=1 for at most 2 seconds, or " +
			"the default line `<model> | 5h 23% | 7d 41%`. Registered by Kaya's managed settings drop-in, which " +
			"oba kaya install --managed renders. A failure is " +
			"logged to recorder.log of the state directory and never fails the line.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			statusline.Run(cmd.Context(), cmd.InOrStdin(), time.Now(), cmd.OutOrStdout(), cmd.ErrOrStderr())
			return nil
		},
	}
}
