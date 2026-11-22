package shrine

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	documentTemplatesURL  = "/document-templates"
	documentTemplateURL   = documentTemplatesURL + "/" + idDocTemplate
	documentTemplateFiles = documentTemplateURL + "/files"
)

// templateJSON is a template as the API returns it; legalEntity is the JSON of legalEntityId and
// legalEntityShortName, extra is appended to the object.
func templateJSON(name string, ready bool, legalEntity, extra string) string {
	r := "false"
	if ready {
		r = "true"
	}
	return `{"id":"` + idDocTemplate + `","name":"` + name + `","type":"NDA","language":"NL",` + legalEntity +
		`,"ready":` + r + `,"updatedAt":"2026-11-14T10:00:00Z"` + extra + `}`
}

const (
	everyLegalEntity = `"legalEntityId":null,"legalEntityShortName":null`
	korurOnly        = `"legalEntityId":"` + idKorur + `","legalEntityShortName":"Korur B.V."`
)

func TestDocumentTemplateListFiltersAndTable(t *testing.T) {
	api := documentAPI(t)
	var query string
	api.Handle("GET "+documentTemplatesURL, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":[`+templateJSON("NDA", true, everyLegalEntity, "")+`,`+
			templateJSON("NDA Korur", false, korurOnly, "")+`],"pagination":{"currentPage":1,"last":true}}`)
	})
	out, _, err := runCLI(t, "", "document", "template", "list", "--type", "nda", "--language", "nl",
		"--legal-entity", idKorur, "--search", "nda")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"type=NDA", "language=NL", "legalEntityId=" + idKorur, "search=nda"} {
		if !strings.Contains(query, want) {
			t.Errorf("query %q misses %q", query, want)
		}
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 3 || !strings.HasPrefix(lines[0], "NAME") || !strings.Contains(lines[1], "all") ||
		!strings.Contains(lines[1], "yes") || !strings.Contains(lines[2], "Korur B.V.") || !strings.Contains(lines[2], "no") {
		t.Errorf("out =\n%s", out)
	}
}

