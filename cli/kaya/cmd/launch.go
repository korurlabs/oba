package cmd

import (
	"context"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/delivery"
)

// envOffline carries why the launcher's sync did not reach Kaya (or did not take its newest
// release) to the SessionStart hook, which tells the agent that the session runs on the cache.
const envOffline = "KAYA_OFFLINE"

// maxOfflineReason bounds the reason passed in envOffline, which ends up in the session context.
const maxOfflineReason = 300

var launchCmd = &cobra.Command{
	Use:   "launch <command> [args...]",
	Short: "Put the checkout's signed config release in place, then run the command",
	Long: "Syncs the checkout of the working directory with Kaya (3 s at most, then the cached release), " +
		"starts kaya watch for it and replaces itself with the command, usually claude. While another " +
		"session of the checkout runs, the active release stays and a newer one waits (state STALE). " +
		"A checkout Kaya does not deliver runs the command unchanged.",
	Args:               cobra.MinimumNArgs(1),
	DisableFlagParsing: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		if args[0] == "--help" || args[0] == "-h" {
			return cmd.Help()
		}
		return launch(cmd.Context(), args, cmd.ErrOrStderr())
	},
}

func init() {
	rootCmd.AddCommand(launchCmd)
}

// ExecuteLaunch runs kaya as kaya-launch, the wrapper the VS Code extension starts Claude Code
// through: the extension passes the path of its bundled Claude binary first, followed by the
// arguments. Without such a path the wrapper runs claude from PATH with the arguments.
func ExecuteLaunch(args []string) {
	command := append([]string{"claude"}, args...)
	if len(args) > 0 && isExecutable(args[0]) {
		command = args
	}
	if err := launch(context.Background(), command, os.Stderr); err != nil {
		_, _ = fmt.Fprintln(os.Stderr, "kaya-launch:", err)
		os.Exit(1)
	}
}

func isExecutable(path string) bool {
	if !filepath.IsAbs(path) {
		return false
	}
	info, err := os.Stat(path)
	return err == nil && info.Mode().IsRegular() && info.Mode().Perm()&0o111 != 0
}

// launch syncs the checkout and execs the command. A failure of the sync never stops the
// command: the session starts on what the checkout holds and the guard hook refuses tools when
// no verified release is there.
func launch(ctx context.Context, command []string, stderr io.Writer) error {
	path, err := exec.LookPath(command[0])
	if err != nil {
		return fmt.Errorf("find %s: %w", command[0], err)
	}
	env := withoutSessionVars(os.Environ())
	checkout, err := currentCheckout()
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "kaya:", err)
	} else if checkout.Kind == delivery.Delivered {
		env = append(env, prepareSession(ctx, checkout, stderr)...)
	}
	// #nosec G204 -- the user's own command, as typed or as the editor passed it
	return syscall.Exec(path, command, env)
}

// prepareSession syncs the checkout, registers this process as a live session and starts the
// watcher. It returns the variables Claude Code and its hooks read.
func prepareSession(ctx context.Context, checkout *delivery.Checkout, stderr io.Writer) []string {
	vars := []string{envLaunched + "=1", envCheckout + "=" + checkout.Root}
	state := delivery.StateUnverified
	release := 0
	var offline error
	d, err := newDeliverer(ctx, checkout, true)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "kaya:", err)
		return append(vars, envReleaseState+"="+state)
	}
	// The launcher's own process becomes Claude Code by exec, so it never counts as another session.
	d.ExcludePID = os.Getpid()
	lockCtx, cancel := lockContext(ctx)
	defer cancel()
	out, err := d.Sync(lockCtx, delivery.Pin)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "kaya:", err)
	} else {
		state, release, offline = out.State, out.Active, out.Offline
		saved, loadErr := checkout.LoadState()
		if loadErr != nil {
			_, _ = fmt.Fprintln(stderr, "kaya:", loadErr)
		}
		printOutcome(stderr, "kaya: ", checkout, out, saved)
	}
	session := delivery.Session{PID: os.Getpid(), Release: release, State: state, DeliveredBy: delivery.DeliveredByLauncher}
	if err := checkout.RegisterSession(session); err != nil {
		_, _ = fmt.Fprintln(stderr, "kaya:", err)
	}
	if err := startWatcher(checkout); err != nil {
		_, _ = fmt.Fprintln(stderr, "kaya: the watcher did not start:", err)
	}
	vars = append(vars, envReleaseState+"="+state)
	if release > 0 {
		vars = append(vars, envRelease+"="+strconv.Itoa(release))
	}
	if offline != nil {
		vars = append(vars, envOffline+"="+offlineReason(offline))
	}
	return vars
}

// offlineReason is err on one line, short enough for the session context, and never empty, since
// an empty variable would read as online.
func offlineReason(err error) string {
	reason := strings.Join(strings.Fields(err.Error()), " ")
	if len(reason) > maxOfflineReason {
		reason = reason[:maxOfflineReason]
	}
	if reason == "" {
		reason = "unknown"
	}
	return reason
}

// startWatcher starts kaya watch for the checkout in its own session, detached from the
// terminal, logging to .kaya/watch.log. A watcher already running for the checkout makes the new
// one exit at once.
func startWatcher(checkout *delivery.Checkout) error {
	self, err := os.Executable()
	if err != nil {
		return fmt.Errorf("find the kaya binary: %w", err)
	}
	logPath := filepath.Join(checkout.Root, ".kaya", "watch.log")
	logFile, err := os.OpenFile(logPath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600) // #nosec G304 -- fixed path below the checkout's .kaya
	if err != nil {
		return fmt.Errorf("open %s: %w", logPath, err)
	}
	defer func() { _ = logFile.Close() }()
	watch := exec.Command(self, "watch", "--checkout", checkout.Root) // #nosec G204 -- this binary with fixed arguments
	watch.Dir = checkout.Root
	watch.Stdout, watch.Stderr = logFile, logFile
	watch.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
	if err := watch.Start(); err != nil {
		return fmt.Errorf("start kaya watch: %w", err)
	}
	return watch.Process.Release()
}

// withoutSessionVars drops the variables a launcher set for an enclosing session, so a command
// started from inside one never reports that session's release.
func withoutSessionVars(env []string) []string {
	kept := make([]string, 0, len(env))
	for _, entry := range env {
		name, _, _ := strings.Cut(entry, "=")
		switch name {
		case envLaunched, envCheckout, envRelease, envReleaseState, envOffline:
			continue
		}
		kept = append(kept, entry)
	}
	return kept
}
