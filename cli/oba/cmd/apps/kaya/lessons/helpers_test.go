package lessons

import (
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

// run runs oba kaya lessons.
func run(t *testing.T, stdin string, args ...string) (string, error) {
	t.Helper()
	return kayatest.Run(t, []*cobra.Command{NewCmd()}, stdin, args...)
}
