//go:build darwin || dragonfly || freebsd || netbsd || openbsd

package terminal

import "golang.org/x/sys/unix"

// fread is FREAD of <sys/fcntl.h>: TIOCFLUSH with it flushes the input queue only.
const fread = 0x0001

// flushTerminalInput discards the input the terminal on fd holds unread, as tcflush(TCIFLUSH)
// does on these systems.
func flushTerminalInput(fd int) error {
	return unix.IoctlSetPointerInt(fd, unix.TIOCFLUSH, fread)
}
