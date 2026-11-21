package delivery

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

// shells are the programs Claude Code may run a hook command through; the session's process is
// the first ancestor of a hook that is none of them.
var shells = map[string]bool{"sh": true, "bash": true, "dash": true, "zsh": true, "fish": true}

// ClaudePID is the Claude Code process a hook runs under: the hook's parent, or the parent of
// the shell that ran the hook command.
func ClaudePID() int {
	pid := os.Getppid()
	for range 3 {
		parent, name, err := parentOf(pid)
		if err != nil || !shells[name] || parent <= 1 {
			return pid
		}
		pid = parent
	}
	return pid
}

// parentOf gives the parent pid and the program name of pid.
func parentOf(pid int) (int, string, error) {
	if data, err := os.ReadFile(fmt.Sprintf("/proc/%d/stat", pid)); err == nil {
		// The name is in parentheses and may hold spaces; the fields after it are fixed.
		text := string(data)
		open, closing := strings.IndexByte(text, '('), strings.LastIndexByte(text, ')')
		if open < 0 || closing < open {
			return 0, "", fmt.Errorf("unexpected /proc/%d/stat", pid)
		}
		fields := strings.Fields(text[closing+1:])
		if len(fields) < 2 {
			return 0, "", fmt.Errorf("unexpected /proc/%d/stat", pid)
		}
		parent, err := strconv.Atoi(fields[1])
		return parent, text[open+1 : closing], err
	}
	out, err := exec.Command("ps", "-o", "ppid=", "-o", "comm=", "-p", strconv.Itoa(pid)).Output() // #nosec G204 -- fixed ps arguments
	if err != nil {
		return 0, "", fmt.Errorf("read the parent of %d: %w", pid, err)
	}
	fields := strings.Fields(string(out))
	if len(fields) < 2 {
		return 0, "", fmt.Errorf("read the parent of %d: unexpected ps output", pid)
	}
	parent, err := strconv.Atoi(fields[0])
	return parent, strings.TrimPrefix(filepath.Base(fields[1]), "-"), err
}
