package cmd

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/auth"
	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/config"
)

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Sign in to Kaya in the browser",
	Long: "Signs you in through the browser (device authorization) and lists the organizations you " +
		"belong to. User commands such as kaya seed use this sign-in; recording and " +
		"config delivery use the machine token instead.",
	Args: cobra.NoArgs,
	RunE: runLogin,
}

var logoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Delete the stored sign-in",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error {
		store, err := auth.DefaultStore()
		if err != nil {
			return err
		}
		if err := store.Delete(); err != nil {
			return err
		}
		_, _ = fmt.Fprintln(cmd.OutOrStdout(), "Signed out.")
		return nil
	},
}

func init() {
	rootCmd.AddCommand(loginCmd, logoutCmd)
}

func runLogin(cmd *cobra.Command, _ []string) error {
	out := cmd.OutOrStdout()
	cfg := config.Resolve(overrides, config.Config{})
	ctx, cancel := context.WithTimeout(cmd.Context(), 15*time.Minute)
	defer cancel()

	flow := auth.NewDeviceFlow(cfg.Issuer, cfg.ClientID)
	endpoints, err := flow.Discover(ctx)
	if err != nil {
		return err
	}
	authorization, err := flow.Authorize(ctx, endpoints.DeviceAuthorizationEndpoint)
	if err != nil {
		return err
	}
	verify := authorization.VerificationURIComplete
	if verify == "" {
		verify = authorization.VerificationURI
	}
	_, _ = fmt.Fprintf(out, "Open %s\nand confirm the code %s\n", verify, authorization.UserCode)
	openBrowser(verify)

	token, err := flow.Poll(ctx, endpoints.TokenEndpoint, authorization)
	if err != nil {
		return err
	}
	creds := auth.Credentials{
		AccessToken:  token.AccessToken,
		RefreshToken: token.RefreshToken,
		ExpiresAt:    time.Now().Add(time.Duration(token.ExpiresIn) * time.Second),
		Issuer:       cfg.Issuer,
		ClientID:     cfg.ClientID,
		APIURL:       cfg.APIURL,
	}
	api := client.New(cfg.APIURL, "", func(context.Context) (string, error) { return creds.AccessToken, nil })
	me, err := api.ProvisionMe(ctx)
	if err != nil {
		return fmt.Errorf("sign in to Kaya: %w", err)
	}
	creds.Email = me.Email
	for _, membership := range me.Memberships {
		creds.Memberships = append(creds.Memberships, auth.Membership{
			OrganizationID: membership.OrganizationID, Name: membership.Name, Role: membership.Role,
		})
	}
	creds.OrgID, err = chooseOrganization(cfg.OrgID, creds.Memberships)
	if err != nil {
		return err
	}

	store, err := auth.DefaultStore()
	if err != nil {
		return err
	}
	if err := store.Save(creds); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Signed in as %s at %s.\n", me.Email, cfg.APIURL)
	for _, membership := range creds.Memberships {
		marker := " "
		if membership.OrganizationID == creds.OrgID {
			marker = "*"
		}
		_, _ = fmt.Fprintf(out, "%s %s (%s) %s\n", marker, membership.Name, membership.Role, membership.OrganizationID)
	}
	switch {
	case len(creds.Memberships) == 0:
		_, _ = fmt.Fprintln(out, "You belong to no organization yet; accept an invitation, then log in again.")
	case creds.OrgID == "":
		_, _ = fmt.Fprintln(out, "You are in several organizations; pass --org <id> to user commands, or log in with --org.")
	}
	return nil
}

// chooseOrganization keeps the organization asked for, which must be one of the memberships, or
// the only membership; with several and none asked for, it chooses none.
func chooseOrganization(asked string, memberships []auth.Membership) (string, error) {
	if asked != "" {
		for _, membership := range memberships {
			if membership.OrganizationID == asked {
				return asked, nil
			}
		}
		return "", fmt.Errorf("you are not a member of organization %s", asked)
	}
	if len(memberships) == 1 {
		return memberships[0].OrganizationID, nil
	}
	return "", nil
}

// openBrowser tries to open url; the URL is printed anyway, so a failure only means the user
// opens it by hand.
func openBrowser(url string) {
	var opener string
	switch runtime.GOOS {
	case "darwin":
		opener = "open"
	case "linux":
		opener = "xdg-open"
	default:
		return
	}
	if err := exec.Command(opener, url).Start(); err != nil { // #nosec G204 -- fixed opener; url comes from the issuer's answer
		_, _ = fmt.Fprintf(os.Stderr, "Could not open a browser (%v); open the URL above by hand.\n", err)
	}
}
