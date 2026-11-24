// Package hook builds oba kaya hook, the Claude Code hooks of config delivery and of the
// recorder, which Kaya's managed settings drop-in registers; oba kaya install --managed renders it.
package hook

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/hook"
)

// NewCmd builds the oba kaya hook command.
func NewCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "hook <session-start|session-end|pre-tool-use|post-tool-use|user-prompt-submit|record>",
		Short: "Claude Code hooks of config delivery and capture (registered by Kaya's managed settings drop-in)",
		Long: "Read the hook event on stdin. session-start reports the session's config release and, when " +
			"Claude Code was not started by oba kaya launch, syncs first, adds the plan feedback a session that " +
			"ended on the branch left, and refreshes .kaya/lessons.md and names the lessons new since the last session; session-end activates a staged release once the checkout has no other " +
			"session; pre-tool-use refuses tools without a verified release and edits of delivered paths, and " +
			"runs the checkout's hook dispatcher when the project settings do not; post-tool-use and " +
			"user-prompt-submit add the plan feedback oba kaya watch fetched for the session; record queues the event for Kaya in a " +
			"checkout one of whose remotes is on the organization's repository list, as this machine last fetched it. " +
			"Only session-start calls Kaya (without oba kaya launch to sync, and " +
			"for at most 2 seconds each for plan feedback and for lessons). An error of the hook itself never blocks Claude Code. " +
			"Kaya's managed settings drop-in registers every one of them; oba kaya install --managed renders it.",
		Args:      cobra.ExactArgs(1),
		ValidArgs: hook.Names,
		RunE: func(cmd *cobra.Command, args []string) error {
			return hook.Run(cmd.Context(), args[0], cmd.InOrStdin(), cmd.OutOrStdout(), cmd.ErrOrStderr())
		},
	}
}
