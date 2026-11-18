package cmd

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/delivery"
)

const (
	// watchInterval is how often the watcher asks Kaya for a newer release, so that it is built
	// before the next session starts.
	watchInterval = 60 * time.Second
	// feedbackInterval is how often the watcher fetches reviewer feedback on the sessions' plans
	// into their inboxes and posts what the hooks delivered, so that feedback reaches a running
	// session within 30 s of its next prompt or tool call.
	feedbackInterval = 15 * time.Second
)

var watchCheckout string

var watchCmd = &cobra.Command{
	Use:   "watch",
	Short: "Prepare the next release and deliver plan feedback while the checkout's sessions run",
	Long: "Asks Kaya every minute for the checkout's current release and prepares a newer one for the " +
		"next session, without switching. Every 15 seconds it fetches the reviewer feedback " +
		"on the live sessions' plans into .kaya/inbox/, where the session's next prompt or tool call picks " +
		"it up, and posts the feedback the hooks delivered. kaya launch starts it; it exits when the " +
		"checkout has no live session, and at once when a watcher of the checkout already runs.",
	Args: cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error {
		dir := watchCheckout
		if dir == "" {
			wd, err := os.Getwd()
			if err != nil {
				return fmt.Errorf("read the working directory: %w", err)
			}
			dir = wd
		}
		checkout, err := delivery.FindCheckout(dir)
		if err != nil {
			return err
		}
		if checkout.Kind != delivery.Delivered {
			return errNotDelivered(checkout)
		}
		ctx, stop := signal.NotifyContext(cmd.Context(), syscall.SIGINT, syscall.SIGTERM)
		defer stop()
		return watch(ctx, checkout, cmd.ErrOrStderr())
	},
}

func init() {
	watchCmd.Flags().StringVar(&watchCheckout, "checkout", "", "the checkout to watch (default: the working directory's)")
	rootCmd.AddCommand(watchCmd)
}

func watch(ctx context.Context, checkout *delivery.Checkout, log io.Writer) error {
	release, held, err := holdWatchLock(checkout)
	if err != nil {
		return err
	}
	if !held {
		return nil
	}
	defer release()
	logf := func(format string, args ...any) {
		_, _ = fmt.Fprintf(log, time.Now().UTC().Format(time.RFC3339)+" "+format+"\n", args...)
	}
	logf("watching %s", checkout.Root)
	var (
		d          *delivery.Deliverer
		lastPolled time.Time
	)
	for {
		live, liveErr := checkout.LiveSessions(0)
		if liveErr != nil {
			logf("%v", liveErr)
		}
		if d == nil || d.Source == nil {
			var err error
			d, err = newDeliverer(ctx, checkout, true)
			if err != nil {
				logf("%v", err)
			}
		}
		if liveErr == nil && len(live) == 0 {
			// The last session may have received feedback just before it ended; its acks go
			// out now, or Kaya would hand the same feedback to the next session on the branch.
			if src := feedbackSourceOf(d); src != nil {
				if err := checkout.PostFeedbackAcks(ctx, src); err != nil {
					logf("%v", err)
				}
			}
			logf("no live session; the watcher ends")
			return nil
		}
		if d != nil && time.Since(lastPolled) >= watchInterval {
			lastPolled = time.Now()
			d.OnlineTimeout = 30 * time.Second
			pollCtx, cancel := lockContext(ctx)
			err := d.Poll(pollCtx)
			cancel()
			if err != nil {
				logf("%v", err)
			}
		}
		if src := feedbackSourceOf(d); src != nil {
			feedbackCtx, cancel := context.WithTimeout(ctx, feedbackInterval)
			err := checkout.SyncFeedback(feedbackCtx, src, sessionIDs(live))
			cancel()
			if err != nil {
				logf("%v", err)
			}
		}
		select {
		case <-ctx.Done():
			logf("stopped")
			return nil
		case <-time.After(feedbackInterval):
		}
	}
}

// feedbackSourceOf is the deliverer's API client as the plan feedback calls see it, nil without
// a machine token.
func feedbackSourceOf(d *delivery.Deliverer) delivery.FeedbackSource {
	if d == nil {
		return nil
	}
	src, _ := d.Source.(delivery.FeedbackSource)
	return src
}

// sessionIDs are the Claude Code session ids of the live sessions; a launcher record before
// its session started has none.
func sessionIDs(live []delivery.Session) []string {
	ids := make([]string, 0, len(live))
	for _, s := range live {
		if s.SessionID != "" {
			ids = append(ids, s.SessionID)
		}
	}
	return ids
}

// holdWatchLock takes .kaya/watch.lock without waiting; held is false when another watcher of
// the checkout has it.
func holdWatchLock(checkout *delivery.Checkout) (func(), bool, error) {
	path := filepath.Join(checkout.Root, ".kaya", "watch.lock")
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600) // #nosec G304 -- fixed path below the checkout's .kaya
	if err != nil {
		return nil, false, fmt.Errorf("open %s: %w", path, err)
	}
	if err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil { // #nosec G115 -- a file descriptor fits an int
		_ = file.Close()
		if errors.Is(err, syscall.EWOULDBLOCK) {
			return nil, false, nil
		}
		return nil, false, fmt.Errorf("lock %s: %w", path, err)
	}
	return func() { _ = file.Close() }, true, nil
}
