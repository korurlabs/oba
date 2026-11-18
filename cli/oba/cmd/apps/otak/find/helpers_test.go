package find

import (
	"io"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// newRoot builds a fresh "oba otak" tree with only this package's commands, as the oba root adds them.
func newRoot() *cobra.Command {
	return clitest.AppRoot("otak", NewCmd())
}

// runCLILines runs "oba <args>" in process on a fresh tree, with stdin delivered one line per read; see
// clitest.RunCLILines.
func runCLILines(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLILines(t, newRoot(), stdin, args...)
}

// runCLITyped runs "oba <args>" in process on a fresh tree, with in as stdin; see clitest.RunCLIInput.
func runCLITyped(t *testing.T, in io.Reader, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLIInput(t, newRoot(), in, args...)
}
