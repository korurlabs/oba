package roadmap

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

const (
	idOps    = "0a000000-0000-4000-8000-000000000001"
	treeJSON = `{"data":[{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
	roadmapRoute = "GET /kazan/boards/" + idOps + "/roadmap"
)

func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	old := width
	width = func() int { return 80 }
	t.Cleanup(func() { width = old })
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kazan/boards", http.StatusOK, treeJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	if err := config.Save(config.File{KazanBoards: map[string]string{clitest.OrgIDAcme: idOps}}); err != nil {
		t.Fatal(err)
	}
	return api
}

func runCLI(t *testing.T, args ...string) (string, string, error) {
	t.Helper()
	root := clitest.AppRoot("kazan", NewCmd())
	group, _, _ := root.Find([]string{"kazan"})
	boardflag.Add(group)
	return clitest.RunCLI(t, root, "", args...)
}

func TestRoadmapSendsTheWindow(t *testing.T) {
	api := newAPI(t)
	var query string
	api.Handle(roadmapRoute, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":{"from":"2026-10-01","to":"2026-12-31","boards":[{"id":"`+idOps+`","name":"Operations",`+
			`"prefix":"OPS","milestones":[{"id":"m1","name":"Beta","startDate":"2026-10-10","targetDate":"2026-11-20",`+
			`"doneCount":3,"totalCount":10,"health":"ON_TRACK"}]}]}}`)
	})
	out, _, err := runCLI(t, "kazan", "roadmap", "--children", "--from", "2026-10-01", "--to", " 2026-12-31")
	if err != nil {
		t.Fatal(err)
	}
	if query != "from=2026-10-01&includeChildBoards=true&to=2026-12-31" {
		t.Errorf("query = %s", query)
	}
	for _, want := range []string{"Roadmap: Operations (OPS)  2026-10-01 .. 2026-12-31\n", "OPS  Operations\n", "Beta  3/10  ON_TRACK  2026-11-20"} {
		if !strings.Contains(out, want) {
			t.Errorf("out misses %q:\n%s", want, out)
		}
	}
}

func TestRoadmapChecksTheWindowBeforeAnyCall(t *testing.T) {
	api := newAPI(t)
	_, _, err := runCLI(t, "kazan", "roadmap", "--from", "2026-12-31", "--to", "2026-10-01")
	if err == nil || !strings.Contains(err.Error(), "is after --to") || len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}

func TestRoadmapNotFoundExitsTwo(t *testing.T) {
	api := newAPI(t)
	api.Respond(roadmapRoute, http.StatusNotFound, `{"message":"no"}`)
	_, _, err := runCLI(t, "kazan", "roadmap")
	if client.ExitCode(err) != 2 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}
