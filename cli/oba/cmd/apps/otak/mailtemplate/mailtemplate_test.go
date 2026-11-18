package mailtemplate

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
	mailTemplateID    = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
	mailTemplatesPath = "/otak/mail-templates"
	mailTemplatePath  = mailTemplatesPath + "/" + mailTemplateID
)

func mailTemplateJSON(name, lang, subject, body string) string {
	return `{"id":"` + mailTemplateID + `","name":"` + name + `","language":"` + lang + `","subject":"` + subject +
		`","body":"` + body + `","updatedAt":"2026-11-17T10:00:00Z","createdAt":"2026-11-17T09:00:00Z"}`
}

func decodeBody(t *testing.T, body string) map[string]any {
	t.Helper()
	var obj map[string]any
	if err := json.Unmarshal([]byte(body), &obj); err != nil {
		t.Fatalf("body %q: %v", body, err)
	}
	return obj
}

func TestMailTemplatePushCreatesFromFile(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "first-contact.md", "\ufeffIntroduction\r\n\r\nDear Jan,\r\n\r\nRegards\r\n")
	api.Respond("POST "+mailTemplatesPath, http.StatusCreated, `{"data":`+mailTemplateJSON("first-contact", "EN", "Introduction", `Dear Jan,\n\nRegards`)+`}`)

	out, _, err := runCLI(t, "otak", "mail-template", "push", file, "--lang", "us")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Created mail template "+mailTemplateID+" (first-contact, EN)") {
		t.Errorf("out = %q", out)
	}
	body := decodeBody(t, api.Request("POST "+mailTemplatesPath).Body)
	if body["name"] != "first-contact" || body["language"] != "EN" || body["subject"] != "Introduction" ||
		body["body"] != "Dear Jan,\n\nRegards" || len(body) != 4 {
		t.Errorf("body = %v", body)
	}
}

func TestMailTemplatePushNewNeedsLanguage(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "mail.md", "S\n\nB")
	_, _, err := runCLI(t, "otak", "mail-template", "push", file)
	if err == nil || !strings.Contains(err.Error(), "a new mail template needs --lang") {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestMailTemplatePushRefusesBadFilesBeforeAnyCall(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	dir := t.TempDir()
	cases := map[string]string{
		writeFile(t, dir, "mail.html", "<p>x</p>"):   "a mail is a .md file",
		writeFile(t, dir, "empty.md", "Subject\n\n"): "body after the subject line is empty",
	}
	for file, want := range cases {
		_, _, err := runCLI(t, "otak", "mail-template", "push", file, "--lang", "nl")
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%s: err = %v, want %q", file, err, want)
		}
	}
	if _, _, err := runCLI(t, "otak", "mail-template", "push", filepath.Join(dir, "mail.md"), "--lang", "de"); err == nil {
		t.Error("missing file accepted")
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestMailTemplatePushUpdateKeepsStoredNameAndLanguage(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "x.md", "Nieuw\n\nTekst\n")
	api.Respond("GET "+mailTemplatePath, http.StatusOK, `{"data":`+mailTemplateJSON("Kennismaking", "TR", "Oud", "Oud")+`}`)
	api.Respond("PUT "+mailTemplatePath, http.StatusOK, `{"data":`+mailTemplateJSON("Kennismaking", "TR", "Nieuw", "Tekst")+`}`)

	out, _, err := runCLI(t, "otak", "mail-template", "push", file, "--id", mailTemplateID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Updated mail template "+mailTemplateID+" (Kennismaking, TR)") {
		t.Errorf("out = %q", out)
	}
	body := decodeBody(t, api.Request("PUT "+mailTemplatePath).Body)
	if body["name"] != "Kennismaking" || body["language"] != "TR" || body["subject"] != "Nieuw" || body["body"] != "Tekst" {
		t.Errorf("body = %v", body)
	}
}

func TestMailTemplatePushUpdateWithNameAndLanguageSkipsTheRead(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "x.md", "S\n\nB\n")
	api.Respond("PUT "+mailTemplatePath, http.StatusOK, `{"data":`+mailTemplateJSON("Intro", "NL", "S", "B")+`}`)
	if _, _, err := runCLI(t, "otak", "mail-template", "push", file, "--id", mailTemplateID, "--name", "Intro", "--lang", "nl"); err != nil {
		t.Fatal(err)
	}
	if got := api.Calls(); strings.Join(got, ",") != "PUT "+mailTemplatePath {
		t.Errorf("calls = %v", got)
	}
}

func TestMailTemplatePushNameTakenShowsAPIError(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "x.md", "S\n\nB\n")
	api.Respond("POST "+mailTemplatesPath, http.StatusConflict, `{"error":{"code":"OTK_STA_014","message":"name taken"}}`)
	_, _, err := runCLI(t, "otak", "mail-template", "push", file, "--lang", "nl")
	if err == nil || !strings.Contains(err.Error(), "OTK_STA_014") {
		t.Fatalf("err = %v", err)
	}
}

func TestMailTemplatePullWritesTheMailFile(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("GET "+mailTemplatePath, http.StatusOK, `{"data":`+mailTemplateJSON("Kennis/making", "NL", "Hallo", `Beste Jan,\n\nGroet`)+`}`)
	dir := t.TempDir()
	out, _, err := runCLI(t, "otak", "mail-template", "pull", mailTemplateID, "-o", dir)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "Kennis-making.md")
	if data, _ := os.ReadFile(path); string(data) != "Hallo\n\nBeste Jan,\n\nGroet\n" || !strings.Contains(out, "Wrote "+path) {
		t.Errorf("out %q, file %q", out, data)
	}
	if _, _, err := runCLI(t, "otak", "mail-template", "pull", mailTemplateID, "-o", dir); err == nil || !strings.Contains(err.Error(), "--force") {
		t.Errorf("second pull: err = %v", err)
	}
}

func TestMailTemplateListFiltersAndTable(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	var query string
	api.Handle("GET "+mailTemplatesPath, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, `{"data":[`+mailTemplateJSON("Intro", "EN", "Hello there", "")+`],"pagination":{"currentPage":1,"last":true}}`)
	})
	out, _, err := runCLI(t, "otak", "mail-template", "list", "--lang", "us", "--search", " intro ")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(query, "language=EN") || !strings.Contains(query, "search=intro") {
		t.Errorf("query = %q", query)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 2 || !strings.HasPrefix(lines[0], "NAME") || !strings.Contains(lines[1], "Hello there") ||
		!strings.Contains(lines[1], mailTemplateID) {
		t.Errorf("out =\n%s", out)
	}
}

func TestMailTemplateListEmpty(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("GET "+mailTemplatesPath, http.StatusOK, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
	out, _, err := runCLI(t, "otak", "mail-template", "list")
	if err != nil || out != "No mail templates.\n" {
		t.Fatalf("out %q, err %v", out, err)
	}
}
