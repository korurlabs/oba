// Package cmd holds the oba root command. The platform commands (login, logout, org, whoami) live in
// cmd/shrine and each app's commands in cmd/apps/<app>; both are added here, and the command
// path picks the app (oba otak ...).
package cmd

import (
	"errors"
	"fmt"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"github.com/spf13/pflag"

	"github.com/korurlabs/oba-cli/cmd/apps/kalkan"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya"
	"github.com/korurlabs/oba-cli/cmd/apps/kazan"
	"github.com/korurlabs/oba-cli/cmd/apps/otak"
	"github.com/korurlabs/oba-cli/cmd/apps/ulak"
	"github.com/korurlabs/oba-cli/cmd/shrine"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
)

// SetVersion sets the version main received through ldflags.
func SetVersion(v string) {
	rootCmd.Version = v
}

var rootCmd = &cobra.Command{
	Use:   "oba",
	Short: "Oba CLI: the Oba apps from your terminal",
	Long: "The Oba CLI signs you in to Oba, chooses the organization you work in and works with the Oba apps, one command group per app.\n\n" +
		"Set " + config.EnvToken + " to call the API with that bearer token instead of the stored session, as scripts and agents do.",
	SilenceUsage:  true,
	SilenceErrors: true,
}

func init() {
	addGlobalFlags(rootCmd)
	rootCmd.AddCommand(shrine.Commands()...)
	rootCmd.AddCommand(shrine.NewWhoamiCmd())
	rootCmd.AddCommand(otak.NewCmd())
	rootCmd.AddCommand(kazan.NewCmd())
	rootCmd.AddCommand(ulak.NewCmd())
	rootCmd.AddCommand(kaya.NewCmd())
	rootCmd.AddCommand(kalkan.NewCmd())
	for _, child := range rootCmd.Commands() {
		rejectUnknownSubcommands(child)
	}
}

// rejectUnknownSubcommands makes cmd and every group below it fail on an argument that names no
// subcommand. Cobra checks this only on the root: a group without a Run of its own prints its help
// and exits 0 for "oba kazan bogus", so a script with a mistyped or renamed command would read
// help text as success. The root keeps cobra's own check.
func rejectUnknownSubcommands(cmd *cobra.Command) {
	if !cmd.HasSubCommands() {
		return
	}
	if cmd.Run == nil && cmd.RunE == nil {
		cmd.Args = unknownSubcommand
		// pflag.ErrHelp makes cobra print the help to stdout after the global flag hooks have
		// given stdout back, as it does for a group that is not runnable, and exit 0.
		cmd.RunE = func(*cobra.Command, []string) error { return pflag.ErrHelp }
	}
	for _, child := range cmd.Commands() {
		rejectUnknownSubcommands(child)
	}
}

// unknownSubcommand rejects any positional argument of a group with the error cobra gives on the
// root. Cobra validates the arguments before the global flag hooks run, so --json does not take
// over stdout first.
func unknownSubcommand(cmd *cobra.Command, args []string) error {
	if len(args) == 0 {
		return nil
	}
	return fmt.Errorf("unknown command %q for %q%s", args[0], cmd.CommandPath(), suggestions(cmd, args[0]))
}

// suggestions repeats cobra's "Did you mean this?" hint for a mistyped subcommand.
func suggestions(cmd *cobra.Command, typed string) string {
	if cmd.DisableSuggestions {
		return ""
	}
	if cmd.SuggestionsMinimumDistance <= 0 {
		cmd.SuggestionsMinimumDistance = 2
	}
	found := cmd.SuggestionsFor(typed)
	if len(found) == 0 {
		return ""
	}
	var b strings.Builder
	b.WriteString("\n\nDid you mean this?\n")
	for _, s := range found {
		_, _ = fmt.Fprintf(&b, "\t%v\n", s)
	}
	return b.String()
}

// Execute runs the command line. An error is printed as "Error: <message>" to stderr and ends
// the process with 2 for an API 404, 3 for an API 403 and 1 otherwise. An exitcode.Error ends it
// with its own code and prints nothing, since the command has printed its outcome.
func Execute() {
	err := rootCmd.Execute()
	if err == nil {
		return
	}
	var exitErr *exitcode.Error
	if errors.As(err, &exitErr) {
		os.Exit(exitErr.Code)
	}
	_, _ = fmt.Fprintln(os.Stderr, "Error:", err)
	os.Exit(client.ExitCode(err))
}
