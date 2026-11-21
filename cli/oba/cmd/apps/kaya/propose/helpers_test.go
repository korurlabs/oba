package propose

import (
	"testing"

	deliverycmd "github.com/korurlabs/oba-cli/cmd/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya/hook"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

// run runs oba kaya with propose, the delivery commands that prepare the checkout it proposes
// from, and the hooks whose guard it relies on.
func run(t *testing.T, stdin string, args ...string) (string, error) {
	t.Helper()
	return kayatest.Run(t, append(deliverycmd.NewCmds(), hook.NewCmd(), NewCmd()), stdin, args...)
}
