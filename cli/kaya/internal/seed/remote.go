package seed

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"regexp"
	"strings"
	"sync"
	"time"
)

// SSHResolveTimeout bounds one `ssh -G` call, so a slow `Match exec` in the user's ssh
// configuration cannot hold up a seed.
const SSHResolveTimeout = 2 * time.Second

// The sender resolves the remote of every captured session with these same patterns, so the seed
// and the captured sessions report the same remote for one checkout and Kaya stores one repository
// for it.
var (
	sshURLRemote = regexp.MustCompile(`(?i)^((?:git\+)?ssh(?:\+git)?://)([^/@]*@)?([^/:]+)(:[0-9]*)?(/.*)$`)
	// scpRemote stands for `^([^/@:]+@)?([^/:]+):(?!//)(.+)$`; Go has no lookahead,
	// so SplitSSHRemote checks that the path does not start with "//".
	scpRemote = regexp.MustCompile(`^([^/@:]+@)?([^/:]+):(.+)$`)
	// sshHost admits plain host names only: no leading dash, so a host can never be read as an
	// ssh option, and no bracketed IPv6 address.
	sshHost = regexp.MustCompile(`^[A-Za-z0-9_][A-Za-z0-9._-]*$`)
)

// SplitSSHRemote splits an ssh remote into the text before its host, the host and the text after
// it, so that before+host+after == remote. ok is false for any other remote (https, file, a local
// path) and for a host that is not a plain name.
func SplitSSHRemote(remote string) (before, host, after string, ok bool) {
	if match := sshURLRemote.FindStringSubmatch(remote); match != nil {
		before, host, after = match[1]+match[2], match[3], match[4]+match[5]
	} else {
		if strings.Contains(remote, "://") {
			return "", "", "", false
		}
		match := scpRemote.FindStringSubmatch(remote)
		if match == nil || strings.HasPrefix(match[3], "//") {
			return "", "", "", false
		}
		before, host, after = match[1], match[2], ":"+match[3]
	}
	if !sshHost.MatchString(host) {
		return "", "", "", false
	}
	return before, host, after, true
}

// SSHHostname is the `hostname` that `ssh -G` gives for host under the user's ssh configuration.
// `ssh -G` prints the configuration only and opens no connection.
func SSHHostname(host string, timeout time.Duration) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, "ssh", "-G", "--", host) // #nosec G204 -- host matches sshHost, so it is a plain name and never an option
	cmd.Env = append(os.Environ(), "LC_ALL=C")
	// A child that keeps the output pipes open must not outlive the timeout either.
	cmd.WaitDelay = 100 * time.Millisecond
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	err := cmd.Run()
	if errors.Is(ctx.Err(), context.DeadlineExceeded) {
		return "", fmt.Errorf("ssh -G %s timed out after %s", host, timeout)
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		message := strings.TrimSpace(stderr.String())
		if len(message) > 500 {
			message = message[:500]
		}
		return "", fmt.Errorf("ssh -G %s exited %d: %s", host, exitErr.ExitCode(), message)
	}
	if err != nil {
		return "", fmt.Errorf("ssh -G %s: %w", host, err)
	}
	for _, line := range strings.Split(stdout.String(), "\n") {
		key, value, _ := strings.Cut(line, " ")
		if key != "hostname" {
			continue
		}
		value = strings.TrimSpace(value)
		if !sshHost.MatchString(value) {
			return "", fmt.Errorf("ssh -G %s gave the hostname %q, which is not a plain host name", host, value)
		}
		return value, nil
	}
	return "", fmt.Errorf("ssh -G %s printed no hostname", host)
}

// RemoteResolver replaces an ssh host alias in a remote with the host it stands for, so that
// `git@github-sensey:korurlabs/village.git` and a clone over github.com name one repository.
// Results are cached per host for the life of the process.
type RemoteResolver struct {
	// Lookup gives the real host name of an ssh host; tests replace it.
	Lookup func(host string) (string, error)

	mu    sync.Mutex
	hosts map[string]string
}

// NewRemoteResolver resolves hosts through `ssh -G` with SSHResolveTimeout.
func NewRemoteResolver() *RemoteResolver {
	return &RemoteResolver{Lookup: func(host string) (string, error) { return SSHHostname(host, SSHResolveTimeout) }}
}

var defaultResolver = NewRemoteResolver()

// Resolve gives the remote with its host alias replaced; user, port and path stay as written.
// Any other remote, or one whose host ssh cannot resolve, comes back unchanged. warning names
// why a host could not be resolved, the first time only.
func (r *RemoteResolver) Resolve(remote string) (resolved, warning string) {
	before, host, after, ok := SplitSSHRemote(remote)
	if !ok {
		return remote, ""
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	name, cached := r.hosts[host]
	if !cached {
		value, err := r.Lookup(host)
		if err != nil {
			warning = fmt.Sprintf("the remote %s is sent as git reports it: %v", remote, err)
		}
		if r.hosts == nil {
			r.hosts = map[string]string{}
		}
		r.hosts[host] = value
		name = value
	}
	// ssh lowercases a host it has no alias for, so a match ignoring case means no alias.
	if name == "" || strings.EqualFold(name, host) {
		return remote, warning
	}
	return before + name + after, warning
}
