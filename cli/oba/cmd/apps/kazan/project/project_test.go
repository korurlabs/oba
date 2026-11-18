package project

import (
	"bytes"
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const (
	projectID   = "0d000000-0000-4000-8000-000000000001"
	projectJSON = `{"data":{"id":"` + projectID + `","name":"Billing","color":"#3366ff"}}`
)

func projectRoute(method string) string {
	return method + " /kaizen/boards/" + idOps + "/projects/" + projectID
}

func TestProjectList(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /kaizen/boards/"+idOps+"/projects", http.StatusOK,
		`{"data":[{"id":"p1","name":"Billing","color":"red"},{"id":"p2","name":"Infra","color":null}]}`)
	out, _, err := runCLI(t, "", "kazan", "project", "list")
	if err != nil {
		t.Fatal(err)
	}
	if out != "NAME     COLOR  ID\nBilling  red    p1\nInfra           p2\n" {
		t.Errorf("out =\n%q", out)
	}
}

func TestProjectGet(t *testing.T) {
	api := newAPI(t)
	api.Respond(projectRoute("GET"), http.StatusOK, projectJSON)
	out, _, err := runCLI(t, "", "kazan", "project", "get", projectID)
	if err != nil || out != "Name:  Billing\nID:    "+projectID+"\nColor: #3366ff\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestProjectGetForbiddenExitsThree(t *testing.T) {
	api := newAPI(t)
	api.Respond(projectRoute("GET"), http.StatusForbidden, `{"message":"no"}`)
	_, _, err := runCLI(t, "", "kazan", "project", "get", projectID)
	if client.ExitCode(err) != 3 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}

// --description is still accepted, so that older scripts run, and never sent.
func TestProjectCreate(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST /kaizen/boards/"+idOps+"/projects", http.StatusCreated, projectJSON)
	_, _, err := runCLI(t, "", "kazan", "project", "create", "--name", "Billing", "--color", "#3366ff", "--description", "x")
	if err != nil {
		t.Fatal(err)
	}
	body := api.Request("POST /kaizen/boards/" + idOps + "/projects").Body
	if body != `{"name":"Billing","color":"#3366ff"}` {
		t.Errorf("body = %s", body)
	}
}

func TestProjectCreateOutputAndNoColor(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST /kaizen/boards/"+idOps+"/projects", http.StatusCreated, projectJSON)
	out, _, err := runCLI(t, "", "kazan", "project", "create", "--name", "Billing")
	if err != nil || out != "Project created: Billing ("+projectID+")\n" {
		t.Errorf("out %q err %v", out, err)
	}
	if body := api.Request("POST /kaizen/boards/" + idOps + "/projects").Body; body != `{"name":"Billing"}` {
		t.Errorf("body = %s", body)
	}
}

func TestProjectCreateNeedsName(t *testing.T) {
	api := newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "project", "create")
	if err == nil || err.Error() != "--name is required" {
		t.Errorf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestProjectUpdateSendsOnlyGivenFields(t *testing.T) {
	api := newAPI(t)
	api.Respond(projectRoute("PUT"), http.StatusOK, projectJSON)
	out, _, err := runCLI(t, "", "kazan", "project", "update", projectID, "--color", "")
	if err != nil || out != "Project "+projectID+" updated.\n" {
		t.Errorf("out %q err %v", out, err)
	}
	if body := api.Request(projectRoute("PUT")).Body; body != `{"color":""}` {
		t.Errorf("body = %s", body)
	}
}

func TestProjectUpdateNothing(t *testing.T) {
	api := newAPI(t)
	_, _, err := runCLI(t, "", "kazan", "project", "update", projectID)
	if !errors.Is(err, terminal.ErrNothingToUpdate) || len(api.Calls()) != 0 {
		t.Errorf("err %v calls %v", err, api.Calls())
	}
}

func TestProjectDeleteWithoutTerminalDoesNotAsk(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api.Respond(projectRoute("DELETE"), http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "project", "delete", projectID)
	if err != nil || out != "Project "+projectID+" deleted.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestProjectDeleteAsksOnATerminal(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond(projectRoute("DELETE"), http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "y\n", "kazan", "project", "delete", projectID)
	if err != nil || out != "Delete project "+projectID+"? (y/n) Project "+projectID+" deleted.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

// With --json, a cancelled delete gives null, and nothing is deleted.
func TestProjectDeleteCancelled(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	out, _, err := runCLI(t, "n\n", "kazan", "project", "delete", projectID)
	if err != nil || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out %q err %v", out, err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("deleted: %v", call)
		}
	}
}

// --json never asks, as kaizen did: an agent reading JSON has nobody to answer.
func TestProjectDeleteJSONDoesNotAsk(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond(projectRoute("DELETE"), http.StatusOK, `{"data":null}`)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", "kazan", "project", "delete", projectID); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	api.Request(projectRoute("DELETE"))
	if strings.TrimSpace(buf.String()) != "null" {
		t.Errorf("json = %q", buf.String())
	}
}
