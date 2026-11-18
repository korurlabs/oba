package shrine

import (
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// runCLI runs "oba <args>" in process on a fresh command tree with only the platform commands, as
// the oba root adds them.
func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(Commands()...)
	return clitest.RunCLI(t, root, stdin, args...)
}
