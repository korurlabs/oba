package cmd

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/delivery"
)

var signingKeysCmd = &cobra.Command{
	Use:   "signing-keys",
	Short: "Print the release signing keys of a local Kaya stack for " + delivery.EnvDevTrustedKeys,
	Long: "Lists the keys GET /kaya/signing-keys returns as a " + delivery.EnvDevTrustedKeys + " line. The " +
		"production keys are compiled into the CLI; this line is honored only while the API is a loopback " +
		"address, for the local stack whose backend signs with an ephemeral key. Needs kaya login.",
	Args: cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error {
		api, _, err := signedInClient()
		if err != nil {
			return err
		}
		ctx, cancel := context.WithTimeout(cmd.Context(), 30*time.Second)
		defer cancel()
		keys, err := api.SigningKeys(ctx)
		if err != nil {
			return err
		}
		pairs := make([]string, 0, len(keys))
		for _, key := range keys {
			pairs = append(pairs, key.KeyID+"="+key.PublicKey)
		}
		_, _ = fmt.Fprintf(cmd.OutOrStdout(), "export %s=%s\n", delivery.EnvDevTrustedKeys, strings.Join(pairs, ","))
		return nil
	},
}

func init() {
	rootCmd.AddCommand(signingKeysCmd)
}
