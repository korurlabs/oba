package milestone

import (
	"bytes"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

func TestMilestoneListRelated(t *testing.T) {
	api := newAPI(t)
	var query string
	api.Handle("GET "+milestonesPath, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, milestonesJSON)
	})
	out, _, err := runCLI(t, "", "kazan", "milestone", "list", "--status", "open", "--related")
	if err != nil {
		t.Fatal(err)
	}
	if query != "includeRelatedBoards=true&status=OPEN" {
		t.Errorf("query = %s", query)
	}
	if !strings.HasPrefix(out, "ID ") || !strings.Contains(out, "BOARD") ||
		!strings.Contains(out, "Beta  OPEN    -           2026-12-01  3/10      ON_TRACK  Operations (OPS)") {
		t.Errorf("out =\n%s", out)
	}
}

func TestMilestoneListEmpty(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+milestonesPath, http.StatusOK, `{"data":[]}`)
	out, _, err := runCLI(t, "", "kazan", "milestone", "list")
	if err != nil || out != "No milestones found.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestMilestoneGet(t *testing.T) {
	api := newAPI(t)
	api.Respond(milestoneRoute("GET", idGA, ""), http.StatusOK, `{"data":`+strings.TrimSuffix(gaJSON, "}")+
		`,"dependsOn":[{"milestoneId":"`+idBeta+`","name":"Beta","targetDate":"2026-12-01","conflict":true}]}}`)
	out, _, err := runCLI(t, "", "kazan", "milestone", "get", "ga")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"Name:        GA\n", "Progress:    0/4 done\n", "Start Date:  2026-11-25\n",
		"Depends on:\n  Beta (target 2026-12-01, " + idBeta + ") [CONFLICT]\n"} {
		if !strings.Contains(out, want) {
			t.Errorf("out misses %q:\n%s", want, out)
		}
	}
}

func TestMilestoneGetAmbiguousName(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+milestonesPath, http.StatusOK, `{"data":[`+betaJSON+`,{"id":"`+idBeta2+`","name":"beta","targetDate":"2027-02-01","boardId":"`+idMob+`"}]}`)
	_, _, err := runCLI(t, "", "kazan", "milestone", "get", "BETA")
	if err == nil || !strings.Contains(err.Error(), `2 milestones are named "BETA"`) || !strings.Contains(err.Error(), idBeta2) {
		t.Errorf("err = %v", err)
	}
}

func TestMilestoneGetForbiddenExitsThree(t *testing.T) {
	api := newAPI(t)
	api.Respond(milestoneRoute("GET", idBeta, ""), http.StatusForbidden, `{"message":"no"}`)
	_, _, err := runCLI(t, "", "kazan", "milestone", "get", "Beta")
	if client.ExitCode(err) != 3 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}

func TestMilestoneCreate(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+milestonesPath, http.StatusCreated, `{"data":`+gaJSON+`}`)
	out, _, err := runCLI(t, "", "kazan", "milestone", "create", "--name", "GA", "--target-date", "2027-01-15",
		"--start-date", "2026-11-25", "--color", "#22C55E")
	if err != nil || out != `Created milestone "GA" (`+idGA+")\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	want := `{"name":"GA","startDate":"2026-11-25","targetDate":"2027-01-15","color":"#22C55E"}`
	if body := api.Request("POST " + milestonesPath).Body; body != want {
		t.Errorf("body = %s", body)
	}
}

func TestMilestoneCreateChecksBeforeAnyCall(t *testing.T) {
	api := newAPI(t)
	for _, args := range [][]string{
		{"--name", "GA"},
		{"--target-date", "2027-01-15"},
		{"--name", "GA", "--target-date", "15.01.2027"},
		{"--name", "GA", "--target-date", "2027-01-15", "--color", "green"},
	} {
		if _, _, err := runCLI(t, "", append([]string{"kazan", "milestone", "create"}, args...)...); err == nil {
			t.Errorf("%v: want an error", args)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestMilestoneUpdateClearsWithEmptyValues(t *testing.T) {
	api := newAPI(t)
	api.Respond(milestoneRoute("PUT", idGA, ""), http.StatusOK, `{"data":`+gaJSON+`}`)
	out, _, err := runCLI(t, "", "kazan", "milestone", "update", "GA", "--start-date", "", "--description", "")
	if err != nil || out != `Updated milestone "GA" (`+idGA+")\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if body := api.Request(milestoneRoute("PUT", idGA, "")).Body; body != `{"clearStartDate":true,"clearDescription":true}` {
		t.Errorf("body = %s", body)
	}
}

func TestMilestoneUpdateWithNothing(t *testing.T) {
	api := newAPI(t)
	if _, _, err := runCLI(t, "", "kazan", "milestone", "update", "GA"); err == nil || len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}

func TestMilestoneStateChanges(t *testing.T) {
	for _, tc := range []struct{ verb, suffix, done string }{
		{"close", "/close", "Closed"}, {"reopen", "/reopen", "Reopened"}, {"restore", "/restore", "Restored"},
	} {
		t.Run(tc.verb, func(t *testing.T) {
			api := newAPI(t)
			api.Respond(milestoneRoute("POST", idBeta, tc.suffix), http.StatusOK, `{"data":null}`)
			out, _, err := runCLI(t, "", "kazan", "milestone", tc.verb, "Beta")
			if err != nil || out != tc.done+` milestone "Beta" (`+idBeta+")\n" {
				t.Errorf("out %q err %v", out, err)
			}
		})
	}
}

// A deleted milestone is no longer listed, so its id is sent as given.
func TestMilestoneRestoreUnlistedID(t *testing.T) {
	api := newAPI(t)
	const deleted = "0f000000-0000-4000-8000-000000000009"
	api.Respond(milestoneRoute("POST", deleted, "/restore"), http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "milestone", "restore", deleted)
	if err != nil || out != "Restored milestone "+deleted+"\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestMilestoneDeleteAsksAndCancels(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	out, _, err := runCLI(t, "n\n", "kazan", "milestone", "delete", "Beta")
	if err != nil || !strings.Contains(out, "and detach its tickets?") || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out %q err %v", out, err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("deleted: %v", call)
		}
	}
}

