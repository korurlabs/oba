package shrine

import (
	"errors"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

// newLogoutCmd builds oba logout.
func newLogoutCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "logout",
		Short: "Sign out and delete the stored session",
		Long:  "Deletes the stored session from the OS secret service or the plaintext fallback. The chosen organization in ~/.config/oba/config.json is kept.",
		Args:  cobra.NoArgs,
		RunE:  runLogout,
	}
}

func runLogout(cmd *cobra.Command, _ []string) error {
	store := auth.NewTokenStore()
	// Any other load failure goes on to Delete: an unreadable session is still removed, and an
	// unreachable secret service fails there with its own message.
	if _, err := store.Load(); errors.Is(err, auth.ErrNoCredentials) {
		_, _ = fmt.Fprintln(cmd.OutOrStdout(), "You are not logged in.")
		return nil
	}
	if err := store.Delete(); err != nil {
		return fmt.Errorf("failed to delete the stored session: %w", err)
	}
	_, _ = fmt.Fprintln(cmd.OutOrStdout(), "Logged out.")
	return nil
}
