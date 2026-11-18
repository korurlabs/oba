package outreach

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

// runCLI runs "oba <args>" in process on a fresh tree; see clitest.RunCLI.
func runCLI(t *testing.T, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), "", args...)
}

// writeFile writes content to name below dir, creating the directories, and returns its path.
func writeFile(t *testing.T, dir, name, content string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}
