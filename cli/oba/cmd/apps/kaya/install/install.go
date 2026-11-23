// Package install builds oba kaya install, which sets a machine up to start Claude Code through
// Kaya's config delivery.
package install

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/install"
)

// effectiveUID is the effective user id oba runs as.
var effectiveUID = os.Geteuid

// NewCmd builds the oba kaya install command.
func NewCmd() *cobra.Command {
	var installDryRun, installManaged, removeManaged bool
	var installCmd = &cobra.Command{
		Use:   "install",
		Short: "Start Claude Code through Kaya on this machine",
		Long: "Once per machine: sets the VS Code setting " + install.WrapperSetting + " to oba-kaya-launch, defines " +
			"claude() { oba kaya launch claude \"$@\"; } in ~/.bashrc and ~/.zshrc, and replaces the claude() block " +
			"of the former kaya CLI and the ConfigChange hook of an older version. Every file it changes is first " +
			"copied to <file>.oba-backup-<time>; a second run changes nothing.\n\n" +
			"It sets hook.kaya-pre-commit and hook.kaya-post-checkout in your global git config: git 2.54 or newer " +
			"runs them in every repository, beside the repository's own hooks, as the oba path found now with " +
			"kaya git-hook <event>; they act only in a checkout Kaya delivers to. It also refreshes the cached " +
			"list of the organization's repositories with the machine token; a failure there is a warning.\n\n" +
			"Kaya's hooks, its session capture and its status line come from the managed settings drop-in " +
			install.DropInName + " of Claude Code, which no user or project setting can override. --managed renders " +
			"it into a new file in your temp folder and prints the one sudo line that installs it with /bin/mkdir and " +
			"/usr/bin/install; --remove-managed prints the sudo line that removes it. Kaya's hooks that an earlier " +
			"install registered in the Claude Code user settings stay there until oba kaya doctor --probe has proved " +
			"the drop-in in force; the next install then removes them.\n\n" +
			"Run it as your own user: it refuses to run as root, and oba never writes the managed settings folder.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := install.RefuseRoot(effectiveUID()); err != nil {
				return err
			}
			opts, err := installOptions()
			if err != nil {
				return err
			}
			w := cmd.OutOrStdout()
			if removeManaged {
				_, _ = fmt.Fprintln(w, "Remove the drop-in as root with this line, which runs only /bin/rm:")
				_, _ = fmt.Fprintln(w, install.RemoveManagedStep(opts.ManagedDir))
				_, _ = fmt.Fprintln(w, "Claude Code sessions started afterwards no longer get Kaya's hooks and status line from it.")
				return nil
			}
			if installManaged {
				return printManaged(w, opts, installDryRun)
			}
			plan, err := install.Build(opts)
			if err != nil {
				return err
			}
			gitPlan, err := install.BuildGitHooks(opts)
			if err != nil {
				return err
			}
			for _, item := range append(plan.Unchanged, gitPlan.Unchanged...) {
				_, _ = fmt.Fprintln(w, "Already set: "+item)
			}
			verb := "Changed"
			if installDryRun {
				verb = "Would change"
			}
			for _, change := range plan.Changes {
				_, _ = fmt.Fprintf(w, "%s %s: %s\n", verb, change.Path, change.Description)
			}
			for _, setting := range gitPlan.Settings {
				_, _ = fmt.Fprintf(w, "%s the global git config: set %s to %s\n", verb, setting.Key, setting.Value)
			}
			for _, item := range append(plan.Manual, gitPlan.Manual...) {
				_, _ = fmt.Fprintln(w, "By hand: "+item)
			}
			if installDryRun {
				return nil
			}
			if len(plan.Changes) > 0 {
				backups, err := install.Apply(plan.Changes, time.Now())
				for _, backup := range backups {
					_, _ = fmt.Fprintln(w, "Backup: "+backup)
				}
				if err != nil {
					return err
				}
			}
			if err := install.ApplyGitHooks(opts, gitPlan.Settings); err != nil {
				return err
			}
			refreshRepositoryList(cmd.Context(), w, cmd.ErrOrStderr())
			if len(plan.Changes) > 0 {
				_, _ = fmt.Fprintln(w, "Open a new terminal and restart VS Code to start Claude Code through Kaya.")
			}
			return nil
		},
	}

	installCmd.Flags().BoolVar(&installDryRun, "dry-run", false, "print the changes, or with --managed the drop-in, without writing anything")
	installCmd.Flags().BoolVar(&installManaged, "managed", false, "render the managed settings drop-in into a temp file and print the sudo line that installs it")
	installCmd.Flags().BoolVar(&removeManaged, "remove-managed", false, "print the sudo line that removes the managed settings drop-in")
	installCmd.MarkFlagsMutuallyExclusive("managed", "remove-managed")
	return installCmd
}

// repositoryListTimeout bounds the refresh of the repository list, the machine token lookup
// included.
const repositoryListTimeout = 60 * time.Second

// refreshRepositoryList refreshes the cached list of the organization's repositories, by which
// the hooks recognise a checkout. A failure is a warning: the machine stays installed, and every
// sync refreshes the list again.
func refreshRepositoryList(ctx context.Context, w, stderr io.Writer) {
	list, err := delivery.RefreshRepositories(ctx, repositoryListTimeout)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "Warning: "+err.Error()+"; oba kaya sync in a checkout refreshes it again")
		return
	}
	_, _ = fmt.Fprintf(w, "Refreshed the repository list: %d repositories of the organization.\n", len(list.Remotes))
}

// printManaged renders the drop-in into a new file in the temp folder and prints the root step
// that installs it, or with dryRun prints the drop-in and writes nothing.
func printManaged(w io.Writer, opts install.Options, dryRun bool) error {
	plan, err := install.BuildManaged(opts)
	if err != nil {
		return err
	}
	if dryRun {
		_, _ = fmt.Fprintf(w, "Would install %s:\n", plan.Path)
		_, _ = w.Write(plan.Content)
		return nil
	}
	staged, err := plan.Stage(os.TempDir())
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(w, "Rendered %s into %s.\n", plan.Path, staged)
	_, _ = fmt.Fprintln(w, "Install it as root with this line, which runs only /bin/mkdir and /usr/bin/install:")
	_, _ = fmt.Fprintln(w, plan.InstallStep(staged))
	_, _ = fmt.Fprintln(w, "Then run oba kaya install, and oba kaya doctor --probe in a connected checkout.")
	return nil
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
	managedDir, err := install.ManagedDir(runtime.GOOS)
	if err != nil {
		return install.Options{}, err
	}
	inForce, err := install.DefaultInForcePath()
	if err != nil {
		return install.Options{}, err
	}
	opts := install.Options{
		Home: home, ConfigHome: os.Getenv("XDG_CONFIG_HOME"), ClaudeConfigDir: os.Getenv("CLAUDE_CONFIG_DIR"),
		GOOS: runtime.GOOS, Shell: os.Getenv("SHELL"), ObaPath: oba, ManagedDir: managedDir, InForcePath: inForce,
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
