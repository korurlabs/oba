package cmd

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/httplog"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	flagJSON  = "json"
	flagOrg   = "org"
	flagDebug = "debug"
)

// addGlobalFlags adds the flags every command takes.
func addGlobalFlags(root *cobra.Command) {
	flags := root.PersistentFlags()
	flags.Bool(flagJSON, false, "print only the data the API returned, as JSON on stdout; messages and prompts go to stderr")
	flags.String(flagOrg, "", "act in this organization, by name or id, for this command only; the chosen one stays stored")
	flags.Bool(flagDebug, false, "log every HTTP request and response to stderr, credentials redacted")
	root.PersistentPreRunE = applyGlobalFlags
	root.PersistentPostRunE = finishGlobalFlags
	cobra.OnFinalize(func() { finishFailedRun(root) })
}

// applyGlobalFlags turns the global flags into the state of this run. Each value is set on every
// run, also when its flag is absent, so that nothing carries over from an earlier run in the
// same process. With --json, everything the command writes for people goes to stderr, and stdout
// carries only the JSON that finishGlobalFlags writes.
func applyGlobalFlags(cmd *cobra.Command, _ []string) error {
	root := cmd.Root()
	flags := root.PersistentFlags()
	jsonOn, err := flags.GetBool(flagJSON)
	if err != nil {
		return err
	}
	org, err := flags.GetString(flagOrg)
	if err != nil {
		return err
	}
	debug, err := flags.GetBool(flagDebug)
	if err != nil {
		return err
	}

	session.UseOrganization(org)
	if debug {
		httplog.Enable(root.ErrOrStderr())
	} else {
		httplog.Enable(nil)
	}
	if jsonOn {
		jsonout.Start(root.OutOrStdout())
		root.SetOut(root.ErrOrStderr())
	} else {
		jsonout.Stop()
	}
	return nil
}

// finishGlobalFlags writes the --json result once the command succeeded and gives stdout back.
// A failed command writes no JSON: its error goes to stderr and its exit status tells a script.
// Cobra skips this hook when the command fails; finishFailedRun handles that case.
func finishGlobalFlags(cmd *cobra.Command, _ []string) error {
	stdout := jsonout.Destination()
	if stdout == nil {
		return nil
	}
	cmd.Root().SetOut(stdout)
	err := jsonout.Flush()
	jsonout.Stop()
	return err
}

// finishFailedRun runs after every run and acts only on one that failed with --json, because a
// run that succeeded has already flushed and stopped. It writes the result only when the command
// kept it on failure: a command that did part of its work, such as a bulk delete where one
// ticket failed, so that a script learns what was done although the exit status is non-zero.
func finishFailedRun(root *cobra.Command) {
	stdout := jsonout.Destination()
	if stdout == nil {
		return
	}
	root.SetOut(stdout)
	if jsonout.KeptOnFailure() {
		if err := jsonout.Flush(); err != nil {
			// The command's own error ends the run and is printed after this; the flush error
			// must not replace it, so it goes to stderr next to it.
			_, _ = fmt.Fprintln(root.ErrOrStderr(), "Error:", err)
		}
	}
	jsonout.Stop()
}
