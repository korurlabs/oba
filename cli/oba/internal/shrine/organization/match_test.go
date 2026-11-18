package organization

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestMatch(t *testing.T) {
	orgs := []client.Organization{{ID: "id-a", Name: "Acme"}, {ID: "id-b", Name: "Beta"}, {ID: "id-c", Name: "beta"}}
	tests := []struct{ value, wantID, wantErr string }{
		{"id-b", "id-b", ""},
		{" aCmE ", "id-a", ""},
		{"BETA", "", `several organizations are named "BETA": id-b, id-c`},
		{"nope", "", `no organization of yours matches "nope"`},
	}
	for _, tt := range tests {
		org, err := Match(orgs, tt.value)
		if tt.wantErr != "" {
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Errorf("Match(%q) err = %v, want %q", tt.value, err, tt.wantErr)
			}
			continue
		}
		if err != nil || org.ID != tt.wantID {
			t.Errorf("Match(%q) = %v, %v; want %s", tt.value, org.ID, err, tt.wantID)
		}
	}
}
