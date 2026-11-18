package template

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	templateID    = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
	templatesPath = "/otak/document-templates"
	templatePath  = templatesPath + "/" + templateID
	filesPath     = templatePath + "/files"
)

func templateJSON(name, docType, lang string, ready bool, extra string) string {
	r := "false"
	if ready {
		r = "true"
	}
	return `{"id":"` + templateID + `","name":"` + name + `","type":"` + docType + `","language":"` + lang +
		`","ready":` + r + `,"updatedAt":"2026-11-14T10:00:00Z"` + extra + `}`
}

func decodeBody(t *testing.T, body string) map[string]any {
	t.Helper()
	var obj map[string]any
	if err := json.Unmarshal([]byte(body), &obj); err != nil {
		t.Fatalf("body %q: %v", body, err)
	}
	return obj
}

// libraryHTML writes legal/nda/nda_nl.html with a logo two levels up, as the library lays it out.
func libraryHTML(t *testing.T) string {
	t.Helper()
	root := t.TempDir()
	writeFile(t, root, "images/logo-korur.png", "\x89PNG fake")
	return writeFile(t, root, "legal/nda/nda_nl.html", `<html><img class="b" src="../../images/logo-korur.png"><p>NDA</p></html>`)
}

func TestTemplatePushHTMLCreatesThenUploads(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := clitest.NewSignedInAPI(t)
	file := libraryHTML(t)
	api.Respond("POST "+templatesPath, http.StatusCreated, `{"data":`+templateJSON("nda_nl", "NDA", "NL", false, "")+`}`)
	var parts map[string]clitest.FormPart
	api.Handle("PUT "+filesPath, func(w http.ResponseWriter, r *http.Request) {
		parts = clitest.ReadMultipart(t, r)
		clitest.WriteJSON(w, `{"data":`+templateJSON("nda_nl", "NDA", "NL", true, "")+`}`)
	})

	out, _, err := runCLI(t, "otak", "template", "push", file, "--type", "nda", "--lang", "nl")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Created template "+templateID+" (nda_nl, NDA, NL)") || !strings.Contains(out, "Uploaded the HTML and its PDF.") {
		t.Errorf("out = %q", out)
	}
	body := decodeBody(t, api.Request("POST "+templatesPath).Body)
	if body["name"] != "nda_nl" || body["type"] != "NDA" || body["language"] != "NL" {
		t.Errorf("body = %v", body)
	}
	if _, ok := body["subject"]; ok {
		t.Errorf("subject sent for an NDA: %v", body)
	}
	html, pdf := parts["html"], parts["pdf"]
	if html.ContentType != "text/html; charset=utf-8" || html.FileName != "nda_nl.html" ||
		!strings.Contains(html.Data, `src="data:image/png;base64,`) || strings.Contains(html.Data, "../../images") {
		t.Errorf("html part = %+v", html)
	}
	if pdf.ContentType != "application/pdf" || pdf.FileName != "nda_nl.pdf" ||
		!strings.HasPrefix(pdf.Data, clitest.StubPDFPrefix) || !strings.HasSuffix(pdf.Data, html.Data) {
		t.Errorf("pdf part = %+v", pdf)
	}
}

