// Package exitcode lets a command end the process with an exit status of its own after it has
// already told the user what happened, such as a hook whose guard refused a tool (2) or a seed
// whose checks failed.
package exitcode

import "fmt"

// Error ends the process with Code. The root command prints nothing for it: the command printed
// its outcome before it returned.
type Error struct {
	Code int
}

func (e *Error) Error() string {
	return fmt.Sprintf("exit status %d", e.Code)
}
