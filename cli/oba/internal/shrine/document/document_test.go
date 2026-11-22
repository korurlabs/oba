package document

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestParseID(t *testing.T) {
	const id = "d0000000-0000-4000-8000-000000000001"
	if got, err := ParseID(" " + id + " "); err != nil || got != id {
		t.Errorf("ParseID = %q, %v", got, err)
	}
	if _, err := ParseID("nda"); err == nil || !strings.Contains(err.Error(), "oba document list") {
		t.Errorf("err = %v", err)
	}
}

func TestLabel(t *testing.T) {
	if got, want := Label(client.Document{ID: "d1", Title: "NDA\nAcme"}), `"NDA Acme" (d1)`; got != want {
		t.Errorf("Label = %q, want %q", got, want)
	}
	if got := Label(client.Document{ID: "d1"}); got != "d1" {
		t.Errorf("Label without title = %q", got)
	}
}
