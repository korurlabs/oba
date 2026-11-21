package delivery

import (
	"testing"

	"github.com/korurlabs/oba-cli/cmd/apps/kaya/hook"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

// run runs oba kaya with the delivery commands and the hooks, which read what they deliver.
func run(t *testing.T, stdin string, args ...string) (string, error) {
	t.Helper()
	return kayatest.Run(t, append(NewCmds(), hook.NewCmd()), stdin, args...)
}
