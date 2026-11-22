package documenttemplate

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestCreateRequest(t *testing.T) {
	const legalEntityID = "0e000000-0000-4000-8000-000000000001"
	tests := []struct {
		name    string
		in      CreateInput
		want    string
		wantErr string
	}{
		{name: "name from the file, every legal entity", in: CreateInput{File: "legal/nda/nda_nl.html", Type: "nda", Language: "nl"},
			want: `{"type":"NDA","name":"nda_nl","language":"NL","legalEntityId":null}`},
		{name: "given name and legal entity", in: CreateInput{File: "a.html", Type: "service-sheet", Language: "en",
			Name: " Service sheet ", LegalEntityID: legalEntityID},
			want: `{"type":"SERVICE_SHEET","name":"Service sheet","language":"EN","legalEntityId":"` + legalEntityID + `"}`},
		{name: "no file", in: CreateInput{Type: "nda", Language: "nl"}, wantErr: "--file is required"},
		{name: "unknown language", in: CreateInput{File: "a.html", Type: "nda", Language: "de"}, wantErr: "unknown language"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req, err := tt.in.request()
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
