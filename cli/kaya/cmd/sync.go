package cmd

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/delivery"
)

var syncJSON bool

var syncCmd = &cobra.Command{
	Use:   "sync",
	Short: "Bring the checkout to its newest signed release",
	Long: "Fetches and verifies the newest release of the checkout's repository, downloads only the files " +
		"the cache lacks, and switches to it unless another session of the checkout runs; then it restores " +
		"delivered files changed by hand. Exits 1 when the checkout has no verified release.",
	Args: cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error {
		return runSync(cmd.Context(), cmd.OutOrStdout(), delivery.Pin)
	},
}

var refreshCmd = &cobra.Command{
	Use:   "refresh",
	Short: "Switch the checkout to its newest release even while sessions run",
	Long: "Like kaya sync, but switches while other sessions of the checkout run, and names them: their " +
		"skills change under them, their always-on rules only after a restart.",
	Args: cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error {
		return runSync(cmd.Context(), cmd.OutOrStdout(), delivery.Force)
	},
}

func init() {
	syncCmd.Flags().BoolVar(&syncJSON, "json", false, "print the outcome as JSON")
	refreshCmd.Flags().BoolVar(&syncJSON, "json", false, "print the outcome as JSON")
	rootCmd.AddCommand(syncCmd, refreshCmd)
}

func runSync(ctx context.Context, w io.Writer, policy delivery.Policy) error {
	checkout, err := currentCheckout()
	if err != nil {
		return err
	}
	if checkout.Kind != delivery.Delivered {
		return errNotDelivered(checkout)
	}
	d, err := newDeliverer(ctx, checkout, true)
	if err != nil {
		return err
	}
	// A manual sync waits longer for Kaya than a session start does.
	d.OnlineTimeout = 60 * time.Second
	lockCtx, cancel := lockContext(ctx)
	defer cancel()
	out, err := d.Sync(lockCtx, policy)
	if err != nil {
		return err
	}
	state, err := checkout.LoadState()
	if err != nil {
		return err
	}
	if syncJSON {
		if err := writeJSON(w, outcomeJSON(checkout, out, state)); err != nil {
			return err
		}
	} else {
		printOutcome(w, "", checkout, out, state)
		if policy == delivery.Force && out.Switched {
			for _, session := range out.LiveSessions {
				_, _ = fmt.Fprintf(w, "Switched under the running session %s (pid %d, release %d).\n", sessionName(session), session.PID, session.Release)
			}
		}
	}
	if out.State == delivery.StateNone || out.State == delivery.StateUnverified {
		return &ExitError{Code: 1}
	}
	return nil
}

func sessionName(s delivery.Session) string {
	if s.SessionID != "" {
		return s.SessionID
	}
	return "started by kaya launch"
}

// outcomeView is the JSON of kaya sync, refresh and status.
type outcomeView struct {
	Checkout       string             `json:"checkout"`
	Repository     string             `json:"repository"`
	State          string             `json:"state"`
	Active         int                `json:"active,omitempty"`
	CreatedAt      string             `json:"createdAt,omitempty"`
	ManifestSHA256 string             `json:"manifestSha256,omitempty"`
	Latest         int                `json:"latest,omitempty"`
	Staged         int                `json:"staged,omitempty"`
	CheckedAt      string             `json:"checkedAt,omitempty"`
	Switched       bool               `json:"switched,omitempty"`
	Offline        string             `json:"offline,omitempty"`
	LiveSessions   []delivery.Session `json:"liveSessions"`
	Problems       []string           `json:"problems"`
	Warnings       []string           `json:"warnings"`
}

func outcomeJSON(checkout *delivery.Checkout, out delivery.Outcome, state delivery.State) outcomeView {
	view := outcomeView{
		Checkout: checkout.Root, Repository: checkout.Name(state), State: out.State, Active: out.Active,
		CreatedAt: out.ActiveCreatedAt, ManifestSHA256: out.ManifestSHA256, Latest: out.Latest, Staged: out.Staged,
		CheckedAt: state.CheckedAt, Switched: out.Switched, LiveSessions: out.LiveSessions,
		Problems: []string{}, Warnings: out.Warnings,
	}
	if view.LiveSessions == nil {
		view.LiveSessions = []delivery.Session{}
	}
	if view.Warnings == nil {
		view.Warnings = []string{}
	}
	if out.Offline != nil {
		view.Offline = out.Offline.Error()
	}
	for _, problem := range out.Problems {
		view.Problems = append(view.Problems, problem.String())
	}
	return view
}

func writeJSON(w io.Writer, value any) error {
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(value); err != nil {
		return fmt.Errorf("print the outcome: %w", err)
	}
	return nil
}
