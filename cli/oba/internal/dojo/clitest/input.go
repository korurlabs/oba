package clitest

import (
	"io"
	"testing"

	"github.com/spf13/cobra"
)

// CtrlD marks where the typist presses Ctrl-D: the terminal then answers one read with the end
// of input and goes on reading afterwards.
const CtrlD = "\x00ctrl-d"

// typedInput replays what a terminal in line mode delivers: one read per typed line, and the end
// of input once for every Ctrl-D.
type typedInput struct{ reads []string }

// Typed returns stdin that delivers each of reads in one read, and the end of input for CtrlD.
func Typed(reads ...string) io.Reader { return &typedInput{reads: reads} }

func (in *typedInput) Read(p []byte) (int, error) {
	if len(in.reads) == 0 {
		return 0, io.EOF
	}
	next := in.reads[0]
	if next == CtrlD {
		in.reads = in.reads[1:]
		return 0, io.EOF
	}
	n := copy(p, next)
	if n == len(next) {
		in.reads = in.reads[1:]
	} else {
		in.reads[0] = next[n:]
	}
	return n, nil
}

// FakeTerminal makes the terminal check seam answer isTerminal for the test and restores it
// when the test ends. Each command package keeps its own seam, so the test passes its address.
func FakeTerminal(t *testing.T, seam *func() bool, isTerminal bool) {
	t.Helper()
	old := *seam
	*seam = func() bool { return isTerminal }
	t.Cleanup(func() { *seam = old })
}

// AppRoot builds a fresh "oba" root holding a bare "oba <app>" group with cmds, so that a test
// of one domain package runs its commands under the same path as the oba root adds them.
func AppRoot(app string, cmds ...*cobra.Command) *cobra.Command {
	group := &cobra.Command{Use: app}
	group.AddCommand(cmds...)
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(group)
	return root
}
