// Package cmd holds the kaya commands. Each command lives in its own file and registers itself
// on rootCmd in init.
package cmd

import (
	"errors"
	"fmt"
	"os"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/auth"
	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/config"
)

var overrides config.Overrides

// SetVersion sets the version main received through ldflags.
func SetVersion(v string) {
	rootCmd.Version = v
}

var rootCmd = &cobra.Command{
	Use:           "kaya",
	Short:         "Kaya CLI: agent config of Claude Code from Kaya",
	Long:          "The Kaya CLI signs you in to Kaya, seeds an organization's agent config from its repositories, delivers it to Claude Code as signed releases and proposes changes of it for review.",
	SilenceUsage:  true,
	SilenceErrors: true,
}

func init() {
	flags := rootCmd.PersistentFlags()
	flags.StringVar(&overrides.APIURL, "api-url", "", "Oba API base URL (env "+config.EnvAPIURL+")")
	flags.StringVar(&overrides.Issuer, "issuer", "", "Keycloak issuer URL of the sensey realm (env "+config.EnvIssuer+")")
	flags.StringVar(&overrides.ClientID, "client-id", "", "Keycloak client id of the CLI sign-in (env "+config.EnvClientID+")")
	flags.StringVar(&overrides.OrgID, "org", "", "organization id of user calls (env "+config.EnvOrgID+")")
	flags.BoolVar(&overrides.Dev, "dev", false, "use the local Oba stack (API :8080, Keycloak :8086)")
}

// ExitError ends the process with Code after its message was already printed.
type ExitError struct {
	Code int
}

func (e *ExitError) Error() string {
	return fmt.Sprintf("exit status %d", e.Code)
}

// Execute runs the command line.
func Execute() {
	err := rootCmd.Execute()
	if err == nil {
		return
	}
	var exitErr *ExitError
	if errors.As(err, &exitErr) {
		os.Exit(exitErr.Code)
	}
	_, _ = fmt.Fprintln(os.Stderr, "Error:", err)
	os.Exit(1)
}

// signedInClient loads the stored sign-in and returns a client for user calls in the resolved
// organization.
func signedInClient() (*client.Client, config.Config, error) {
	store, err := auth.DefaultStore()
	if err != nil {
		return nil, config.Config{}, err
	}
	creds, err := store.Load()
	if err != nil {
		return nil, config.Config{}, err
	}
	cfg := config.Resolve(overrides, config.Config{APIURL: creds.APIURL, Issuer: creds.Issuer, ClientID: creds.ClientID, OrgID: creds.OrgID})
	if cfg.Issuer != creds.Issuer {
		return nil, config.Config{}, fmt.Errorf("signed in at %s, not at %s; run kaya login again", creds.Issuer, cfg.Issuer)
	}
	tokens := auth.NewTokenSource(store, creds)
	return client.New(cfg.APIURL, cfg.OrgID, tokens.Token), cfg, nil
}
