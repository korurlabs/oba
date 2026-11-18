package pr

import (
	"bytes"
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const (
	idShare   = "55555555-5555-4555-8555-555555555555"
	idAda     = "77777777-7777-4777-8777-777777777777"
	idBob     = "88888888-8888-4888-8888-888888888888"
	idOps     = "0a000000-0000-4000-8000-000000000001"
	idTicket  = "0b000000-0000-4000-8000-000000000001"
	idTicket2 = "0b000000-0000-4000-8000-000000000002"

	sharesPath  = "/kaizen/pull-request-shares"
	sharePath   = sharesPath + "/" + idShare
	reposPath   = "/kaizen/github/repositories"
	pullsPath   = reposPath + "/101/pull-requests"
	membersPath = "/organizations/" + clitest.OrgIDAcme + "/members"

	reposJSON = `{"data":[{"githubRepositoryId":102,"fullName":"senseylabs/agents","privateRepository":false},` +
		`{"githubRepositoryId":101,"fullName":"senseylabs/village","privateRepository":true}]}`
	membersJSON = `{"data":[` +
		`{"userId":"u1","email":"a@b.c","firstName":"Me","role":"ADMIN","status":"ACTIVE"},` +
		`{"userId":"` + idAda + `","email":"ada@x.io","firstName":"Ada","lastName":"Lovelace","role":"MEMBER","status":"ACTIVE"},` +
		`{"userId":"` + idBob + `","email":"bob@x.io","firstName":"Bob","lastName":"Stone","role":"MEMBER","status":"ACTIVE"},` +
		`{"userId":"g1","email":"guest@x.io","role":"GUEST","status":"ACTIVE"},` +
		`{"userId":"d1","email":"gone@x.io","role":"MEMBER","status":"DISABLED"}]}`
	treeJSON = `{"data":[{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
	ticketJSON = `{"data":{"id":"` + idTicket + `","key":"OPS-12","board":{"id":"` + idOps + `","name":"Operations","prefix":"OPS"}}}`

	// shareJSON is one share: reviewers Ada (approved) and Bob (pending), related ticket OPS-12,
	// a live card.
	shareJSON = `{"data":{"id":"` + idShare + `","authorId":"u1","authorFirstName":"Grace","authorLastName":"Hopper",` +
		`"repositoryFullName":"senseylabs/village","number":688,"title":"Add sidebar",` +
		`"url":"https://github.com/senseylabs/village/pull/688","status":"IN_REVIEW","note":"keep me",` +
		`"reviewers":[{"userId":"` + idAda + `","firstName":"Ada","lastName":"Lovelace","approvedAt":"2026-10-27T10:00:00Z"},` +
		`{"userId":"` + idBob + `","firstName":"Bob","lastName":"Stone","approvedAt":null}],` +
		`"relatedTickets":[{"ticketId":"` + idTicket + `","ticketKey":"OPS-12","ticketTitle":"Sidebar"}],` +
		`"createdAt":"2026-10-27T09:30:00Z","editedAt":null,"summaryText":"CARD TEXT\n",` +
		`"channelId":"ch1","messageId":"msg1"}}`
)

// pullJSON is village#688 with GitHub state state.
func pullJSON(state string) string {
	return `{"data":{"number":688,"title":"Add sidebar","state":"` + state + `","authorLogin":"ghopper",` +
		`"updatedAt":"2026-10-27T09:30:00Z","url":"https://github.com/senseylabs/village/pull/688"}}`
}

// newAPI starts a mock API with the profile, the members, the repositories, the board tree,
// ticket OPS-12, village#688 as OPEN and the share, signed in to clitest.OrgIDAcme, with no
// terminal on stdin.
func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	t.Setenv("OBA_KAZAN_WEB_URL", "https://kazan.example.com")
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	api.Respond("GET "+membersPath, http.StatusOK, membersJSON)
	api.Respond("GET "+reposPath, http.StatusOK, reposJSON)
	api.Respond("GET "+pullsPath+"/688", http.StatusOK, pullJSON("OPEN"))
	api.Respond("GET /kaizen/boards", http.StatusOK, treeJSON)
	api.Respond("GET /kaizen/tickets/by-key/OPS-12", http.StatusOK, ticketJSON)
	api.Respond("GET "+sharePath, http.StatusOK, shareJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

// captureQuery serves body on route and keeps the query of the last request.
func captureQuery(api *clitest.MockAPI, route, body string) *string {
	var query string
	api.Handle(route, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, body)
	})
	return &query
}

func run(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	root := clitest.AppRoot("kazan", NewCmd())
	return clitest.RunCLILines(t, root, stdin, append([]string{"kazan", "pr"}, args...)...)
}

// captureJSON turns on --json output for the test and returns a function that flushes it.
func captureJSON(t *testing.T) func() string {
	t.Helper()
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	return func() string {
		if err := jsonout.Flush(); err != nil {
			t.Fatal(err)
		}
		return buf.String()
	}
}

// writes returns the calls that are not GETs.
func writes(api *clitest.MockAPI) []string {
	var out []string
	for _, call := range api.Calls() {
		if !strings.HasPrefix(call, "GET ") {
			out = append(out, call)
		}
	}
	return out
}
