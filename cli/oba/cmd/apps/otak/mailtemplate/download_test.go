package mailtemplate

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestMailTemplateDownloadWritesTheMailFile(t *testing.T) {
	api := clitest.NewSignedInAPI(t)
	api.Respond("GET "+mailTemplatePath, http.StatusOK, `{"data":`+mailTemplateJSON("Kennis/making", "NL", "Hallo", `Beste Jan,\n\nGroet`)+`}`)
	dir := t.TempDir()
	out, _, err := runCLI(t, "otak", "mail-template", "download", mailTemplateID, "-o", dir)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "Kennis-making.md")
	if data, _ := os.ReadFile(path); string(data) != "Hallo\n\nBeste Jan,\n\nGroet\n" || !strings.Contains(out, "Wrote "+path) {
		t.Errorf("out %q, file %q", out, data)
	}
	if _, _, err := runCLI(t, "otak", "mail-template", "download", mailTemplateID, "-o", dir); err == nil || !strings.Contains(err.Error(), "--force") {
		t.Errorf("second download: err = %v", err)
	}
}
