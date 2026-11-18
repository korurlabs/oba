package cmd

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/install"
)

var installDryRun bool

var installCmd = &cobra.Command{
	Use:   "install",
	Short: "Start Claude Code through Kaya on this machine",
	Long: "Once per machine: sets the VS Code setting " + install.WrapperSetting + " to kaya-launch, defines " +
		"claude() { kaya launch claude \"$@\"; } in ~/.bashrc and ~/.zshrc, and registers the SessionStart, " +
		"SessionEnd, PreToolUse, UserPromptSubmit and PostToolUse hooks in the Claude Code user settings and " +
		"removes the ConfigChange hook an older kaya registered. Every file it " +
		"changes is first copied to <file>.kaya-backup-<time>; a second run changes nothing.",
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

func init() {
	installCmd.Flags().BoolVar(&installDryRun, "dry-run", false, "print the changes without writing anything")
	rootCmd.AddCommand(installCmd)
}

func installOptions() (install.Options, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return install.Options{}, fmt.Errorf("find the home directory: %w", err)
	}
	kaya, err := kayaPath()
	if err != nil {
		return install.Options{}, err
	}
	opts := install.Options{
		Home: home, ConfigHome: os.Getenv("XDG_CONFIG_HOME"), ClaudeConfigDir: os.Getenv("CLAUDE_CONFIG_DIR"),
		GOOS: runtime.GOOS, Shell: os.Getenv("SHELL"), KayaPath: kaya,
	}
	if launch, err := exec.LookPath(install.LaunchName); err == nil {
		if abs, err := filepath.Abs(launch); err == nil {
			opts.LaunchPath = abs
		}
	}
	return opts, nil
}

// kayaPath is the path the hooks and the shell function run: kaya as found on PATH, which
// survives an upgrade by Homebrew, else this binary.
func kayaPath() (string, error) {
	if path, err := exec.LookPath("kaya"); err == nil {
		if abs, err := filepath.Abs(path); err == nil {
			return abs, nil
		}
	}
	self, err := os.Executable()
	if err != nil {
		return "", fmt.Errorf("find the kaya binary: %w", err)
	}
	return self, nil
}
