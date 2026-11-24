package mailtemplate

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/spf13/cobra"

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

// newRoot builds a fresh "oba otak" tree with only this package's commands, as the oba root adds them.
func newRoot() *cobra.Command {
	return clitest.AppRoot("otak", NewCmd())
}

// runCLI runs "oba <args>" in process on a fresh tree; see clitest.RunCLI.
func runCLI(t *testing.T, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), "", args...)
}

// writeFile writes content to name below dir, creating the directories, and returns its path.
func writeFile(t *testing.T, dir, name, content string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}
