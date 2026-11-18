// Package cmd holds the kalkan commands. Each command lives in its own file and registers itself
// on rootCmd in init.
package cmd

import (
	"errors"
	"fmt"
	"os"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kalkan-cli/internal/auth"
	"github.com/korurlabs/kalkan-cli/internal/client"
	"github.com/korurlabs/kalkan-cli/internal/config"
	"github.com/korurlabs/kalkan-cli/internal/project"
)

var (
	overrides  config.Overrides
	jsonOutput bool
)

// SetVersion sets the version main received through ldflags.
func SetVersion(v string) {
	rootCmd.Version = v
}

var rootCmd = &cobra.Command{
	Use:   "kalkan",
	Short: "Kalkan CLI: read and write Kalkan pages from the terminal",
	Long: "The Kalkan CLI signs you in to Kalkan, lists and reads its pages, and writes pages by " +
		"their path. Only kalkan login is interactive; every other command runs unattended, so " +
		"developers and Claude Code agents can call it from scripts.",
	SilenceUsage:  true,
	SilenceErrors: true,
}

func init() {
	flags := rootCmd.PersistentFlags()
	flags.StringVar(&overrides.APIURL, "api-url", "", "Oba API base URL (env "+config.EnvAPIURL+")")
	flags.StringVar(&overrides.Issuer, "issuer", "", "Keycloak issuer URL of the sensey realm (env "+config.EnvIssuer+")")
	flags.StringVar(&overrides.ClientID, "client-id", "", "Keycloak client id of the CLI sign-in (env "+config.EnvClientID+")")
	flags.StringVar(&overrides.OrgID, "org", "", "organization id of every call (env "+config.EnvOrgID+")")
	flags.BoolVar(&overrides.Dev, "dev", false, "use the local Oba stack (API :8080, Keycloak :8086)")
	flags.BoolVar(&jsonOutput, "json", false, "print the result as JSON on stdout and errors as JSON on stderr")
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
	os.Exit(ExitFailure)
}

// projectOrganization is the organization of the kalkan.yaml that applies in the current
// directory, empty when there is none. A kalkan.yaml that does not parse is an error, so a
// repository never acts in another organization than the one it names.
func projectOrganization() (string, error) {
	cwd, err := os.Getwd()
	if err != nil {
		return "", fmt.Errorf("read the current directory: %w", err)
	}
	proj, err := project.Find(cwd)
	if err != nil || proj == nil {
		return "", err
	}
	return proj.Organization, nil
}

// signedInClient loads the stored sign-in and returns a client for calls in the resolved
// organization. No stored sign-in, or one made at another issuer, is ErrNotSignedIn.
func signedInClient() (*client.Client, config.Config, error) {
	store, err := auth.DefaultStore()
	if err != nil {
		return nil, config.Config{}, err
	}
	creds, err := store.Load()
	if err != nil {
		return nil, config.Config{}, err
	}
	stored := config.Config{APIURL: creds.APIURL, Issuer: creds.Issuer, ClientID: creds.ClientID, OrgID: creds.OrgID}
	projectOrgID, err := projectOrganization()
	if err != nil {
		return nil, config.Config{}, err
	}
	cfg := config.Resolve(overrides, projectOrgID, stored)
	if cfg.Issuer != creds.Issuer {
		return nil, config.Config{}, fmt.Errorf("signed in at %s, not at %s: %w", creds.Issuer, cfg.Issuer, auth.ErrNotSignedIn)
	}
	if cfg.OrgID == "" {
		return nil, config.Config{}, fmt.Errorf("no organization chosen; pass --org <id>, set %s, or run kalkan login --org <id>", config.EnvOrgID)
	}
	tokens := auth.NewTokenSource(store, creds)
	return client.New(cfg.APIURL, cfg.OrgID, tokens.Token), cfg, nil
}
