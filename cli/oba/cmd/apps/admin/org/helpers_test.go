package org

import (
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// newRoot builds a fresh "oba admin" tree with only this package's commands, as the oba root adds them.
func newRoot() *cobra.Command {
	return clitest.AppRoot("admin", NewCmd())
}

// runCLI runs "oba <args>" in process on a fresh tree; see clitest.RunCLI.
func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), stdin, args...)
}

// newOrgAPI starts a mock API serving the organization request schema and /users/me, signed in
// with defaultOrgID as the account's default organization ("" for none) and no terminal on stdin.
func newOrgAPI(t *testing.T, defaultOrgID string) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.ServeDocs("testdata/api-docs-organization.json")
	clitest.LoginTo(t, api.URL(), defaultOrgID)
	return api
}
