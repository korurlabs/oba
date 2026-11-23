package hook

import (
	"fmt"
	"io"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/capture"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/sender"
)

// recordHook is `oba kaya hook record`: it queues the event for Kaya and makes sure a sender runs.
// Only a session in a checkout Kaya recognises is recorded; in any other checkout the hook
// writes nothing, so nothing of it, its remotes included, reaches Kaya. It prints nothing on
// stdout, and every error is one stderr line with exit 0, so recording never blocks or fails
// Claude Code.
func recordHook(raw []byte, stderr io.Writer) error {
	in, err := capture.Decode(raw)
	if err != nil {
		return hookFailed(stderr, fmt.Errorf("nothing recorded: %w", err))
	}
	recognised, err := recordedCheckout(in)
	if err != nil {
		return hookFailed(stderr, fmt.Errorf("nothing recorded: %w", err))
	}
	if !recognised {
		return nil
	}
	queued, err := capture.Record(in, time.Now(), stderr)
	if err != nil {
		return hookFailed(stderr, fmt.Errorf("nothing recorded: %w", err))
	}
	if queued {
		return hookFailed(stderr, startSender())
	}
	return nil
}

// recordedCheckout reports whether the event's directory is in a checkout one of whose remotes is
// on the cached repository list. The list is only read here, never fetched, so the hook needs no
// network. A directory outside git is left to Record, which names it.
func recordedCheckout(in map[string]any) (bool, error) {
	dir, err := capture.EventDir(in)
	if err != nil {
		return false, err
	}
	checkout, err := delivery.FindCheckout(dir)
	if err != nil {
		return false, err
	}
	return checkout.Kind != delivery.NotKaya, nil
}

// startSender starts the process that sends the queue to Kaya, unless one runs or was started
// too recently. The queue is durable, so a sender that does not start loses nothing.
func startSender() error {
	_, err := sender.StartDetached()
	if err != nil {
		return fmt.Errorf("queued, but the sender did not start: %w", err)
	}
	return nil
}
