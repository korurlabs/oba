//go:build linux

package terminal

import "golang.org/x/sys/unix"

// flushTerminalInput discards the input the terminal on fd holds unread, as tcflush(TCIFLUSH).
func flushTerminalInput(fd int) error {
	return unix.IoctlSetInt(fd, unix.TCFLSH, unix.TCIFLUSH)
}
