package lessons

import (
	"context"
	"fmt"
	"io"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
)

// tokenTimeout bounds the machine token lookup, which can go through kagi. Each API call has the
// client's own bound.
const tokenTimeout = 30 * time.Second

// Checkout is the checkout of the working directory and the remote it names to Kaya: the remote
// that puts it on the organization's repository list. A checkout not cut over yet will do, since
// lessons are not delivered config; a checkout with no listed remote is refused, so nothing of it
// reaches Kaya.
func Checkout() (*delivery.Checkout, string, error) {
	checkout, err := delivery.CurrentCheckout()
	if err != nil {
		return nil, "", err
	}
	if checkout.Kind == delivery.NotRepository {
		return nil, "", delivery.NotDelivered(checkout)
	}
	remote, _, err := checkout.Remotes()
	if err != nil {
		return nil, "", err
	}
	return checkout, remote, nil
}

// Session is the session that records a lesson: id when given, else the session of the Claude
// Code process that runs the command, else the only live session of the checkout. A failed read of the session records costs
// the link, not the lesson, and says so.
func Session(stderr io.Writer, checkout *delivery.Checkout, id string) (string, error) {
	if id != "" {
		if !delivery.ValidSessionID(id) {
			return "", fmt.Errorf("--session %q is not a Claude Code session id", id)
		}
		return id, nil
	}
	live, err := checkout.LiveSessions(0)
	if err != nil {
		_, _ = fmt.Fprintf(stderr, "oba kaya lessons: the lesson gets no session link: %v\n", err)
		return "", nil
	}
	return SessionOf(delivery.ClaudePID(), live), nil
}

// Client is a client with the machine token, read once: an import makes many calls, and each
// read can go through kagi.
func Client(ctx context.Context) (*client.Client, error) {
	return machine.Client(ctx, tokenTimeout)
}
