package shrine

import (
	"bytes"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const (
	idKorur        = "0e000000-0000-4000-8000-000000000001"
	idKorurTR      = "0e000000-0000-4000-8000-000000000002"
	legalEntityURL = "/legal-entities/" + idKorur
	korurJSON      = `{"id":"` + idKorur + `","shortName":"Korur B.V.","legalName":"Korur B.V.","country":"NLD",` +
		`"registrationNumber":"12345678","vatNumber":"NL001","street":"Damrak 1","postalCode":"1012 LG","city":"Amsterdam",` +
		`"governingLaw":"Dutch law","court":null,"createdAt":"2026-11-01T10:00:00Z","updatedAt":"2026-11-02T10:00:00Z"}`
)

// legalEntityAPI starts a mock API signed in to Acme, with stdin not a terminal.
func legalEntityAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

func TestLegalEntityList(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("GET /legal-entities", http.StatusOK, `{"data":[`+
		`{"id":"`+idKorur+`","shortName":"Korur B.V.","legalName":"Korur B.V.","country":"NLD","registrationNumber":"12345678","updatedAt":"2026-11-02T10:00:00Z"},`+
		`{"id":"`+idKorurTR+`","shortName":"Korur TR","legalName":"Korur Siber Güvenlik Limited Şirketi","country":"TUR","registrationNumber":null}]}`)
	out, _, err := runCLI(t, "", "legal-entity", "list")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(out, "SHORT NAME") || !strings.Contains(out, idKorurTR) ||
		!strings.Contains(out, "Korur Siber Güvenlik Limited Şirketi  TUR      -") {
		t.Errorf("out =\n%s", out)
	}
	if got := api.Request("GET /legal-entities").Org; got != clitest.OrgIDAcme {
		t.Errorf("org header = %q", got)
	}
}

func TestLegalEntityListEmpty(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("GET /legal-entities", http.StatusOK, `{"data":[]}`)
	out, _, err := runCLI(t, "", "legal-entity", "list")
	if err != nil || out != "No legal entities.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestLegalEntityGet(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("GET "+legalEntityURL, http.StatusOK, `{"data":`+korurJSON+`}`)
	out, _, err := runCLI(t, "", "legal-entity", "get", idKorur)
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"Short Name:           Korur B.V.\n", "Country:              NLD\n",
		"Postal Code:          1012 LG\n", "Court:                -\n"} {
		if !strings.Contains(out, want) {
			t.Errorf("out misses %q:\n%s", want, out)
		}
	}
}

func TestLegalEntityGetNotFoundExitsTwo(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("GET "+legalEntityURL, http.StatusNotFound, `{"message":"Legal entity not found"}`)
	_, _, err := runCLI(t, "", "legal-entity", "get", idKorur)
	if client.ExitCode(err) != 2 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}

