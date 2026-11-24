package mailtemplate

import (
	"net/http"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestMailTemplateUploadCreatesFromFile(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "first-contact.md", "\ufeffIntroduction\r\n\r\nDear Jan,\r\n\r\nRegards\r\n")
	api.Respond("POST "+mailTemplatesPath, http.StatusCreated, `{"data":`+mailTemplateJSON("first-contact", "EN", "Introduction", `Dear Jan,\n\nRegards`)+`}`)

	out, _, err := runCLI(t, "otak", "mail-template", "upload", file, "--lang", "us")
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

func TestMailTemplateUploadNewNeedsLanguage(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "mail.md", "S\n\nB")
	_, _, err := runCLI(t, "otak", "mail-template", "upload", file)
	if err == nil || !strings.Contains(err.Error(), "a new mail template needs --lang") {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestMailTemplateUploadRefusesBadFilesBeforeAnyCall(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	dir := t.TempDir()
	cases := map[string]string{
		writeFile(t, dir, "mail.html", "<p>x</p>"):   "a mail is a .md file",
		writeFile(t, dir, "empty.md", "Subject\n\n"): "body after the subject line is empty",
	}
	for file, want := range cases {
		_, _, err := runCLI(t, "otak", "mail-template", "upload", file, "--lang", "nl")
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%s: err = %v, want %q", file, err, want)
		}
	}
	if _, _, err := runCLI(t, "otak", "mail-template", "upload", filepath.Join(dir, "mail.md"), "--lang", "de"); err == nil {
		t.Error("missing file accepted")
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestMailTemplateUploadUpdateKeepsStoredNameAndLanguage(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "x.md", "Nieuw\n\nTekst\n")
	api.Respond("GET "+mailTemplatePath, http.StatusOK, `{"data":`+mailTemplateJSON("Kennismaking", "TR", "Oud", "Oud")+`}`)
	api.Respond("PUT "+mailTemplatePath, http.StatusOK, `{"data":`+mailTemplateJSON("Kennismaking", "TR", "Nieuw", "Tekst")+`}`)

	out, _, err := runCLI(t, "otak", "mail-template", "upload", file, "--id", mailTemplateID)
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

func TestMailTemplateUploadUpdateWithNameAndLanguageSkipsTheRead(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "x.md", "S\n\nB\n")
	api.Respond("PUT "+mailTemplatePath, http.StatusOK, `{"data":`+mailTemplateJSON("Intro", "NL", "S", "B")+`}`)
	if _, _, err := runCLI(t, "otak", "mail-template", "upload", file, "--id", mailTemplateID, "--name", "Intro", "--lang", "nl"); err != nil {
		t.Fatal(err)
	}
	if got := api.Calls(); strings.Join(got, ",") != "PUT "+mailTemplatePath {
		t.Errorf("calls = %v", got)
	}
}

func TestMailTemplateUploadNameTakenShowsAPIError(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	file := writeFile(t, t.TempDir(), "x.md", "S\n\nB\n")
	api.Respond("POST "+mailTemplatesPath, http.StatusConflict, `{"error":{"code":"OTK_STA_014","message":"name taken"}}`)
	_, _, err := runCLI(t, "otak", "mail-template", "upload", file, "--lang", "nl")
	if err == nil || !strings.Contains(err.Error(), "OTK_STA_014") {
		t.Fatalf("err = %v", err)
	}
}