func TestDocumentTemplateListRefusesBadFilterBeforeAnyCall(t *testing.T) {
	api := documentAPI(t)
	for _, tt := range []struct {
		args    []string
		wantErr string
	}{
		{[]string{"--language", "de"}, "unknown language"},
		{[]string{"--legal-entity", "korur"}, `invalid --legal-entity "korur"`},
	} {
		_, _, err := runCLI(t, "", append([]string{"document", "template", "list"}, tt.args...)...)
		if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
			t.Errorf("%v: err = %v, want %q", tt.args, err, tt.wantErr)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentTemplateCreateThenUploads(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := documentAPI(t)
	file := writeHTML(t)
	api.Respond("POST "+documentTemplatesURL, http.StatusCreated, `{"data":`+templateJSON("nda", false, everyLegalEntity, "")+`}`)
	var parts map[string]clitest.FormPart
	api.Handle("PUT "+documentTemplateFiles, func(w http.ResponseWriter, r *http.Request) {
		parts = clitest.ReadMultipart(t, r)
		clitest.WriteJSON(w, `{"data":`+templateJSON("nda", true, everyLegalEntity, "")+`}`)
	})

	out, _, err := runCLI(t, "", "document", "template", "create", "--file", file, "--type", "nda", "--language", "nl")
	if err != nil {
		t.Fatal(err)
	}
	if out != `Created document template "nda" (`+idDocTemplate+") (NDA, NL)\nUploaded the HTML and its PDF.\n" {
		t.Errorf("out = %q", out)
	}
	if body := api.Request("POST " + documentTemplatesURL).Body; body != `{"type":"NDA","name":"nda","language":"NL","legalEntityId":null}` {
		t.Errorf("body = %s", body)
	}
	if !strings.Contains(parts["html"].Data, `src="data:image/png;base64,`) || !strings.HasPrefix(parts["pdf"].Data, clitest.StubPDFPrefix) {
		t.Errorf("parts = %+v", parts)
	}
}

func TestDocumentTemplateCreateForOneLegalEntity(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := documentAPI(t)
	api.Respond("POST "+documentTemplatesURL, http.StatusCreated, `{"data":`+templateJSON("NDA", false, korurOnly, "")+`}`)
	api.Respond("PUT "+documentTemplateFiles, http.StatusOK, `{"data":`+templateJSON("NDA", true, korurOnly, "")+`}`)
	if _, _, err := runCLI(t, "", "document", "template", "create", "--file", writeHTML(t), "--type", "nda", "--language", "nl",
		"--name", "NDA", "--legal-entity", idKorur); err != nil {
		t.Fatal(err)
	}
	want := `{"type":"NDA","name":"NDA","language":"NL","legalEntityId":"` + idKorur + `"}`
	if body := api.Request("POST " + documentTemplatesURL).Body; body != want {
		t.Errorf("body = %s", body)
	}
}

func TestDocumentTemplateCreateChecksBeforeAnyCall(t *testing.T) {
	api := documentAPI(t)
	dir := t.TempDir()
	md := filepath.Join(dir, "first-contact.md")
	if err := os.WriteFile(md, []byte("Hi\n\nBody\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	html := filepath.Join(dir, "nda.html")
	for _, tt := range []struct {
		args    []string
		wantErr string
	}{
		{[]string{"--type", "nda", "--language", "nl"}, "--file is required"},
		{[]string{"--file", html, "--language", "nl"}, "--type is required"},
		{[]string{"--file", html, "--type", "nda"}, "--language is required"},
		{[]string{"--file", html, "--type", "mail", "--language", "nl"}, "oba otak mail-template"},
		{[]string{"--file", md, "--type", "nda", "--language", "nl"}, "use 'oba otak mail-template push'"},
		{[]string{"--file", html, "--type", "nda", "--language", "nl", "--legal-entity", "korur"}, `invalid --legal-entity "korur"`},
	} {
		_, _, err := runCLI(t, "", append([]string{"document", "template", "create"}, tt.args...)...)
		if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
			t.Errorf("%v: err = %v, want %q", tt.args, err, tt.wantErr)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentTemplateCreateFilesFailureNamesTheUpload(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := documentAPI(t)
	file := writeHTML(t)
	api.Respond("POST "+documentTemplatesURL, http.StatusCreated, `{"data":`+templateJSON("nda", false, everyLegalEntity, "")+`}`)
	api.Respond("PUT "+documentTemplateFiles, http.StatusUnprocessableEntity, `{"error":{"code":"DOC_VAL_001","message":"file not HTML or PDF"}}`)
	_, _, err := runCLI(t, "", "document", "template", "create", "--file", file, "--type", "nda", "--language", "nl")
	if err == nil || !strings.Contains(err.Error(), "'oba document template upload "+idDocTemplate+" "+file+"'") ||
		!strings.Contains(err.Error(), "DOC_VAL_001") {
		t.Fatalf("err = %v", err)
	}
}

func TestDocumentTemplateDownloadWritesTheHTML(t *testing.T) {
	api := documentAPI(t)
	urls := `,"htmlUrl":"` + api.URL() + `/blob/t.html?sig=x","pdfUrl":"` + api.URL() + `/blob/t.pdf?sig=x"`
	api.Respond("GET "+documentTemplateURL, http.StatusOK, `{"data":`+templateJSON("NDA", true, everyLegalEntity, urls)+`}`)
	api.Respond("GET /blob/t.html", http.StatusOK, "<p>nda</p>")
	path := filepath.Join(t.TempDir(), "nda_nl.html")
	out, _, err := runCLI(t, "", "document", "template", "download", idDocTemplate, "--output", path)
	if err != nil || out != "Wrote "+path+"\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if data, _ := os.ReadFile(path); string(data) != "<p>nda</p>" {
		t.Errorf("html = %q", data)
	}
	for _, r := range api.Requests() {
		if strings.HasPrefix(r.Path, "/blob/") && r.Auth != "" {
			t.Errorf("token sent to the blob URL %s", r.Path)
		}
	}
}

func TestDocumentTemplateDownloadWithoutFiles(t *testing.T) {
	api := documentAPI(t)
	api.Respond("GET "+documentTemplateURL, http.StatusOK, `{"data":`+templateJSON("NDA", false, everyLegalEntity, `,"htmlUrl":null`)+`}`)
	_, _, err := runCLI(t, "", "document", "template", "download", idDocTemplate, "-o", filepath.Join(t.TempDir(), "x.html"))
	if err == nil || !strings.Contains(err.Error(), "'oba document template upload "+idDocTemplate+" <file.html>'") {
		t.Fatalf("err = %v", err)
	}
}

func TestDocumentTemplateUploadReplacesTheFiles(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := documentAPI(t)
	api.Respond("PUT "+documentTemplateFiles, http.StatusOK, `{"data":`+templateJSON("NDA", true, everyLegalEntity, "")+`}`)
	out, _, err := runCLI(t, "", "document", "template", "upload", idDocTemplate, writeHTML(t))
	if err != nil || out != `Uploaded the HTML and its PDF to document template "NDA" (`+idDocTemplate+")\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if calls := api.Calls(); len(calls) != 1 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentTemplateRefusesInvalidIDBeforeAnyCall(t *testing.T) {
	api := documentAPI(t)
	for _, args := range [][]string{{"download", "nda"}, {"upload", "nda", "nda.html"}} {
		_, _, err := runCLI(t, "", append([]string{"document", "template"}, args...)...)
		if err == nil || !strings.Contains(err.Error(), `invalid document template ID "nda"`) {
			t.Errorf("%v: err = %v", args, err)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}
