package shrine

import (
	"bytes"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const (
	idDocument     = "d0000000-0000-4000-8000-000000000001"
	idDocTemplate  = "7e000000-0000-4000-8000-000000000001"
	idMail         = "3a000000-0000-4000-8000-000000000001"
	idAttachment   = "a7000000-0000-4000-8000-000000000001"
	documentsURL   = "/documents"
	documentURL    = documentsURL + "/" + idDocument
	documentFiles  = documentURL + "/files"
	documentAttach = documentURL + "/attachments"
)

// documentJSON is a document detail as the API returns it; extra is appended to the object.
func documentJSON(title string, ready bool, extra string) string {
	r := "false"
	if ready {
		r = "true"
	}
	return `{"id":"` + idDocument + `","title":"` + title + `","type":"NDA","language":"NL","entityType":"RELATION",` +
		`"entityId":"` + clitest.RelationID + `","relationId":"` + clitest.RelationID + `","relationName":"Acme B.V.",` +
		`"legalEntityId":"` + idKorur + `","legalEntityShortName":"Korur B.V.","templateId":null,"ready":` + r + `,` +
		`"snapshotCount":2,"updatedBy":{"id":"u1","displayName":"Merthan"},"updatedAt":"2026-11-20T10:00:00Z",` +
		`"createdAt":"2026-11-19T10:00:00Z"` + extra + `}`
}

// documentAPI starts a mock API signed in to Acme.
func documentAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

// writeHTML writes legal/nda/nda.html with a logo two levels up, as the document library lays it
// out, and returns its path.
func writeHTML(t *testing.T) string {
	t.Helper()
	root := t.TempDir()
	for name, content := range map[string]string{
		"images/logo.png":    "\x89PNG fake",
		"legal/nda/nda.html": `<html><img src="../../images/logo.png"><p>NDA</p></html>`,
	} {
		path := filepath.Join(root, name)
		if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return filepath.Join(root, "legal/nda/nda.html")
}

func decodeObject(t *testing.T, body string) map[string]any {
	t.Helper()
	var obj map[string]any
	if err := json.Unmarshal([]byte(body), &obj); err != nil {
		t.Fatalf("body %q: %v", body, err)
	}
	return obj
}

// createArgs are the required flags of document create, without a source.
func createArgs() []string {
	return []string{"document", "create", "--relation", clitest.RelationID, "--legal-entity", idKorur,
		"--title", "NDA Acme", "--type", "nda", "--language", "nl"}
}

func TestDocumentListFiltersAndTable(t *testing.T) {
	api := documentAPI(t)
	var query string
	api.Handle("GET "+documentsURL, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":[`+documentJSON("NDA Acme", true, "")+`],"pagination":{"currentPage":1,"last":true}}`)
	})
	out, _, err := runCLI(t, "", "document", "list", "--relation", clitest.RelationID, "--legal-entity", idKorur,
		"--type", "service-sheet", "--language", "us", "--search", "acme")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"relationId=" + clitest.RelationID, "legalEntityId=" + idKorur, "type=SERVICE_SHEET",
		"language=EN", "search=acme"} {
		if !strings.Contains(query, want) {
			t.Errorf("query %q misses %q", query, want)
		}
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 2 || !strings.HasPrefix(lines[0], "TITLE") || !strings.Contains(lines[1], "Acme B.V.") ||
		!strings.Contains(lines[1], "Korur B.V.") || !strings.Contains(lines[1], idDocument) {
		t.Errorf("out =\n%s", out)
	}
	if got := api.Request("GET " + documentsURL).Org; got != clitest.OrgIDAcme {
		t.Errorf("org header = %q", got)
	}
}

func TestDocumentListEmpty(t *testing.T) {
	api := documentAPI(t)
	api.Respond("GET "+documentsURL, http.StatusOK, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
	out, _, err := runCLI(t, "", "document", "list")
	if err != nil || out != "No documents.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestDocumentListRefusesBadFilterBeforeAnyCall(t *testing.T) {
	api := documentAPI(t)
	for _, tt := range []struct {
		args    []string
		wantErr string
	}{
		{[]string{"--relation", "acme"}, `invalid --relation "acme"`},
		{[]string{"--legal-entity", "korur"}, `invalid --legal-entity "korur"`},
		{[]string{"--language", "de"}, "unknown language"},
		{[]string{"--type", "mail"}, "oba otak mail-template"},
	} {
		_, _, err := runCLI(t, "", append([]string{"document", "list"}, tt.args...)...)
		if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
			t.Errorf("%v: err = %v, want %q", tt.args, err, tt.wantErr)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentCreateFromTemplate(t *testing.T) {
	api := documentAPI(t)
	api.Respond("POST "+documentsURL, http.StatusCreated, `{"data":`+documentJSON("NDA Acme", true, "")+`}`)
	out, _, err := runCLI(t, "", append(createArgs(), "--template", idDocTemplate)...)
	if err != nil {
		t.Fatal(err)
	}
	if out != `Created document "NDA Acme" (`+idDocument+") (NDA, NL)\nCopied the HTML and the PDF of the template.\n" {
		t.Errorf("out = %q", out)
	}
	want := `{"entityType":"RELATION","entityId":"` + clitest.RelationID + `","legalEntityId":"` + idKorur +
		`","title":"NDA Acme","type":"NDA","language":"NL","templateId":"` + idDocTemplate + `"}`
	if body := api.Request("POST " + documentsURL).Body; body != want {
		t.Errorf("body = %s", body)
	}
	if calls := api.Calls(); len(calls) != 1 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentCreateFromTemplateWithoutFilesNamesTheUpload(t *testing.T) {
	api := documentAPI(t)
	api.Respond("POST "+documentsURL, http.StatusCreated, `{"data":`+documentJSON("NDA Acme", false, "")+`}`)
	out, _, err := runCLI(t, "", append(createArgs(), "--template", idDocTemplate)...)
	if err != nil || !strings.Contains(out, "'oba document upload "+idDocument+" <file.html>'") {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestDocumentCreateFromFileCreatesThenUploads(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := documentAPI(t)
	file := writeHTML(t)
	api.Respond("POST "+documentsURL, http.StatusCreated, `{"data":`+documentJSON("NDA Acme", false, "")+`}`)
	var parts map[string]clitest.FormPart
	api.Handle("PUT "+documentFiles, func(w http.ResponseWriter, r *http.Request) {
		parts = clitest.ReadMultipart(t, r)
		clitest.WriteJSON(w, `{"data":`+documentJSON("NDA Acme", true, "")+`}`)
	})

	out, _, err := runCLI(t, "", append(createArgs(), "--file", file)...)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(out, "Uploaded the HTML and its PDF.\n") {
		t.Errorf("out = %q", out)
	}
	if body := decodeObject(t, api.Request("POST "+documentsURL).Body); body["templateId"] != nil || body["title"] != "NDA Acme" {
		t.Errorf("body = %v", body)
	}
	html, pdf := parts["html"], parts["pdf"]
	if html.ContentType != "text/html; charset=utf-8" || html.FileName != "nda.html" ||
		!strings.Contains(html.Data, `src="data:image/png;base64,`) || strings.Contains(html.Data, "../../images") {
		t.Errorf("html part = %+v", html)
	}
	if pdf.ContentType != "application/pdf" || pdf.FileName != "nda.pdf" ||
		!strings.HasPrefix(pdf.Data, clitest.StubPDFPrefix) || !strings.HasSuffix(pdf.Data, html.Data) {
		t.Errorf("pdf part = %+v", pdf)
	}
}

func TestDocumentCreateFromFileJSONIsTheDocumentWithFiles(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := documentAPI(t)
	api.Respond("POST "+documentsURL, http.StatusCreated, `{"data":`+documentJSON("NDA Acme", false, "")+`}`)
	api.Respond("PUT "+documentFiles, http.StatusOK, `{"data":`+documentJSON("NDA Acme", true, "")+`}`)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", append(createArgs(), "--file", writeHTML(t))...); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	if got := buf.String(); !strings.Contains(got, `"ready": true`) {
		t.Errorf("json = %s", got)
	}
}

func TestDocumentCreateFilesFailureNamesTheUpload(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := documentAPI(t)
	file := writeHTML(t)
	api.Respond("POST "+documentsURL, http.StatusCreated, `{"data":`+documentJSON("NDA Acme", false, "")+`}`)
	api.Respond("PUT "+documentFiles, http.StatusUnprocessableEntity, `{"error":{"code":"DOC_VAL_001","message":"file not HTML or PDF"}}`)
	_, _, err := runCLI(t, "", append(createArgs(), "--file", file)...)
	if err == nil || !strings.Contains(err.Error(), "'oba document upload "+idDocument+" "+file+"'") ||
		!strings.Contains(err.Error(), "DOC_VAL_001") {
		t.Fatalf("err = %v", err)
	}
}

func TestDocumentCreateChecksBeforeAnyCall(t *testing.T) {
	api := documentAPI(t)
	md := filepath.Join(t.TempDir(), "mail.md")
	if err := os.WriteFile(md, []byte("Hi\n\nBody\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	withSource := func(args ...string) []string { return append(createArgs(), args...) }
	without := func(flag string) []string {
		args := createArgs()
		for i, a := range args {
			if a == flag {
				return append(append(append([]string{}, args[:i]...), args[i+2:]...), "--template", idDocTemplate)
			}
		}
		t.Fatalf("no %s", flag)
		return nil
	}
	for _, tt := range []struct {
		name    string
		args    []string
		wantErr string
	}{
		{"no source", createArgs(), "pass exactly one of --template and --file"},
		{"both sources", withSource("--template", idDocTemplate, "--file", md), "pass exactly one of --template and --file"},
		{"no relation", without("--relation"), "--relation is required"},
		{"no legal entity", without("--legal-entity"), "--legal-entity is required"},
		{"no title", without("--title"), "--title is required"},
		{"no type", without("--type"), "--type is required"},
		{"no language", without("--language"), "--language is required"},
		{"bad template id", withSource("--template", "nda"), `invalid --template "nda"`},
		{"mail file", withSource("--file", md), "use 'oba otak mail-template upload'"},
	} {
		t.Run(tt.name, func(t *testing.T) {
			_, _, err := runCLI(t, "", tt.args...)
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Errorf("err = %v, want %q", err, tt.wantErr)
			}
		})
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentCreateWithoutWeasyPrintSendsNothing(t *testing.T) {
	api := documentAPI(t)
	file := writeHTML(t)
	clitest.NoWeasyPrint(t)
	_, _, err := runCLI(t, "", append(createArgs(), "--file", file)...)
	if err == nil || !strings.Contains(err.Error(), "brew install weasyprint") {
		t.Fatalf("err = %v", err)
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentDownloadWritesTheHTML(t *testing.T) {
	api := documentAPI(t)
	urls := `,"htmlUrl":"` + api.URL() + `/blob/d.html?sig=x","pdfUrl":"` + api.URL() + `/blob/d.pdf?sig=x"`
	api.Respond("GET "+documentURL, http.StatusOK, `{"data":`+documentJSON("NDA Acme", true, urls)+`}`)
	api.Respond("GET /blob/d.html", http.StatusOK, "<p>nda</p>")
	path := filepath.Join(t.TempDir(), "work", "nda.html")

	out, _, err := runCLI(t, "", "document", "download", idDocument, "--output", path)
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
	if _, _, err := runCLI(t, "", "document", "download", idDocument, "-o", path); err == nil || !strings.Contains(err.Error(), "--force") {
		t.Errorf("second download: err = %v", err)
	}
	if _, _, err := runCLI(t, "", "document", "download", idDocument, "-o", path, "--force"); err != nil {
		t.Errorf("forced download: %v", err)
	}
}

func TestDocumentDownloadDefaultsToTheTitle(t *testing.T) {
	api := documentAPI(t)
	urls := `,"htmlUrl":"` + api.URL() + `/blob/d.html?sig=x","pdfUrl":null`
	api.Respond("GET "+documentURL, http.StatusOK, `{"data":`+documentJSON("NDA Acme/B.V.", true, urls)+`}`)
	api.Respond("GET /blob/d.html", http.StatusOK, "<p>nda</p>")
	dir := t.TempDir()
	t.Chdir(dir)
	out, _, err := runCLI(t, "", "document", "download", idDocument)
	if err != nil || out != "Wrote NDA-Acme-B.V.html\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if _, err := os.Stat(filepath.Join(dir, "NDA-Acme-B.V.html")); err != nil {
		t.Error(err)
	}
}

func TestDocumentDownloadWithoutFiles(t *testing.T) {
	api := documentAPI(t)
	api.Respond("GET "+documentURL, http.StatusOK, `{"data":`+documentJSON("NDA Acme", false, `,"htmlUrl":null,"pdfUrl":null`)+`}`)
	_, _, err := runCLI(t, "", "document", "download", idDocument, "-o", filepath.Join(t.TempDir(), "x.html"))
	if err == nil || !strings.Contains(err.Error(), "has no files yet") {
		t.Fatalf("err = %v", err)
	}
}

func TestDocumentUploadReplacesTheFiles(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := documentAPI(t)
	var parts map[string]clitest.FormPart
	api.Handle("PUT "+documentFiles, func(w http.ResponseWriter, r *http.Request) {
		parts = clitest.ReadMultipart(t, r)
		clitest.WriteJSON(w, `{"data":`+documentJSON("NDA Acme", true, "")+`}`)
	})
	out, _, err := runCLI(t, "", "document", "upload", idDocument, writeHTML(t))
	if err != nil || out != `Uploaded the HTML and its PDF to document "NDA Acme" (`+idDocument+")\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if !strings.HasPrefix(parts["pdf"].Data, clitest.StubPDFPrefix) || parts["html"].FileName != "nda.html" {
		t.Errorf("parts = %+v", parts)
	}
	if calls := api.Calls(); len(calls) != 1 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentRefusesBadInputBeforeAnyCall(t *testing.T) {
	api := documentAPI(t)
	pdf := filepath.Join(t.TempDir(), "nda.pdf")
	if err := os.WriteFile(pdf, []byte("%PDF-"), 0o600); err != nil {
		t.Fatal(err)
	}
	for _, tt := range []struct {
		args    []string
		wantErr string
	}{
		{[]string{"download", "nda"}, `invalid document ID "nda"`},
		{[]string{"upload", "nda", pdf}, `invalid document ID "nda"`},
		{[]string{"upload", idDocument, pdf}, "a document is an .html file"},
		{[]string{"attach-to-mail", "nda", "--mail", idMail}, `invalid document ID "nda"`},
		{[]string{"attach-to-mail", idDocument}, "--mail is required"},
		{[]string{"attach-to-mail", idDocument, "--mail", "first"}, `invalid --mail "first"`},
	} {
		_, _, err := runCLI(t, "", append([]string{"document"}, tt.args...)...)
		if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
			t.Errorf("%v: err = %v, want %q", tt.args, err, tt.wantErr)
		}
	}
	if calls := api.Calls(); len(calls) != 0 {
		t.Errorf("calls = %v", calls)
	}
}

func TestDocumentAttachToMail(t *testing.T) {
	api := documentAPI(t)
	api.Respond("POST "+documentAttach, http.StatusCreated, `{"data":{"id":"`+idAttachment+`","url":"https://blob/x?sig=s",`+
		`"fileName":"NDA Acme.pdf","contentType":"application/pdf","fileSize":1234,"entityType":"MAIL","entityId":"`+idMail+`",`+
		`"createdAt":"2026-11-21T09:00:00Z","width":null,"height":null,"documentId":"`+idDocument+`"}}`)
	out, _, err := runCLI(t, "", "document", "attach-to-mail", idDocument, "--mail", idMail)
	if err != nil || out != "Attached NDA Acme.pdf (attachment "+idAttachment+") to mail "+idMail+"\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if body := api.Request("POST " + documentAttach).Body; body != `{"entityType":"MAIL","entityId":"`+idMail+`"}` {
		t.Errorf("body = %s", body)
	}
}

func TestDocumentAttachToSentMailShowsAPIError(t *testing.T) {
	api := documentAPI(t)
	api.Respond("POST "+documentAttach, http.StatusConflict,
		`{"error":{"code":"OTK_STA_011","message":"The outreach can no longer be changed."}}`)
	_, _, err := runCLI(t, "", "document", "attach-to-mail", idDocument, "--mail", idMail)
	if err == nil || err.Error() != "OTK_STA_011: The outreach can no longer be changed." {
		t.Fatalf("err = %v", err)
	}
}
