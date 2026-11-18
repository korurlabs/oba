package cmd

import (
	"fmt"
	"io"
	"time"

	"github.com/korurlabs/kaya-cli/internal/capture"
	"github.com/korurlabs/kaya-cli/internal/sender"
)

// recordHook is `kaya hook record`: it queues the event for Kaya and makes sure a sender runs.
// It prints nothing on stdout, and every error is one stderr line with exit 0, so recording
// never blocks or fails Claude Code.
func recordHook(raw []byte, stderr io.Writer) error {
	in, err := capture.Decode(raw)
	if err != nil {
		return hookFailed(stderr, fmt.Errorf("nothing recorded: %w", err))
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

// startSender starts the process that sends the queue to Kaya, unless one runs or was started
// too recently. The queue is durable, so a sender that does not start loses nothing.
func startSender() error {
	_, err := sender.StartDetached()
	if err != nil {
		return fmt.Errorf("queued, but the sender did not start: %w", err)
	}
	return nil
}
