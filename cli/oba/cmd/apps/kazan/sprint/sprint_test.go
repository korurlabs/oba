package sprint

import (
	"bytes"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

func TestSprintList(t *testing.T) {
	newAPI(t)
	out, _, err := runCLI(t, "", "kazan", "sprint", "list")
	if err != nil {
		t.Fatal(err)
	}
	want := "NAME      STATUS   START  END\nSprint 1  ACTIVE   -      -\nSprint 2  PLANNED  -      -\n"
	if out != want {
		t.Errorf("out =\n%q", out)
	}
}

func TestSprintGetNotFoundExitsTwo(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+sprintsPath+"/"+idS2, http.StatusNotFound, `{"message":"gone"}`)
	_, _, err := runCLI(t, "", "kazan", "sprint", "get", "Sprint", "2")
	if client.ExitCode(err) != 2 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}

func TestSprintGet(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+sprintsPath+"/"+idS2, http.StatusOK, `{"data":{"id":"`+idS2+`","name":"Sprint 2","status":"PLANNED",`+
		`"goal":"Ship","boards":[{"id":"`+idOps+`","name":"Operations","prefix":"OPS"}]}}`)
	out, _, err := runCLI(t, "", "kazan", "sprint", "get", "sprint 2")
	if err != nil || !strings.Contains(out, "Goal:        Ship\n") || !strings.Contains(out, "Name:        Sprint 2\n") {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestSprintCreateWithLinkChildren(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+sprintsPath+"/child-boards", http.StatusOK, `{"data":[`+
		`{"id":"`+idMob+`","name":"Mobile","prefix":"MOB","canLink":true},`+
		`{"id":"c2","name":"Busy","prefix":"BSY","canLink":true,"activeSprintId":"x"}]}`)
	api.Respond("POST "+sprintsPath, http.StatusCreated, `{"data":{"id":"s9","name":"Sprint 5"}}`)
	out, _, err := runCLI(t, "", "kazan", "sprint", "create", "--name", "Sprint 5", "--goal", "Ship",
		"--start-date", "2026-11-23T09:00:00Z", "--link-children")
	if err != nil || out != "Created sprint: Sprint 5\nLinked to 1 child board(s)\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	want := `{"name":"Sprint 5","goal":"Ship","startDate":"2026-11-23T09:00:00Z","childBoardIds":["` + idMob + `"]}`
	if body := api.Request("POST " + sprintsPath).Body; body != want {
		t.Errorf("body = %s", body)
	}
}

// A script links no child board unless asked, and asks nothing.
func TestSprintCreateScriptLinksNoChildren(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+sprintsPath, http.StatusCreated, `{"data":{"id":"s9","name":"Sprint 5"}}`)
	if _, _, err := runCLI(t, "", "kazan", "sprint", "create", "--name", "Sprint 5"); err != nil {
		t.Fatal(err)
	}
	if body := api.Request("POST " + sprintsPath).Body; body != `{"name":"Sprint 5"}` {
		t.Errorf("body = %s", body)
	}
}

func TestSprintCreateChecksBeforeAnyCall(t *testing.T) {
	api := newAPI(t)
	for _, args := range [][]string{
		{"--goal", "x"},
		{"--name", "S", "--start-date", "next week"},
		{"--name", "S", "--link-children", "--child-boards", "MOB"},
	} {
		if _, _, err := runCLI(t, "", append([]string{"kazan", "sprint", "create"}, args...)...); err == nil {
			t.Errorf("%v: want an error", args)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestSprintUpdateSeriesName(t *testing.T) {
	api := newAPI(t)
	api.Respond("PUT "+sprintsPath+"/"+idS1, http.StatusOK, `{"data":{"id":"`+idS1+`","name":"Weekly 1"}}`)
	out, _, err := runCLI(t, "", "kazan", "sprint", "update", "Sprint 1", "--series-name", "Weekly")
	if err != nil || out != "Updated sprint: Weekly 1\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if body := api.Request("PUT " + sprintsPath + "/" + idS1).Body; body != `{"seriesName":"Weekly"}` {
		t.Errorf("body = %s", body)
	}
}

func TestSprintUpdateWithNothing(t *testing.T) {
	newAPI(t)
	if _, _, err := runCLI(t, "", "kazan", "sprint", "update", "Sprint 1"); err == nil {
		t.Error("want an error")
	}
}

func TestSprintStart(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+sprintsPath+"/"+idS2+"/start", http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "sprint", "start", "Sprint 2")
	if err != nil || out != "Started sprint Sprint 2 (PLANNED)\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestSprintCompleteToASprintOfAnotherBoard(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /kaizen/boards/"+idMob+"/sprints", http.StatusOK, `{"data":[{"id":"m1","name":"Mob 1","status":"PLANNED"}]}`)
	api.Respond("POST "+sprintsPath+"/"+idS1+"/complete", http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "sprint", "complete", "sprint", "--tickets-to", "sprint",
		"--target-sprint", "Mob 1", "--target-board", "MOB")
	if err != nil || out != "Completed sprint Sprint 1 (ACTIVE)\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	want := `{"ticketAction":"MOVE_TO_SPRINT","targetSprintId":"m1","targetBoardId":"` + idMob + `"}`
	if body := api.Request("POST " + sprintsPath + "/" + idS1 + "/complete").Body; body != want {
		t.Errorf("body = %s", body)
	}
}

