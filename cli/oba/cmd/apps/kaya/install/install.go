// Package install builds oba kaya install, which sets a machine up to start Claude Code through
// Kaya's config delivery.
package install

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/install"
)

// NewCmd builds the oba kaya install command.
func NewCmd() *cobra.Command {
	var installDryRun bool
	var installCmd = &cobra.Command{
		Use:   "install",
		Short: "Start Claude Code through Kaya on this machine",
		Long: "Once per machine: sets the VS Code setting " + install.WrapperSetting + " to oba-kaya-launch, defines " +
			"claude() { oba kaya launch claude \"$@\"; } in ~/.bashrc and ~/.zshrc, and registers the SessionStart, " +
			"SessionEnd, PreToolUse, UserPromptSubmit and PostToolUse hooks in the Claude Code user settings and " +
			"replaces what the former kaya CLI registered there (its hooks, its claude() block and the ConfigChange " +
			"hook of an older version). Every file it changes is first copied to <file>.oba-backup-<time>; a " +
			"second run changes nothing.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			opts, err := installOptions()
			if err != nil {
				return err
			}
			plan, err := install.Build(opts)
			if err != nil {
				return err
			}
			w := cmd.OutOrStdout()
			for _, item := range plan.Unchanged {
				_, _ = fmt.Fprintln(w, "Already set: "+item)
			}
			verb := "Changed"
			if installDryRun {
				verb = "Would change"
			}
			for _, change := range plan.Changes {
				_, _ = fmt.Fprintf(w, "%s %s: %s\n", verb, change.Path, change.Description)
			}
			for _, item := range plan.Manual {
				_, _ = fmt.Fprintln(w, "By hand: "+item)
			}
			if installDryRun || len(plan.Changes) == 0 {
				return nil
			}
			backups, err := install.Apply(plan.Changes, time.Now())
			for _, backup := range backups {
				_, _ = fmt.Fprintln(w, "Backup: "+backup)
			}
			if err != nil {
				return err
			}
			_, _ = fmt.Fprintln(w, "Open a new terminal and restart VS Code to start Claude Code through Kaya.")
			return nil
		},
	}

	installCmd.Flags().BoolVar(&installDryRun, "dry-run", false, "print the changes without writing anything")
	return installCmd
}

func installOptions() (install.Options, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return install.Options{}, fmt.Errorf("find the home directory: %w", err)
	}
	oba, err := obaPath()
	if err != nil {
		return install.Options{}, err
	}
	opts := install.Options{
		Home: home, ConfigHome: os.Getenv("XDG_CONFIG_HOME"), ClaudeConfigDir: os.Getenv("CLAUDE_CONFIG_DIR"),
		GOOS: runtime.GOOS, Shell: os.Getenv("SHELL"), ObaPath: oba,
	}
	if launch, err := exec.LookPath(install.LaunchName); err == nil {
		if abs, err := filepath.Abs(launch); err == nil {
			opts.LaunchPath = abs
		}
	}
	return opts, nil
}

// obaPath is the path the hooks and the shell function run: oba as found on PATH, which
// survives an upgrade by Homebrew, else this binary.
func obaPath() (string, error) {
	if path, err := exec.LookPath("oba"); err == nil {
		if abs, err := filepath.Abs(path); err == nil {
			return abs, nil
		}
	}
	self, err := os.Executable()
	if err != nil {
		return "", fmt.Errorf("find the oba binary: %w", err)
	}
	return self, nil
}
