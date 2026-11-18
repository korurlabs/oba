package updates

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// currentUpdateJSON is update idUpdate with OPS-7 at 40% with a note, a line whose ticket is
// gone, focus and questions.
const currentUpdateJSON = `{"data":{"id":"` + idUpdate + `","focus":"Menu","questions":"Q?","items":[` +
	`{"ticketId":"` + idTicket1 + `","ticketKey":"OPS-7","ticketTitle":"Menu","percentage":40,"note":"n"},` +
	`{"ticketId":null,"ticketKey":"OLD-1","percentage":10}]}}`

func editAPI(t *testing.T, current string) *clitest.MockAPI {
	t.Helper()
	api := newAPI(t)
	api.Respond("GET "+updatePath, http.StatusOK, current)
	api.Respond("PUT "+updatePath, http.StatusOK, `{"data":{"id":"`+idUpdate+`","summaryText":"SAVED"}}`)
	return api
}

func TestEditSendsTheFullContent(t *testing.T) {
	api := editAPI(t, currentUpdateJSON)
	out, errOut, err := run(t, "", "edit", idUpdate, "--percent", "OPS-7=60")
	if err != nil {
		t.Fatal(err)
	}
	want := `{"tickets":[{"ticketId":"` + idTicket1 + `","percentage":60,"note":"n"}],"focus":"Menu","questions":"Q?"}`
	if got := api.Request("PUT " + updatePath).Body; got != want {
		t.Errorf("body = %s\nwant   %s", got, want)
	}
	if out != "SAVED\n" || !strings.Contains(errOut, "Saved update "+idUpdate) {
		t.Errorf("out %q stderr %q", out, errOut)
	}
}

func TestEditAddsALineSetsANoteAndClearsTheQuestions(t *testing.T) {
	api := editAPI(t, currentUpdateJSON)
	if _, _, err := run(t, "", "edit", idUpdate, "--add", "KAGI-2", "--note", "KAGI-2=new", "--questions", ""); err != nil {
		t.Fatal(err)
	}
	want := `{"tickets":[{"ticketId":"` + idTicket1 + `","percentage":40,"note":"n"},{"ticketId":"` + idTicket2 +
		`","percentage":55,"note":"new"}],"focus":"Menu"}`
	if got := api.Request("PUT " + updatePath).Body; got != want {
		t.Errorf("body = %s\nwant   %s", got, want)
	}
}

func TestEditSendsTheNewFocusTrimmed(t *testing.T) {
	api := editAPI(t, currentUpdateJSON)
	if _, _, err := run(t, "", "edit", idUpdate, "--focus", "  New  "); err != nil {
		t.Fatal(err)
	}
	if got := api.Request("PUT " + updatePath).Body; !strings.Contains(got, `"focus":"New"`) {
		t.Errorf("body = %s", got)
	}
}

func TestEditOfAnUpdateWithoutFocusNeedsFocus(t *testing.T) {
	api := editAPI(t, `{"data":{"id":"`+idUpdate+`","items":[{"ticketId":"`+idTicket1+`","ticketKey":"OPS-7","percentage":40}]}}`)
	_, _, err := run(t, "", "edit", idUpdate, "--percent", "OPS-7=50")
	if err == nil || !strings.Contains(err.Error(), "pass --focus") {
		t.Fatalf("err = %v", err)
	}
	if len(writes(api)) != 0 {
		t.Error("nothing may be written")
	}
}

func TestEditFailsBeforeWritingWhenEveryLineIsRemoved(t *testing.T) {
	api := editAPI(t, currentUpdateJSON)
	_, _, err := run(t, "", "edit", idUpdate, "--remove", "ops-7")
	if err == nil || !strings.Contains(err.Error(), "at least one ticket") {
		t.Fatalf("err = %v", err)
	}
	if len(writes(api)) != 0 {
		t.Error("nothing may be written")
	}
}

func TestEditRemoveOfATicketNotOnTheUpdateFails(t *testing.T) {
	editAPI(t, currentUpdateJSON)
	_, _, err := run(t, "", "edit", idUpdate, "--remove", "KAGI-2")
	if err == nil || !strings.Contains(err.Error(), "not on the update (tickets: OPS-7)") {
		t.Fatalf("err = %v", err)
	}
}

func TestEditAddOfATicketAlreadyOnTheUpdateFails(t *testing.T) {
	editAPI(t, currentUpdateJSON)
	_, _, err := run(t, "", "edit", idUpdate, "--add", "OPS-7")
	if err == nil || !strings.Contains(err.Error(), "already on the update") {
		t.Fatalf("err = %v", err)
	}
}

func TestEditOnATerminalWithoutFlagsAsksAndConfirms(t *testing.T) {
	api := editAPI(t, currentUpdateJSON)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("POST "+updatePath+"/preview", http.StatusOK, `{"data":{"summaryText":"PREVIEW"}}`)

	// Percentage 70, note cleared, focus kept, questions changed, then yes.
	out, _, err := run(t, "70\n-\n\nNone\ny\n", "edit", idUpdate)
	if err != nil {
		t.Fatal(err)
	}
	want := `{"tickets":[{"ticketId":"` + idTicket1 + `","percentage":70}],"focus":"Menu","questions":"None"}`
	if got := api.Request("PUT " + updatePath).Body; got != want {
		t.Errorf("body = %s\nwant   %s", got, want)
	}
	if api.Request("POST "+updatePath+"/preview").Body != want || !strings.Contains(out, "Save this update? (y/n)") {
		t.Errorf("out = %q", out)
	}
}

func TestEditJSONIsTheSavedUpdate(t *testing.T) {
	editAPI(t, currentUpdateJSON)
	flush := captureJSON(t)
	if _, _, err := run(t, "", "edit", idUpdate, "--focus", "X"); err != nil {
		t.Fatal(err)
	}
	if got := flush(); !strings.Contains(got, `"summaryText": "SAVED"`) || strings.Contains(got, `"focus": "Menu"`) {
		t.Errorf("json =\n%s", got)
	}
}
