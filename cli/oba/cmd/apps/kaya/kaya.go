// Package kaya builds the oba kaya command and adds the commands of each Kaya domain, one package
// per domain under cmd/apps/kaya.
package kaya

import (
	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/githook"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/hook"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/install"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/lessons"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/propose"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/seed"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/sender"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/signingkey"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/statusline"
	installer "github.com/korurlabs/oba-cli/internal/apps/kaya/install"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
)

// NewCmd builds the oba kaya command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "kaya",
		Short: "Kaya: the agent config of Claude Code, delivered as signed releases",
		Long: "Kaya keeps the agent config of the organization's repositories (rules, skills, hooks and " +
			"settings of Claude Code) and delivers it to each checkout as a signed release. 'oba kaya seed' " +
			"imports it, 'oba kaya install' sets a machine up, 'oba kaya launch' and the hooks put the " +
			"release in place for every session, and 'oba kaya propose' sends a change of it for review.\n\n" +
			"seed and signing-keys act as you, in the current organization, with the sign-in of 'oba login'. " +
			"The commands that run in sessions and in the background (sync, refresh, launch, watch, hook, " +
			"sender, lessons, propose) send the machine token instead: " + machine.EnvToken + ", else the one " +
			"in your Kagi personal environment. They call OBA_API_URL, else the production API.",
	}
	cmd.AddCommand(delivery.NewCmds()...)
	cmd.AddCommand(
		hook.NewCmd(),
		githook.NewCmd(),
		statusline.NewCmd(),
		install.NewCmd(),
		seed.NewCmd(),
		propose.NewCmd(),
		lessons.NewCmd(),
		sender.NewCmd(),
		signingkey.NewCmd(),
	)
	return cmd
}

// LaunchName is the name under which the oba binary is the wrapper the VS Code extension starts
// Claude Code through; 'oba kaya install' links it to oba.
const LaunchName = installer.LaunchName

// ExecuteLaunch runs oba as LaunchName.
func ExecuteLaunch(args []string) {
	delivery.ExecuteLaunch(args)
}
