//go:build !linux && !darwin && !dragonfly && !freebsd && !netbsd && !openbsd

package terminal

// flushTerminalInput does nothing here: these systems offer no portable input flush. Confirm
// still discards its own buffer and asks again on any answer other than y, yes, n or no.
func flushTerminalInput(_ int) error {
	return nil
}
