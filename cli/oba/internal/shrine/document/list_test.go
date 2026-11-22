package document

import (
	"bytes"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestListQuery(t *testing.T) {
	values, err := ListQuery{RelationID: relationID, Type: "contract", Language: "TR"}.query()
	if err != nil {
		t.Fatal(err)
	}
	if got, want := values.Encode(), "language=TR&relationId="+relationID+"&type=CONTRACT"; got != want {
		t.Errorf("query = %q, want %q", got, want)
	}
	if values, err := (ListQuery{}).query(); err != nil || len(values) != 0 {
		t.Errorf("empty query = %v, %v", values, err)
	}
}

func TestPrintListMarksMissingValues(t *testing.T) {
	var out bytes.Buffer
	if err := PrintList(&out, []client.Document{{ID: "d1", Title: "NDA", Type: "NDA", Language: "NL"}}); err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSpace(out.String()), "\n")
	if len(lines) != 2 || !strings.Contains(lines[1], "NDA    NDA   NL        -         -             no     0     -        d1") {
		t.Errorf("out =\n%s", out.String())
	}
}