func TestTemplatePushRefusesMail(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "first-contact.md", "Introduction\n\nDear Jan,\n")
	for _, args := range [][]string{{"--type", "mail", "--lang", "en"}, {"--type", "nda", "--lang", "en"}} {
		_, _, err := runCLI(t, append([]string{"otak", "template", "push", file}, args...)...)
		if err == nil || !strings.Contains(err.Error(), "oba otak mail-template") {
			t.Errorf("%v: err = %v", args, err)
		}
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestTemplatePushWithoutWeasyPrintSendsNothing(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := libraryHTML(t)
	clitest.NoWeasyPrint(t)
	_, _, err := runCLI(t, "otak", "template", "push", file, "--type", "nda", "--lang", "nl")
	if err == nil || !strings.Contains(err.Error(), "brew install weasyprint") {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestTemplatePushWrongExtensionSendsNothing(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "nda.pdf", "%PDF-")
	_, _, err := runCLI(t, "otak", "template", "push", file, "--type", "nda", "--lang", "nl")
	if err == nil || !strings.Contains(err.Error(), "a NDA is an .html file") {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestTemplatePushNewNeedsTypeAndLanguage(t *testing.T) {
	clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "nda.html", "<p>NDA</p>")
	_, _, err := runCLI(t, "otak", "template", "push", file, "--type", "nda")
	if err == nil || !strings.Contains(err.Error(), "needs --type and --lang") {
		t.Fatalf("err = %v", err)
	}
}

func TestTemplatePushUpdateKeepsStoredValues(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := clitest.NewSignedInAPI(t)
	file := libraryHTML(t)
	api.Respond("GET "+templatePath, http.StatusOK, `{"data":`+templateJSON("Gizlilik", "NDA", "TR", true, "")+`}`)
	api.Respond("PUT "+templatePath, http.StatusOK, `{"data":`+templateJSON("Gizlilik", "NDA", "TR", true, "")+`}`)
	api.Respond("PUT "+filesPath, http.StatusOK, `{"data":`+templateJSON("Gizlilik", "NDA", "TR", true, "")+`}`)

	out, _, err := runCLI(t, "otak", "template", "push", file, "--id", templateID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Updated template "+templateID) {
		t.Errorf("out = %q", out)
	}
	body := decodeBody(t, api.Request("PUT "+templatePath).Body)
	if body["name"] != "Gizlilik" || body["type"] != "NDA" || body["language"] != "TR" {
		t.Errorf("body = %v", body)
	}
	want := []string{"GET " + templatePath, "PUT " + templatePath, "PUT " + filesPath}
	if got := api.Calls(); strings.Join(got, ",") != strings.Join(want, ",") {
		t.Errorf("calls = %v", got)
	}
}

func TestTemplatePushUpdateChangesType(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := clitest.NewSignedInAPI(t)
	file := libraryHTML(t)
	api.Respond("GET "+templatePath, http.StatusOK, `{"data":`+templateJSON("NDA", "NDA", "NL", true, "")+`}`)
	api.Respond("PUT "+templatePath, http.StatusOK, `{"data":`+templateJSON("NDA", "TERMS", "NL", true, "")+`}`)
	api.Respond("PUT "+filesPath, http.StatusOK, `{"data":`+templateJSON("NDA", "TERMS", "NL", true, "")+`}`)
	if _, _, err := runCLI(t, "otak", "template", "push", file, "--id", templateID, "--type", "terms"); err != nil {
		t.Fatal(err)
	}
	body := decodeBody(t, api.Request("PUT "+templatePath).Body)
	if body["type"] != "TERMS" || body["name"] != "NDA" || body["language"] != "NL" {
		t.Errorf("body = %v", body)
	}
}

func TestTemplatePushFilesFailureNamesTheNewID(t *testing.T) {
	clitest.StubWeasyPrint(t)
	api := clitest.NewSignedInAPI(t)
	file := libraryHTML(t)
	api.Respond("POST "+templatesPath, http.StatusCreated, `{"data":`+templateJSON("nda_nl", "NDA", "NL", false, "")+`}`)
	api.Respond("PUT "+filesPath, http.StatusUnprocessableEntity, `{"error":{"code":"OTK_VAL_009","message":"file not HTML or PDF"}}`)
	_, _, err := runCLI(t, "otak", "template", "push", file, "--type", "nda", "--lang", "nl")
	if err == nil || !strings.Contains(err.Error(), "--id "+templateID) || !strings.Contains(err.Error(), "OTK_VAL_009") {
		t.Fatalf("err = %v", err)
	}
}

func TestTemplatePullHTMLAndPDF(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	urls := `,"htmlUrl":"` + api.URL() + `/blob/a.html?sig=x","pdfUrl":"` + api.URL() + `/blob/a.pdf?sig=x"`
	api.Respond("GET "+templatePath, http.StatusOK, `{"data":`+templateJSON("NDA Acme/B.V.", "NDA", "NL", true, urls)+`}`)
	api.Respond("GET /blob/a.html", http.StatusOK, "<p>nda</p>")
	api.Respond("GET /blob/a.pdf", http.StatusOK, "%PDF-1.7")
	dir := t.TempDir()

	out, _, err := runCLI(t, "otak", "template", "pull", templateID, "-o", dir)
	if err != nil {
		t.Fatal(err)
	}
	html := filepath.Join(dir, "NDA-Acme-B.V.html")
	if !strings.Contains(out, "Wrote "+html) {
		t.Errorf("out = %q", out)
	}
	if data, _ := os.ReadFile(html); string(data) != "<p>nda</p>" {
		t.Errorf("html = %q", data)
	}
	if data, _ := os.ReadFile(filepath.Join(dir, "NDA-Acme-B.V.pdf")); string(data) != "%PDF-1.7" {
		t.Errorf("pdf = %q", data)
	}
	for _, r := range api.Requests() {
		if strings.HasPrefix(r.Path, "/blob/") && r.Auth != "" {
			t.Errorf("token sent to the blob URL %s", r.Path)
		}
	}

	if _, _, err := runCLI(t, "otak", "template", "pull", templateID, "-o", dir); err == nil || !strings.Contains(err.Error(), "--force") {
		t.Errorf("second pull: err = %v", err)
	}
	if _, _, err := runCLI(t, "otak", "template", "pull", templateID, "-o", dir, "--force"); err != nil {
		t.Errorf("forced pull: %v", err)
	}
}

func TestTemplatePullWithoutFiles(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("GET "+templatePath, http.StatusOK, `{"data":`+templateJSON("NDA", "NDA", "NL", false, "")+`}`)
	_, _, err := runCLI(t, "otak", "template", "pull", templateID, "-o", t.TempDir())
	if err == nil || !strings.Contains(err.Error(), "has no files yet") {
		t.Fatalf("err = %v", err)
	}
}

func TestTemplateListFiltersAndTable(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	var query string
	api.Handle("GET "+templatesPath, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":[`+templateJSON("NDA", "NDA", "EN", true, "")+`],"pagination":{"currentPage":1,"last":true}}`)
	})
	out, _, err := runCLI(t, "otak", "template", "list", "--type", "nda", "--lang", "us")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(query, "type=NDA") || !strings.Contains(query, "language=EN") {
		t.Errorf("query = %q", query)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 2 || !strings.HasPrefix(lines[0], "NAME") || !strings.Contains(lines[1], "yes") || !strings.Contains(lines[1], templateID) {
		t.Errorf("out =\n%s", out)
	}
}

func TestTemplateListUnknownLanguage(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	_, _, err := runCLI(t, "otak", "template", "list", "--lang", "de")
	if err == nil || !strings.Contains(err.Error(), "unknown language") {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}
