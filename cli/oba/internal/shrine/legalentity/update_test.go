package legalentity

import (
	"encoding/json"
	"errors"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func ptr(s string) *string { return &s }

func TestMerge(t *testing.T) {
	current := client.LegalEntity{ID: "e", ShortName: "Korur", LegalName: "Korur B.V.", Country: "NLD",
		RegistrationNumber: ptr("123"), City: ptr("Amsterdam"), Court: ptr("Rechtbank")}
	tests := []struct {
		name string
		in   Input
		want string
	}{
		{name: "keeps everything not given", in: Input{City: ptr("Rotterdam")},
			want: `{"shortName":"Korur","legalName":"Korur B.V.","country":"NLD","registrationNumber":"123","vatNumber":null,` +
				`"street":null,"postalCode":null,"city":"Rotterdam","governingLaw":null,"court":"Rechtbank"}`},
		{name: "blank clears an optional field", in: Input{Court: ptr(""), RegistrationNumber: ptr("  ")},
			want: `{"shortName":"Korur","legalName":"Korur B.V.","country":"NLD","registrationNumber":null,"vatNumber":null,` +
				`"street":null,"postalCode":null,"city":"Amsterdam","governingLaw":null,"court":null}`},
		{name: "required fields change", in: Input{ShortName: ptr("Korur NL"), LegalName: ptr("Korur Nederland B.V."), Country: ptr("NLD")},
			want: `{"shortName":"Korur NL","legalName":"Korur Nederland B.V.","country":"NLD","registrationNumber":"123","vatNumber":null,` +
				`"street":null,"postalCode":null,"city":"Amsterdam","governingLaw":null,"court":"Rechtbank"}`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, err := json.Marshal(merge(current, tt.in))
			if err != nil || string(body) != tt.want {
				t.Errorf("body %s, err %v", body, err)
			}
		})
	}
}

func TestCheckUpdate(t *testing.T) {
	if err := (Input{}).CheckUpdate(); !errors.Is(err, terminal.ErrNothingToUpdate) {
		t.Errorf("empty input: err = %v", err)
	}
	if err := (Input{LegalName: ptr(" ")}).CheckUpdate(); err == nil || err.Error() != "--legal-name cannot be empty" {
		t.Errorf("blank legal name: err = %v", err)
	}
	if err := (Input{Street: ptr("")}).CheckUpdate(); err != nil {
		t.Errorf("clearing an optional field: err = %v", err)
	}
}
