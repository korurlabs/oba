package relation

import (
	"net/http"
	"net/url"
	"strings"
	"sync"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// listEnv serves GET /otak/relations from per-type pages and records the query of every call.
type listEnv struct {
	api     *clitest.MockAPI
	mu      sync.Mutex
	queries []url.Values
}

func (e *listEnv) typesCalled() string {
	e.mu.Lock()
	defer e.mu.Unlock()
	var types []string
	for _, q := range e.queries {
		types = append(types, q.Get("type"))
	}
	return strings.Join(types, ",")
}

// newListEnv serves the api document with the nine stages and, per type, the given pages (each
// a JSON body); a type without pages answers an empty last page.
func newListEnv(t *testing.T, pages map[string][]string) *listEnv {
	t.Helper()
	api := relationFixture(t)
	e := &listEnv{api: api}
	api.Handle("GET /otak/relations", func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		e.mu.Lock()
		e.queries = append(e.queries, q)
		e.mu.Unlock()
		list := pages[q.Get("type")]
		page := 1
		if q.Get("page") == "2" {
			page = 2
		}
		if len(list) < page {
			clitest.WriteJSON(w, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
			return
		}
		clitest.WriteJSON(w, list[page-1])
	})
	return e
}

const (
	eventID       = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
	allStages     = "NEW,QUALIFIED,CONTACTED,WARM,PROPOSAL,CLOSED,LOST,PARKED,DIRECT"
	leadPage      = `{"data":[{"id":"l1","name":"Lost Lead","type":"LEAD","stage":"LOST","website":"https://lost.nl","owner":{"name":"Ann"}}],"pagination":{"currentPage":1,"last":true}}`
	supplierPage  = `{"data":[{"id":"s1","name":"Parts BV","type":"SUPPLIER","stage":null,"website":null,"owner":null}],"pagination":{"currentPage":1,"last":true}}`
	customersPage = `{"data":[{"id":"c1","name":"Closed Co","type":"CUSTOMER","stage":"CLOSED"},{"id":"c2","name":"Direct Co","type":"CUSTOMER","stage":"DIRECT"}],"pagination":{"currentPage":1,"last":true}}`
)

func TestRelationListEverySeries(t *testing.T) {
	e := newListEnv(t, map[string][]string{"LEAD": {leadPage}, "SUPPLIER": {supplierPage}, "CUSTOMER": {customersPage}})
	out, _, err := runCLI(t, "", "otak", "relation", "list")
	if err != nil {
		t.Fatal(err)
	}
	if got := e.typesCalled(); got != "LEAD,SUPPLIER,CUSTOMER" {
		t.Errorf("types = %s", got)
	}
	for _, q := range e.queries {
		stages := strings.Join(q["stage"], ",")
		if q.Get("type") == "SUPPLIER" {
			if len(q["stage"]) != 0 {
				t.Errorf("stage on supplier: %v", q)
			}
		} else if stages != allStages {
			t.Errorf("%s stages = %s", q.Get("type"), stages)
		}
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 5 || !strings.HasPrefix(lines[0], "NAME") || !strings.Contains(lines[0], "TYPE") {
		t.Fatalf("out =\n%s", out)
	}
	order := []string{"Lost Lead", "Parts BV", "Closed Co", "Direct Co"}
	for i, name := range order {
		if !strings.HasPrefix(lines[i+1], name) {
			t.Errorf("row %d = %q, want %s first", i, lines[i+1], name)
		}
	}
	for i, want := range [][]string{{"LEAD", "LOST"}, {"SUPPLIER", "-"}, {"CUSTOMER", "CLOSED"}, {"CUSTOMER", "DIRECT"}} {
		fields := strings.Fields(lines[i+1])
		if fields[2] != want[0] || fields[3] != want[1] {
			t.Errorf("row %d = %v, want type %s stage %s", i, fields, want[0], want[1])
		}
	}
	if !strings.Contains(lines[2], " - ") {
		t.Errorf("supplier row lacks dashes: %q", lines[2])
	}
}

func TestRelationListTypeAndStageFlags(t *testing.T) {
	tests := []struct {
		name       string
		args       []string
		wantTypes  string
		wantStages string
	}{
		{"type only", []string{"--type", "customer"}, "CUSTOMER", allStages},
		{"type and stages", []string{"--type", "lead", "--stage", "new,lost"}, "LEAD", "NEW,LOST"},
		{"stage without type skips suppliers", []string{"--stage", "closed"}, "LEAD,CUSTOMER", "CLOSED"},
		{"repeated stage", []string{"--type", "LEAD", "--stage", "new", "--stage", "warm"}, "LEAD", "NEW,WARM"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newListEnv(t, nil)
			out, _, err := runCLI(t, "", append([]string{"otak", "relation", "list"}, tt.args...)...)
			if err != nil {
				t.Fatal(err)
			}
			if got := e.typesCalled(); got != tt.wantTypes {
				t.Errorf("types = %s, want %s", got, tt.wantTypes)
			}
			for _, q := range e.queries {
				if got := strings.Join(q["stage"], ","); got != tt.wantStages {
					t.Errorf("stages = %s, want %s", got, tt.wantStages)
				}
			}
			if out != "No relations.\n" {
				t.Errorf("out = %q", out)
			}
		})
	}
}

func TestRelationListStageEncoding(t *testing.T) {
	e := newListEnv(t, nil)
	if _, _, err := runCLI(t, "", "otak", "relation", "list", "--type", "lead", "--stage", "new,lost"); err != nil {
		t.Fatal(err)
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if raw := e.queries[0].Encode(); !strings.Contains(raw, "stage=NEW&stage=LOST") {
		t.Errorf("query = %s", raw)
	}
}

func TestRelationListRefusalsBeforeAnyCall(t *testing.T) {
	tests := []struct {
		name string
		args []string
		want string
	}{
		{"supplier with stage", []string{"--type", "supplier", "--stage", "NEW"}, "--stage does not apply to suppliers"},
		{"unknown type", []string{"--type", "partner"}, `unknown type "partner"; allowed: LEAD, SUPPLIER, CUSTOMER`},
		{"unknown stage", []string{"--type", "lead", "--stage", "won"}, `unknown stage "won"; allowed: `},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newListEnv(t, nil)
			_, _, err := runCLI(t, "", append([]string{"otak", "relation", "list"}, tt.args...)...)
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := e.api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestRelationListSearchSent(t *testing.T) {
	e := newListEnv(t, nil)
	if _, _, err := runCLI(t, "", "otak", "relation", "list", "--type", "lead", "--search", " acme "); err != nil {
		t.Fatal(err)
	}
	if got := e.queries[0].Get("search"); got != "acme" {
		t.Errorf("search = %q", got)
	}
}

func TestRelationListEventSent(t *testing.T) {
	e := newListEnv(t, nil)
	if _, _, err := runCLI(t, "", "otak", "relation", "list", "--event", " "+eventID+" "); err != nil {
		t.Fatal(err)
	}
	if got := e.typesCalled(); got != "LEAD,SUPPLIER,CUSTOMER" {
		t.Errorf("types = %s", got)
	}
	for _, q := range e.queries {
		if got := q.Get("eventId"); got != eventID {
			t.Errorf("%s eventId = %q", q.Get("type"), got)
		}
	}
}

func TestRelationListWithoutEventSendsNoEventID(t *testing.T) {
	e := newListEnv(t, nil)
	if _, _, err := runCLI(t, "", "otak", "relation", "list", "--type", "lead"); err != nil {
		t.Fatal(err)
	}
	if _, ok := e.queries[0]["eventId"]; ok {
		t.Errorf("query = %v", e.queries[0])
	}
}

func TestRelationListContactColumns(t *testing.T) {
	page := `{"data":[{"id":"a","name":"Acme","type":"LEAD","stage":"NEW","website":null,"owner":{"name":"Ann"},` +
		`"mainPersonContactName":"Jan de Vries","mainPersonContactEmail":"jan@acme.nl","mainBusinessContactEmail":"info@acme.nl"},` +
		`{"id":"b","name":"Beta","type":"LEAD","stage":"NEW","website":null,"owner":{"name":"Ann"},` +
		`"mainPersonContactName":"Piet","mainPersonContactEmail":null,"mainBusinessContactEmail":"sales@beta.nl"},` +
		`{"id":"c","name":"Gamma","type":"LEAD","stage":"NEW","website":null,"owner":{"name":"Ann"},` +
		`"mainPersonContactName":null,"mainPersonContactEmail":null,"mainBusinessContactEmail":null}],"pagination":{"currentPage":1,"last":true}}`
	newListEnv(t, map[string][]string{"LEAD": {page}})
	out, _, err := runCLI(t, "", "otak", "relation", "list", "--type", "lead")
	if err != nil {
		t.Fatal(err)
	}
	want := "NAME   TYPE  STAGE  WEBSITE  OWNER  CONTACT       CONTACT MAIL  COMPANY MAIL   ID\n" +
		"Acme   LEAD  NEW    -        Ann    Jan de Vries  jan@acme.nl   info@acme.nl   a\n" +
		"Beta   LEAD  NEW    -        Ann    Piet          -             sales@beta.nl  b\n" +
		"Gamma  LEAD  NEW    -        Ann    -             -             -              c\n"
	if out != want {
		t.Errorf("out =\n%s\nwant\n%s", out, want)
	}
}

func TestRelationListReadsPagesUntilLast(t *testing.T) {
	p1 := `{"data":[{"id":"a","name":"First","type":"LEAD","stage":"NEW"}],"pagination":{"currentPage":1,"last":false}}`
	p2 := `{"data":[{"id":"b","name":"Second","type":"LEAD","stage":"NEW"}],"pagination":{"currentPage":2,"last":true}}`
	e := newListEnv(t, map[string][]string{"LEAD": {p1, p2}})
	out, _, err := runCLI(t, "", "otak", "relation", "list", "--type", "lead")
	if err != nil {
		t.Fatal(err)
	}
	if len(e.queries) != 2 || e.queries[0].Get("page") != "1" || e.queries[1].Get("page") != "2" {
		t.Errorf("queries = %v", e.queries)
	}
	if !strings.Contains(out, "First") || !strings.Contains(out, "Second") {
		t.Errorf("out = %s", out)
	}
}

func TestRelationListNullCellsAndSanitizedName(t *testing.T) {
	page := `{"data":[{"id":"a","name":"Evil\u001b[31m\tCo","type":"LEAD","stage":null,"website":null,"owner":null}],"pagination":{"currentPage":1,"last":true}}`
	newListEnv(t, map[string][]string{"LEAD": {page}})
	out, _, err := runCLI(t, "", "otak", "relation", "list", "--type", "lead")
	if err != nil {
		t.Fatal(err)
	}
	if strings.ContainsRune(out, 0x1b) || strings.Contains(out, "\t") {
		t.Errorf("control characters in %q", out)
	}
	row := strings.Split(strings.TrimSpace(out), "\n")[1]
	// name, type, then six dashes for stage, website, owner, contact, contact mail and company
	// mail, then the id.
	if fields := strings.Fields(row); len(fields) < 9 || fields[len(fields)-1] != "a" ||
		strings.Join(fields[len(fields)-8:len(fields)-1], " ") != "LEAD - - - - - -" {
		t.Errorf("row = %q", row)
	}
}

func TestRelationListSecondTypeFailsWithoutTable(t *testing.T) {
	e := newListEnv(t, map[string][]string{"LEAD": {leadPage}})
	e.api.Handle("GET /otak/relations", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("type") == "SUPPLIER" {
			w.WriteHeader(http.StatusInternalServerError)
			clitest.WriteJSON(w, `{"message":"boom"}`)
			return
		}
		clitest.WriteJSON(w, leadPage)
	})
	out, _, err := runCLI(t, "", "otak", "relation", "list")
	if err == nil || !strings.Contains(err.Error(), "failed to read the supplier relations") {
		t.Fatalf("err = %v", err)
	}
	if out != "" {
		t.Errorf("partial table printed: %q", out)
	}
}

func TestRelationListForbiddenExitsThree(t *testing.T) {
	e := newListEnv(t, nil)
	e.api.Respond("GET /otak/relations", http.StatusForbidden, `{"message":"no"}`)
	_, _, err := runCLI(t, "", "otak", "relation", "list")
	if err == nil || client.ExitCode(err) != 3 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestRelationListCampaignSent(t *testing.T) {
	const campaignID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
	e := newListEnv(t, nil)
	if _, _, err := runCLI(t, "", "otak", "relation", "list", "--campaign", " "+campaignID+" "); err != nil {
		t.Fatal(err)
	}
	if len(e.queries) == 0 {
		t.Fatal("no list call")
	}
	for _, q := range e.queries {
		if got := q.Get("campaignId"); got != campaignID {
			t.Errorf("%s campaignId = %q", q.Get("type"), got)
		}
	}
}
