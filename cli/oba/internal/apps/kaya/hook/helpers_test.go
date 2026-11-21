package hook

import (
	"bytes"
	"context"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/kayatest"
)

// run runs what `oba kaya <args>` runs for the hooks and for sync, which prepares their checkout,
// and returns stdout and stderr together.
func run(t *testing.T, stdin string, args ...string) (string, error) {
	t.Helper()
	switch {
	case len(args) == 1 && args[0] == "sync":
		return kayatest.Sync(t)
	case len(args) == 2 && args[0] == "hook":
		var out bytes.Buffer
		err := Run(context.Background(), args[1], strings.NewReader(stdin), &out, &out)
		return out.String(), err
	}
	t.Fatalf("run %v: only sync and hook <name> run here", args)
	return "", nil
}
