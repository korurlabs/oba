package icp

import (
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestBrainstormContextDescribes(t *testing.T) {
	blank := " "
	nl := "NL"
	tests := []struct {
		name string
		x    BrainstormContext
		want bool
	}{
		{"name only", BrainstormContext{Profile: client.OrganizationProfile{Name: "Acme"}}, false},
		{"blank field", BrainstormContext{Profile: client.OrganizationProfile{Name: "Acme", Website: &blank}}, false},
		{"country", BrainstormContext{Profile: client.OrganizationProfile{Name: "Acme", Country: &nl}}, true},
		{"an ICP", BrainstormContext{Icps: []client.Icp{{Name: "Benelux banks"}}}, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.x.Describes(); got != tt.want {
				t.Errorf("Describes() = %v", got)
			}
		})
	}
}