func TestSprintCompleteDeleteNeedsYes(t *testing.T) {
	api := newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "sprint", "complete", "sprint", "--tickets-to", "delete")
	if err == nil || !strings.Contains(err.Error(), "pass --yes") || len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}

func TestSprintLink(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+sprintsPath+"/"+idS2+"/bulk-link", http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "sprint", "link", "Sprint 2", "--to-board", "MOB")
	if err != nil || out != "Linked sprint Sprint 2 (PLANNED) to 1 board(s)\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if body := api.Request("POST " + sprintsPath + "/" + idS2 + "/bulk-link").Body; body != `{"boardIds":["`+idMob+`"]}` {
		t.Errorf("body = %s", body)
	}
}

func TestSprintUnlinkFromTheOnlyBoardNeedsYes(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+sprintsPath+"/"+idS2, http.StatusOK, `{"data":{"id":"`+idS2+`","name":"Sprint 2","boards":[{"id":"`+idOps+`"}]}}`)
	api.Respond("POST "+sprintsPath+"/"+idS2+"/unlink", http.StatusOK, `{"data":null}`)
	_, _, err := runCLI(t, "", "kazan", "sprint", "unlink", "Sprint 2")
	if err == nil || !strings.Contains(err.Error(), "pass --yes") {
		t.Fatalf("err = %v", err)
	}
	out, _, err := runCLI(t, "", "kazan", "sprint", "unlink", "Sprint 2", "--tickets-to", "sprint", "--target-sprint", "Sprint 1", "--yes")
	if err != nil || out != "Unlinked sprint Sprint 2 (PLANNED) from the board\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	want := `{"transfer":{"boardId":"` + idOps + `","sprintId":"` + idS1 + `"}}`
	if body := api.Request("POST " + sprintsPath + "/" + idS2 + "/unlink").Body; body != want {
		t.Errorf("body = %s", body)
	}
}

func TestSprintUnlinkIntoItself(t *testing.T) {
	newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "sprint", "unlink", "Sprint 2", "--tickets-to", "sprint", "--target-sprint", "sprint 2", "--yes")
	if err == nil || !strings.Contains(err.Error(), "must be another sprint") {
		t.Errorf("err = %v", err)
	}
}

func TestSprintDeleteToASprint(t *testing.T) {
	api := newAPI(t)
	var query string
	captureQuery(api, "DELETE "+sprintsPath+"/"+idS2, `{"data":null}`, &query)
	out, _, err := runCLI(t, "", "kazan", "sprint", "delete", "Sprint 2", "--tickets-to", "sprint", "--target-sprint", "Sprint 1")
	if err != nil || out != "Deleted sprint Sprint 2 (PLANNED)\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if query != "targetSprintId="+idS1+"&ticketAction=MOVE_TO_SPRINT" {
		t.Errorf("query = %s", query)
	}
}

func TestSprintDeleteAsksAndCancels(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	out, _, err := runCLI(t, "n\n", "kazan", "sprint", "delete", "Sprint 2")
	if err != nil || !strings.HasSuffix(out, "Cancelled.\n") || !strings.Contains(out, "Delete sprint 'Sprint 2'?") {
		t.Errorf("out %q err %v", out, err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("deleted: %v", call)
		}
	}
}

// --json never asks: an agent reading JSON has nobody to answer.
func TestSprintDeleteJSONDoesNotAsk(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	var query string
	captureQuery(api, "DELETE "+sprintsPath+"/"+idS2, `{"data":null}`, &query)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", "kazan", "sprint", "delete", "Sprint 2"); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	if query != "ticketAction=MOVE_TO_BACKLOG" || strings.TrimSpace(buf.String()) != "null" {
		t.Errorf("query %q json %q", query, buf.String())
	}
}

func TestSprintRestoreByID(t *testing.T) {
	api := newAPI(t)
	const deleted = "0e000000-0000-4000-8000-000000000009"
	api.Respond("POST "+sprintsPath+"/"+deleted+"/restore", http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "sprint", "restore", deleted)
	if err != nil || out != "Restored sprint "+deleted+"\n" {
		t.Errorf("out %q err %v", out, err)
	}
}
