package shrine

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"runtime"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// errLoginWithEnvToken is printed as it is, so it keeps the full stop.
var errLoginWithEnvToken = errors.New(config.EnvToken + " is set, so commands use it and would ignore a stored session. Unset " + config.EnvToken + " to sign in.") //nolint:staticcheck // ST1005: shown to the user verbatim

// newLoginCmd builds oba login.
func newLoginCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "login",
		Short: "Sign in through the browser",
		Long: "Signs you in with the Keycloak device authorization grant: opens the browser, waits for the sign-in, " +
			"syncs your Oba profile and stores the session in the OS secret service.\n\n" +
			"Environment: " + config.EnvAPIURL + " (default " + config.ProdAPIURL + "), " +
			config.EnvIssuer + " (default " + config.ProdIssuer + "), " +
			config.EnvClientID + " (default " + config.ProdClientID + "). " +
			"The values used are stored with the session, so later commands call the same API.",
		Args: cobra.NoArgs,
		RunE: runLogin,
	}
}

func runLogin(cmd *cobra.Command, _ []string) error {
	if session.EnvToken() != "" {
		return errLoginWithEnvToken
	}
	out := cmd.OutOrStdout()
	endpoints := config.Resolve(config.Endpoints{})

	deviceFlow := auth.NewDeviceFlow(endpoints.Issuer, endpoints.ClientID, "openid")
	oidc, err := deviceFlow.DiscoverEndpoints()
	if err != nil {
		return fmt.Errorf("failed to discover the sign-in endpoints: %w", err)
	}
	deviceResp, err := deviceFlow.RequestDeviceAuthorization(oidc.DeviceAuthorizationEndpoint)
	if err != nil {
		return fmt.Errorf("failed to start the device sign-in: %w", err)
	}

	_, _ = fmt.Fprintln(out)
	_, _ = fmt.Fprintf(out, "Open this URL in your browser: %s\n", deviceResp.VerificationURIComplete)
	_, _ = fmt.Fprintf(out, "Enter code: %s\n", deviceResp.UserCode)
	_, _ = fmt.Fprintln(out)
	if deviceResp.VerificationURIComplete != "" {
		openBrowser(deviceResp.VerificationURIComplete, cmd.ErrOrStderr())
	}

	_, _ = fmt.Fprintln(out, "Waiting for authentication...")
	interval := time.Duration(deviceResp.Interval) * time.Second
	if interval == 0 {
		interval = 5 * time.Second
	}
	expiresAt := time.Now().Add(time.Duration(deviceResp.ExpiresIn) * time.Second)
	tokenResp, err := deviceFlow.PollForToken(oidc.TokenEndpoint, deviceResp.DeviceCode, interval, expiresAt)
	if err != nil {
		return fmt.Errorf("authentication failed: %w", err)
	}

	user, err := syncProfile(client.NewWithToken(endpoints.APIURL, "", tokenResp.AccessToken), out)
	if err != nil {
		return err
	}
	defaultOrgID := ""
	if user.DefaultOrganizationID != nil {
		defaultOrgID = *user.DefaultOrganizationID
	}

	// The keyring was probed before the browser round trip; probe again so that a keychain the
	// user unlocked meanwhile is used rather than written past into the plaintext fallback.
	auth.ResetTokenStore()
	creds := auth.Credentials{
		AccessToken:  tokenResp.AccessToken,
		RefreshToken: tokenResp.RefreshToken,
		ExpiresAt:    time.Now().Add(time.Duration(tokenResp.ExpiresIn) * time.Second),
		APIURL:       endpoints.APIURL,
		IssuerURL:    endpoints.Issuer,
		ClientID:     endpoints.ClientID,
		UserID:       user.ID,
		DefaultOrgID: defaultOrgID,
	}
	if err := auth.NewTokenStore().Save(creds); err != nil {
		return fmt.Errorf("failed to store the session: %w", err)
	}

	orgID, err := config.CurrentOrgID(defaultOrgID)
	if err != nil {
		return fmt.Errorf("signed in, but could not read the chosen organization: %w", err)
	}

	_, _ = fmt.Fprintln(out)
	_, _ = fmt.Fprintln(out, "Login successful.")
	_, _ = fmt.Fprintf(out, "API:          %s\n", endpoints.APIURL)
	_, _ = fmt.Fprintf(out, "User:         %s\n", user.Email)
	_, _ = fmt.Fprintf(out, "Organization: %s\n", describeOrg(user, orgID))
	return nil
}

// syncProfile reads the signed-in user. A user new to Oba has no profile yet, so a failing GET
// is followed by POST /users/me, which creates it from the token, and a second GET.
func syncProfile(c *client.Client, out io.Writer) (client.User, error) {
	body, err := c.Get("/users/me")
	if err != nil {
		_, _ = fmt.Fprintln(out, "Syncing user profile...")
		if _, postErr := c.Post("/users/me", nil); postErr != nil {
			return client.User{}, fmt.Errorf("failed to sync the user profile: %w", postErr)
		}
		body, err = c.Get("/users/me")
		if err != nil {
			return client.User{}, fmt.Errorf("failed to read the user profile after sync: %w", err)
		}
	}
	var resp client.APIResponse[client.User]
	if err := json.Unmarshal(body, &resp); err != nil {
		return client.User{}, fmt.Errorf("failed to parse the user profile: %w", err)
	}
	return resp.Data, nil
}

// describeOrg names the current organization by name and id, or says that none is chosen.
func describeOrg(user client.User, orgID string) string {
	if orgID == "" {
		return "none. Run 'oba org use <name|id>'"
	}
	for _, org := range user.Organizations {
		if org.ID == orgID {
			return fmt.Sprintf("%s (%s)", org.Name, org.ID)
		}
	}
	return orgID
}

func openBrowser(url string, errOut io.Writer) {
	var c *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		c = exec.Command("open", url)
	case "linux":
		c = exec.Command("xdg-open", url)
	default:
		return
	}
	c.Stdout = os.Stdout
	c.Stderr = os.Stderr
	if err := c.Start(); err != nil {
		_, _ = fmt.Fprintf(errOut, "Could not open the browser (%v). Open the URL above by hand.\n", err)
		return
	}
	// Reap the opener so it does not linger as a zombie while login polls.
	go func() { _ = c.Wait() }()
}
