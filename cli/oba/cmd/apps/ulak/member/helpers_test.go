package member

import (
	"net/http"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	membersPath = "/organizations/" + clitest.OrgIDAcme + "/members"
	anaID       = "aaaaaaaa-0000-4000-8000-000000000001"
	anaPath     = membersPath + "/" + anaID

	// membersJSON lists four members out of order: a GUEST, an OWNER, two MEMBERs with e-mails
	// in mixed case, and Ana, a MEMBER without a last name.
	membersJSON = `{"data":[` +
		`{"id":"m4","userId":"u-4","email":"zed@example.com","firstName":"Zed","lastName":"Guest","role":"GUEST","status":"ACTIVE"},` +
		`{"id":"m1","userId":"u-1","email":"owner@example.com","firstName":"Olga","lastName":"Owner","role":"OWNER","status":"ACTIVE"},` +
		`{"id":"m3","userId":"u-3","email":"Bob@example.com","firstName":null,"lastName":null,"role":"MEMBER","status":"ACTIVE"},` +
		`{"id":"m2","userId":"` + anaID + `","email":"ana@example.com","firstName":"Ana","lastName":null,"role":"MEMBER","status":"ACTIVE"}]}`
)

// newRoot builds a fresh "oba ulak" tree with only this package's commands, as the oba root adds them.
func newRoot() *cobra.Command {
	return clitest.AppRoot("ulak", NewCmd())
}

// runCLI runs "oba <args>" in process on a fresh tree; see clitest.RunCLI.
func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), stdin, args...)
}

// runCLILines runs "oba <args>" like runCLI, with stdin delivered one line per read; see
// clitest.RunCLILines.
func runCLILines(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLILines(t, newRoot(), stdin, args...)
}

// newMemberAPI starts a mock API serving /users/me and the members of Acme, signed in to
// clitest.OrgIDAcme, with no terminal on stdin.
func newMemberAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	api.Respond("GET "+membersPath, http.StatusOK, membersJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}
