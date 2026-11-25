package attachment

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestAttachmentAddFilesToPage(t *testing.T) {
	api := newAttachmentAPI(t)
	dir := t.TempDir()
	pdf := writeFile(t, dir, "policy.pdf", "%PDF-1.7 a")
	notes := writeFile(t, dir, "notes", "plain text notes")
	var parts []clitest.FormPart
	api.Handle("POST "+attachmentsPath, func(w http.ResponseWriter, r *http.Request) {
		parts = clitest.ReadMultipartParts(t, r)
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `{"data":[`+attachmentJSON("a1", "policy.pdf", "application/pdf")+","+
			attachmentJSON("a2", "notes", "text/plain; charset=utf-8")+`]}`)
	})

	out, _, err := runCLI(t, "", "kalkan", "attachment", "add", "--page", "engineering/plans", "--file", pdf, "--file", notes)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Added attachment a1 (policy.pdf) to page engineering/plans\nAdded attachment a2 (notes) to page engineering/plans\n" {
		t.Errorf("out = %q", out)
	}
	if got := strings.Join(api.Calls(), ";"); got != "GET "+pagesPath+";POST "+attachmentsPath {
		t.Errorf("calls = %v", got)
	}
	if len(parts) != 3 {
		t.Fatalf("parts = %+v", parts)
	}
	// The API matches each files part with the metadata entry at the same position by file name.
	metadata := `[{"fileName":"policy.pdf","entityType":"KALKAN_PAGE","entityId":"` + pageID + `"},` +
		`{"fileName":"notes","entityType":"KALKAN_PAGE","entityId":"` + pageID + `"}]`
	want := []clitest.FormPart{
		{Name: "metadata", ContentType: "application/json", Data: metadata},
		{Name: "files", FileName: "policy.pdf", ContentType: "application/pdf", Data: "%PDF-1.7 a"},
		{Name: "files", FileName: "notes", ContentType: "text/plain; charset=utf-8", Data: "plain text notes"},
	}
	for i := range want {
		if parts[i] != want[i] {
			t.Errorf("part %d = %+v, want %+v", i, parts[i], want[i])
		}
	}
}

func TestAttachmentAddRefusesBadInputBeforeAnyCall(t *testing.T) {
	api := newAttachmentAPI(t)
	dir := t.TempDir()
	a := writeFile(t, dir, "a.pdf", "%PDF same")
	b := writeFile(t, dir, "b.pdf", "%PDF same")
	empty := writeFile(t, dir, "empty.pdf", "")
	tests := []struct {
		name string
		args []string
		want string
	}{
		{"no file", nil, "pass --file with the file to attach"},
		{"missing", []string{"--file", dir + "/nope.pdf"}, "failed to read " + dir + "/nope.pdf"},
		{"directory", []string{"--file", dir}, dir + " is not a regular file"},
		{"empty", []string{"--file", empty}, "is empty"},
		{"same content", []string{"--file", a, "--file", b}, b + " has the same content as " + a},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			args := append([]string{"kalkan", "attachment", "add", "--page", "engineering/plans"}, tt.args...)
			_, _, err := runCLI(t, "", args...)
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Errorf("err = %v, want %q", err, tt.want)
			}
		})
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}