func TestLegalEntityRefusesInvalidIDBeforeAnyCall(t *testing.T) {
	api := legalEntityAPI(t)
	for _, args := range [][]string{{"get", "korur"}, {"update", "korur", "--city", "x"}, {"delete", "korur", "--yes"}} {
		_, _, err := runCLI(t, "", append([]string{"legal-entity"}, args...)...)
		if err == nil || !strings.Contains(err.Error(), `invalid legal entity ID "korur"`) {
			t.Errorf("%v: err = %v", args, err)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestLegalEntityCreate(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("POST /legal-entities", http.StatusCreated, `{"data":`+korurJSON+`}`)
	out, _, err := runCLI(t, "", "legal-entity", "create", "--short-name", "Korur B.V.", "--legal-name", "Korur B.V.",
		"--country", "NLD", "--city", "Amsterdam", "--court", "")
	if err != nil || out != `Created legal entity "Korur B.V." (`+idKorur+")\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	want := `{"shortName":"Korur B.V.","legalName":"Korur B.V.","country":"NLD","registrationNumber":null,"vatNumber":null,` +
		`"street":null,"postalCode":null,"city":"Amsterdam","governingLaw":null,"court":null}`
	if body := api.Request("POST /legal-entities").Body; body != want {
		t.Errorf("body = %s", body)
	}
}

func TestLegalEntityCreateChecksBeforeAnyCall(t *testing.T) {
	api := legalEntityAPI(t)
	for _, tt := range []struct {
		args    []string
		wantErr string
	}{
		{[]string{"--legal-name", "K", "--country", "NLD"}, "--short-name is required"},
		{[]string{"--short-name", "K", "--country", "NLD"}, "--legal-name is required"},
		{[]string{"--short-name", "K", "--legal-name", "K", "--country", " "}, "--country is required"},
	} {
		_, _, err := runCLI(t, "", append([]string{"legal-entity", "create"}, tt.args...)...)
		if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
			t.Errorf("%v: err = %v, want %q", tt.args, err, tt.wantErr)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestLegalEntityCreateConflict(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("POST /legal-entities", http.StatusConflict, `{"message":"Short name already taken","errorCode":"LGE_STA_001"}`)
	_, _, err := runCLI(t, "", "legal-entity", "create", "--short-name", "korur b.v.", "--legal-name", "K", "--country", "NLD")
	if err == nil || client.ExitCode(err) != 1 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}

// The API replaces every field, so update reads the entity and sends its stored values for every
// field that was not given.
func TestLegalEntityUpdateKeepsFieldsNotGiven(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("GET "+legalEntityURL, http.StatusOK, `{"data":`+korurJSON+`}`)
	api.Respond("PUT "+legalEntityURL, http.StatusOK, `{"data":`+korurJSON+`}`)
	out, _, err := runCLI(t, "", "legal-entity", "update", idKorur, "--city", "Rotterdam", "--street", "", "--court", "Rechtbank Rotterdam")
	if err != nil || out != `Updated legal entity "Korur B.V." (`+idKorur+")\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	want := `{"shortName":"Korur B.V.","legalName":"Korur B.V.","country":"NLD","registrationNumber":"12345678","vatNumber":"NL001",` +
		`"street":null,"postalCode":"1012 LG","city":"Rotterdam","governingLaw":"Dutch law","court":"Rechtbank Rotterdam"}`
	if body := api.Request("PUT " + legalEntityURL).Body; body != want {
		t.Errorf("body = %s", body)
	}
	if calls := api.Calls(); len(calls) != 2 || calls[0] != "GET "+legalEntityURL {
		t.Errorf("calls = %v", calls)
	}
}

func TestLegalEntityUpdateChecksBeforeAnyCall(t *testing.T) {
	api := legalEntityAPI(t)
	for _, tt := range []struct {
		args    []string
		wantErr string
	}{
		{nil, "Nothing to update."},
		{[]string{"--short-name", ""}, "--short-name cannot be empty"},
		{[]string{"--country", " "}, "--country cannot be empty"},
	} {
		_, _, err := runCLI(t, "", append([]string{"legal-entity", "update", idKorur}, tt.args...)...)
		if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
			t.Errorf("%v: err = %v, want %q", tt.args, err, tt.wantErr)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestLegalEntityUpdateJSONIsTheUpdatedEntity(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("GET "+legalEntityURL, http.StatusOK, `{"data":`+korurJSON+`}`)
	api.Respond("PUT "+legalEntityURL, http.StatusOK, `{"data":{"id":"`+idKorur+`","city":"Rotterdam"}}`)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", "legal-entity", "update", idKorur, "--city", "Rotterdam"); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	if got := strings.TrimSpace(buf.String()); !strings.Contains(got, `"city": "Rotterdam"`) || strings.Contains(got, "Amsterdam") {
		t.Errorf("json = %s", got)
	}
}

func TestLegalEntityDeleteAsksAndCancels(t *testing.T) {
	api := legalEntityAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("GET "+legalEntityURL, http.StatusOK, `{"data":`+korurJSON+`}`)
	out, _, err := runCLI(t, "n\n", "legal-entity", "delete", idKorur)
	if err != nil || !strings.Contains(out, `Delete legal entity "Korur B.V." (`+idKorur+`)?`) || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out %q err %v", out, err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("deleted: %v", call)
		}
	}
}

func TestLegalEntityDeleteAsksAndDeletes(t *testing.T) {
	api := legalEntityAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("GET "+legalEntityURL, http.StatusOK, `{"data":`+korurJSON+`}`)
	api.Respond("DELETE "+legalEntityURL, http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "y\n", "legal-entity", "delete", idKorur)
	if err != nil || !strings.HasSuffix(out, `Deleted legal entity "Korur B.V." (`+idKorur+")\n") {
		t.Errorf("out %q err %v", out, err)
	}
	api.Request("DELETE " + legalEntityURL)
}

func TestLegalEntityDeleteWithoutTerminalDoesNotAsk(t *testing.T) {
	api := legalEntityAPI(t)
	api.Respond("DELETE "+legalEntityURL, http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "legal-entity", "delete", idKorur)
	if err != nil || out != "Deleted legal entity "+idKorur+"\n" {
		t.Errorf("out %q err %v", out, err)
	}
	if calls := api.Calls(); len(calls) != 1 {
		t.Errorf("calls = %v", calls)
	}
}

func TestLegalEntityDeleteJSONDoesNotAsk(t *testing.T) {
	api := legalEntityAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("DELETE "+legalEntityURL, http.StatusOK, `{"data":null}`)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", "legal-entity", "delete", idKorur); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	if strings.TrimSpace(buf.String()) != "null" {
		t.Errorf("json = %q", buf.String())
	}
}
