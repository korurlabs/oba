// Package machine is how the Kaya commands that run without a user, inside Claude Code sessions
// and in the background, reach Kaya: the API URL and the machine token (kyt_...), which the
// recorder, config delivery, plan feedback, lessons and proposals send instead of a user's
// sign-in.
package machine

import (
	"context"
	"fmt"
	"net"
	"net/url"
	"os"
	"os/exec"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

const (
	// EnvToken holds the machine token.
	EnvToken = "KAYA_RECORDER_TOKEN"
	// kagiPath is the Kagi app whose personal environment holds the machine token.
	kagiPath = "/kaya/recorder"
)

// APIURL is the API the machine calls go to: OBA_API_URL, else production. It reads no stored
// session, because the hooks call it on every tool use and must neither wait on nor prompt for
// the keyring; a developer on the local stack sets OBA_API_URL.
func APIURL() string {
	return config.Resolve(config.Endpoints{}).APIURL
}

// Token is the machine token: KAYA_RECORDER_TOKEN, else from the developer's Kagi personal
// environment.
func Token(ctx context.Context) (string, error) {
	if token := strings.TrimSpace(os.Getenv(EnvToken)); token != "" {
		return token, nil
	}
	kagi, err := exec.LookPath("kagi")
	if err != nil {
		return "", fmt.Errorf("no %s and no kagi on PATH", EnvToken)
	}
	cmd := exec.CommandContext(ctx, kagi, "run", "--personal", "--path", kagiPath, "--", // #nosec G204 -- fixed kagi arguments
		"sh", "-c", `printf %s "$`+EnvToken+`"`)
	out, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("read %s through kagi: %w", EnvToken, err)
	}
	token := strings.TrimSpace(string(out))
	if token == "" {
		return "", fmt.Errorf("no %s in your Kagi personal environment (kagi secrets set --personal --path %s --from-file /dev/stdin, then enter %s=<token> and Ctrl-D)", EnvToken, kagiPath, EnvToken)
	}
	return token, nil
}

// Client returns a client of the API that sends the machine token, read once within
// tokenTimeout, since each read can go through kagi. The API must be reached over https or on a
// loopback address.
func Client(ctx context.Context, tokenTimeout time.Duration) (*client.Client, error) {
	apiURL := APIURL()
	if err := CheckTransport(apiURL); err != nil {
		return nil, err
	}
	tokenCtx, cancel := context.WithTimeout(ctx, tokenTimeout)
	defer cancel()
	token, err := Token(tokenCtx)
	if err != nil {
		return nil, err
	}
	return client.New(apiURL, "", func(context.Context) (string, error) { return token, nil }), nil
}

// IsLoopback reports whether the API URL names this machine.
func IsLoopback(apiURL string) bool {
	parsed, err := url.Parse(apiURL)
	if err != nil {
		return false
	}
	host := parsed.Hostname()
	if host == "localhost" {
		return true
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

// CheckTransport refuses to send the machine token over plain http to another machine.
func CheckTransport(apiURL string) error {
	parsed, err := url.Parse(apiURL)
	if err != nil {
		return fmt.Errorf("the API URL %q is invalid: %w", apiURL, err)
	}
	if parsed.Scheme == "https" || (parsed.Scheme == "http" && IsLoopback(apiURL)) {
		return nil
	}
	return fmt.Errorf("the API URL %s is not https; the machine token is sent only over https or to this machine", apiURL)
}
