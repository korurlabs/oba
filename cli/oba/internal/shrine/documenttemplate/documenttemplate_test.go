package documenttemplate

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestParseID(t *testing.T) {
	const id = "7e000000-0000-4000-8000-000000000001"
	if got, err := ParseID(" " + id + " "); err != nil || got != id {
		t.Errorf("ParseID = %q, %v", got, err)
	}
	if _, err := ParseID("nda"); err == nil || !strings.Contains(err.Error(), "oba document template list") {
		t.Errorf("err = %v", err)
	}
}

func TestLegalEntityCell(t *testing.T) {
	id, name := "e1", "Korur\tB.V."
	for _, tt := range []struct {
		t    client.DocumentTemplate
		want string
	}{
		{client.DocumentTemplate{}, "all"},
		{client.DocumentTemplate{LegalEntityID: &id, LegalEntityShortName: &name}, "Korur B.V."},
		{client.DocumentTemplate{LegalEntityID: &id}, "e1"},
	} {
		if got := legalEntityCell(tt.t); got != tt.want {
			t.Errorf("legalEntityCell = %q, want %q", got, tt.want)
		}
	}
}
