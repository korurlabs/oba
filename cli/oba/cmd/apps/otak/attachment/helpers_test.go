package attachment

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// newRoot builds a fresh "oba otak" tree with only this package's commands, as the oba root adds them.
func newRoot() *cobra.Command {
	return clitest.AppRoot("otak", NewCmd())
}

// runCLI runs "oba <args>" in process on a fresh tree with stdin; see clitest.RunCLI.
func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), stdin, args...)
}

// newAttachmentAPI starts a mock API signed in to clitest.OrgIDAcme, with a terminal on stdin
// or not.
func newAttachmentAPI(t *testing.T, isTerminal bool) *clitest.MockAPI {
	t.Helper()
	clitest.FakeTerminal(t, &stdinIsTerminal, isTerminal)
	return clitest.NewSignedInAPI(t)
}

// writeFile writes content to name below dir and returns its path.
func writeFile(t *testing.T, dir, name, content string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}
