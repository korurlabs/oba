package delivery

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
)

// maxOfflineReason bounds the reason passed in EnvOffline, which ends up in the session context.
const maxOfflineReason = 300

// ExecuteLaunch runs kaya as oba-kaya-launch, the wrapper the VS Code extension starts Claude Code
// through: the extension passes the path of its bundled Claude binary first, followed by the
// arguments. Without such a path the wrapper runs claude from PATH with the arguments.
func ExecuteLaunch(args []string) {
	command := append([]string{"claude"}, args...)
	if len(args) > 0 && isExecutable(args[0]) {
		command = args
	}
	if err := Launch(context.Background(), command, os.Stderr); err != nil {
		_, _ = fmt.Fprintln(os.Stderr, "oba-kaya-launch:", err)
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

// Launch syncs the checkout of the working directory and execs the command. A failure of the sync never stops the
// command: the session starts on what the checkout holds and the guard hook refuses tools when
// no verified release is there.
func Launch(ctx context.Context, command []string, stderr io.Writer) error {
	path, err := exec.LookPath(command[0])
	if err != nil {
		return fmt.Errorf("find %s: %w", command[0], err)
	}
	env := withoutSessionVars(os.Environ())
	checkout, m, err := launchCheckout(ctx, stderr)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya:", err)
	} else if checkout.Kind == Delivered {
		env = append(env, prepareSession(ctx, checkout, m, stderr)...)
	}
	// #nosec G204 -- the user's own command, as typed or as the editor passed it
	return syscall.Exec(path, command, env)
}

// launchCheckout is the checkout of the working directory, recognised after the repository list
// was refreshed (within onlineTimeout), so a repository added to Kaya is delivered from this
// launch on. Outside git it reaches nothing. A failed refresh leaves the cached list, and an API
// the token may not be sent to leaves the session on the cached release; both are named on stderr.
func launchCheckout(ctx context.Context, stderr io.Writer) (*Checkout, Machine, error) {
	checkout, err := CurrentCheckout()
	if err != nil || checkout.Kind == NotRepository {
		return checkout, Machine{}, err
	}
	m, err := ConnectMachine(ctx)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya:", err)
		return checkout, Machine{Err: err}, nil
	}
	refreshCtx, cancel := context.WithTimeout(ctx, onlineTimeout)
	defer cancel()
	if _, err := m.RefreshRepositoryList(refreshCtx); err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya:", err)
	}
	checkout, err = FindCheckout(checkout.Root)
	return checkout, m, err
}

// prepareSession syncs the checkout, registers this process as a live session and starts the
// watcher. It returns the variables Claude Code and its hooks read.
func prepareSession(ctx context.Context, checkout *Checkout, m Machine, stderr io.Writer) []string {
	vars := []string{EnvLaunched + "=1", EnvCheckout + "=" + checkout.Root}
	d, err := OpenWith(checkout, m)
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya:", err)
		return append(vars, EnvReleaseState+"="+StateUnverified)
	}
	// The launcher's own process becomes Claude Code by exec, so it never counts as another session.
	d.ExcludePID = os.Getpid()
	session, offline := startSession(ctx, d, stderr)
	if err := startWatcher(checkout); err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya: the watcher did not start:", err)
	}
	vars = append(vars, EnvReleaseState+"="+session.State)
	if session.Release > 0 {
		vars = append(vars, EnvRelease+"="+strconv.Itoa(session.Release))
	}
	if offline != nil {
		vars = append(vars, EnvOffline+"="+offlineReason(offline))
	}
	return vars
}

// startSession syncs the checkout and registers this process as a live session on the release the
// sync leaves active. offline is why Kaya was not reached, or why its newest release was not taken.
// A delivered path git tracks, as on a branch from before Kaya delivered that path, refuses only
// the release that would replace it: the active release stays placed, so the session runs on it
// and the refusal, with its fix, is printed as a warning.
func startSession(ctx context.Context, d *Deliverer, stderr io.Writer) (session Session, offline error) {
	checkout := d.Checkout
	session = Session{PID: os.Getpid(), State: StateUnverified, DeliveredBy: DeliveredByLauncher}
	lockCtx, cancel := LockContext(ctx)
	defer cancel()
	out, err := d.Sync(lockCtx, Pin)
	var tracked *TrackedError
	if errors.As(err, &tracked) {
		out.Warnings = append(out.Warnings, err.Error())
		err = nil
	}
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya:", err)
	} else {
		session.State, session.Release, offline = out.State, out.Active, out.Offline
		saved, loadErr := checkout.LoadState()
		if loadErr != nil {
			_, _ = fmt.Fprintln(stderr, "oba kaya:", loadErr)
		}
		PrintOutcome(stderr, "oba kaya: ", checkout, out, saved)
	}
	if err := checkout.RegisterSession(session); err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya:", err)
	}
	return session, offline
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

// startWatcher starts oba kaya watch for the checkout in its own session, detached from the
// terminal, logging to .kaya/watch.log. A watcher already running for the checkout makes the new
// one exit at once.
func startWatcher(checkout *Checkout) error {
	self, err := os.Executable()
	if err != nil {
		return fmt.Errorf("find the oba binary: %w", err)
	}
	logPath := filepath.Join(checkout.Root, ".kaya", "watch.log")
	logFile, err := os.OpenFile(logPath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600) // #nosec G304 -- fixed path below the checkout's .kaya
	if err != nil {
		return fmt.Errorf("open %s: %w", logPath, err)
	}
	defer func() { _ = logFile.Close() }()
	watch := exec.Command(self, "kaya", "watch", "--checkout", checkout.Root) // #nosec G204 -- this binary with fixed arguments
	watch.Dir = checkout.Root
	watch.Stdout, watch.Stderr = logFile, logFile
	watch.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
	if err := watch.Start(); err != nil {
		return fmt.Errorf("start oba kaya watch: %w", err)
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
		case EnvLaunched, EnvCheckout, EnvRelease, EnvReleaseState, EnvOffline:
			continue
		}
		kept = append(kept, entry)
	}
	return kept
}