func TestMilestoneDeleteJSONDoesNotAsk(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond(milestoneRoute("DELETE", idBeta, ""), http.StatusOK, `{"data":null}`)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", "kazan", "milestone", "delete", "Beta"); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	api.Request(milestoneRoute("DELETE", idBeta, ""))
	if strings.TrimSpace(buf.String()) != "null" {
		t.Errorf("json = %q", buf.String())
	}
}

func TestMilestoneLinkTicketsByKey(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /kazan/tickets/by-key/OPS-1", http.StatusOK, `{"data":{"id":"t1","board":{"id":"`+idOps+`"}}}`)
	api.Respond("GET /kazan/tickets/by-key/MOB-2", http.StatusOK, `{"data":{"id":"t2","board":{"id":"`+idMob+`"}}}`)
	api.Respond(milestoneRoute("POST", idBeta, "/link"), http.StatusOK, `{"data":`+strings.Replace(betaJSON, `"doneCount":3`, `"doneCount":5`, 1)+`}`)
	out, _, err := runCLI(t, "", "kazan", "milestone", "link", "Beta", "--tickets", "ops-1, MOB-2")
	if err != nil || out != `Linked 2 tickets to milestone "Beta" (`+idBeta+") (5/10 done)\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if body := api.Request(milestoneRoute("POST", idBeta, "/link")).Body; body != `{"ticketIds":["t1","t2"]}` {
		t.Errorf("body = %s", body)
	}
}

func TestMilestoneUnlinkNeedsTickets(t *testing.T) {
	newAPI(t)
	if _, _, err := runCLI(t, "", "kazan", "milestone", "unlink", "Beta"); err == nil || !strings.Contains(err.Error(), "--tickets is required") {
		t.Errorf("err = %v", err)
	}
}

func TestMilestoneDependWithConflict(t *testing.T) {
	api := newAPI(t)
	api.Respond(milestoneRoute("POST", idGA, "/dependencies/"+idBeta), http.StatusOK, `{"data":`+strings.TrimSuffix(gaJSON, "}")+
		`,"dependsOn":[{"milestoneId":"`+idBeta+`","name":"Beta","targetDate":"2026-12-01","conflict":true}]}}`)
	out, _, err := runCLI(t, "", "kazan", "milestone", "depend", "GA", "--on", "beta")
	if err != nil {
		t.Fatal(err)
	}
	want := `Milestone "GA" (` + idGA + `) now depends on "Beta" (` + idBeta + ")\n" +
		"Depends on:\n  Beta (target 2026-12-01, " + idBeta + ") [CONFLICT]\n" +
		`Warning: "GA" (` + idGA + `) starts before "Beta" is due on 2026-12-01. Dates were not changed.` + "\n"
	if out != want {
		t.Errorf("out =\n%s\nwant\n%s", out, want)
	}
}

func TestMilestoneUndepend(t *testing.T) {
	api := newAPI(t)
	api.Respond(milestoneRoute("DELETE", idGA, "/dependencies/"+idBeta), http.StatusOK, `{"data":`+gaJSON+`}`)
	out, _, err := runCLI(t, "", "kazan", "milestone", "undepend", "GA", "--on", idBeta)
	if err != nil || out != `Milestone "GA" (`+idGA+`) no longer depends on "Beta" (`+idBeta+")\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestMilestoneDependOnItself(t *testing.T) {
	newAPI(t)
	if _, _, err := runCLI(t, "", "kazan", "milestone", "depend", "GA", "--on", "ga"); err == nil ||
		!strings.Contains(err.Error(), "cannot depend on itself") {
		t.Errorf("err = %v", err)
	}
}
