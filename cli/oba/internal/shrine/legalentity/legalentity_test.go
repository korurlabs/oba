package legalentity

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestParseID(t *testing.T) {
	const id = "0e000000-0000-4000-8000-000000000001"
	if got, err := ParseID(" " + id + " "); err != nil || got != id {
		t.Errorf("ParseID = %q, %v", got, err)
	}
	if _, err := ParseID("Korur"); err == nil || !strings.Contains(err.Error(), "oba legal-entity list") {
		t.Errorf("err = %v", err)
	}
}

func TestLabel(t *testing.T) {
	if got, want := Label(client.LegalEntity{ID: "e1", ShortName: "Korur\nB.V."}), `"Korur B.V." (e1)`; got != want {
		t.Errorf("Label = %q, want %q", got, want)
	}
	if got := Label(client.LegalEntity{ID: "e1"}); got != "e1" {
		t.Errorf("Label without name = %q", got)
	}
}
