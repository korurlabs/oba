// Package githook builds oba kaya git-hook, the git hooks of config delivery that oba kaya install
// registers in the user's global git config.
package githook

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/githook"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
)

// NewCmd builds the hidden oba kaya git-hook command.
func NewCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "git-hook <pre-commit|post-checkout> [git's hook arguments]",
		Short: "git hooks of config delivery (registered by oba kaya install)",
		Long: "Run by git from the hook.kaya-pre-commit and hook.kaya-post-checkout entries of the global git " +
			"config, and only acting in a checkout Kaya delivers to. pre-commit refuses a commit that stages a " +
			"delivered path or a path below .agents/, .claude/ or .kaya/, and names the git rm --cached line " +
			"that takes it out of git. post-checkout puts the active release in place again after a branch switch, " +
			"and delivers a new worktree: from Kaya within 3 seconds, else the release the main worktree has " +
			"active. Only the refusal of pre-commit stops git; every other failure is a line on stderr.",
		Hidden: true,
		Args:   cobra.MinimumNArgs(1),
		// git's hook arguments are passed on as they are, never read as flags.
		DisableFlagParsing: true,
		ValidArgs:          githook.Events,
		RunE: func(cmd *cobra.Command, args []string) error {
			if code := githook.Run(cmd.Context(), args[0], args[1:], cmd.ErrOrStderr()); code != 0 {
				return &exitcode.Error{Code: code}
			}
			return nil
		},
	}
}
