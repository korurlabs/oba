package delivery

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
)

func newLaunchCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "launch <command> [args...]",
		Short: "Put the checkout's signed config release in place, then run the command",
		Long: "Syncs the checkout of the working directory with Kaya (3 s at most, then the cached release), " +
			"starts oba kaya watch for it and replaces itself with the command, usually claude. While another " +
			"session of the checkout runs, the active release stays and a newer one waits (state STALE). " +
			"A checkout Kaya does not deliver runs the command unchanged.",
		Args:               cobra.MinimumNArgs(1),
		DisableFlagParsing: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if args[0] == "--help" || args[0] == "-h" {
				return cmd.Help()
			}
			return delivery.Launch(cmd.Context(), args, cmd.ErrOrStderr())
		},
	}
}

// ExecuteLaunch runs oba as oba-kaya-launch, the wrapper the VS Code extension starts Claude Code
// through.
func ExecuteLaunch(args []string) {
	delivery.ExecuteLaunch(args)
}
