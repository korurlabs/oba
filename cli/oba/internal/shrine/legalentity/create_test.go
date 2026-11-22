package legalentity

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestCreateRequest(t *testing.T) {
	tests := []struct {
		name    string
		in      Input
		want    string
		wantErr string
	}{
		{name: "required only", in: Input{ShortName: ptr("Korur"), LegalName: ptr("Korur B.V."), Country: ptr("NLD")},
			want: `{"shortName":"Korur","legalName":"Korur B.V.","country":"NLD","registrationNumber":null,"vatNumber":null,` +
				`"street":null,"postalCode":null,"city":null,"governingLaw":null,"court":null}`},
		{name: "blank optional is null", in: Input{ShortName: ptr("Korur"), LegalName: ptr("Korur B.V."), Country: ptr("NLD"),
			VATNumber: ptr("NL001"), Street: ptr(" ")},
			want: `{"shortName":"Korur","legalName":"Korur B.V.","country":"NLD","registrationNumber":null,"vatNumber":"NL001",` +
				`"street":null,"postalCode":null,"city":null,"governingLaw":null,"court":null}`},
		{name: "no short name", in: Input{LegalName: ptr("K"), Country: ptr("NLD")}, wantErr: "--short-name is required"},
		{name: "blank legal name", in: Input{ShortName: ptr("K"), LegalName: ptr(""), Country: ptr("NLD")}, wantErr: "--legal-name is required"},
		{name: "no country", in: Input{ShortName: ptr("K"), LegalName: ptr("K")}, wantErr: "--country is required"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req, err := tt.in.createRequest()
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v", err)
				}
				return
			}
			body, _ := json.Marshal(req)
			if err != nil || string(body) != tt.want {
				t.Errorf("body %s, err %v", body, err)
			}
		})
	}
}
