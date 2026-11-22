package document

import (
	"encoding/json"
	"strings"
	"testing"
)

const (
	relationID    = "0f000000-0000-4000-8000-000000000001"
	legalEntityID = "0e000000-0000-4000-8000-000000000001"
	templateID    = "7e000000-0000-4000-8000-000000000001"
)

func TestCreateRequest(t *testing.T) {
	base := CreateInput{RelationID: relationID, LegalEntityID: legalEntityID, Title: " NDA Acme ", Type: "nda", Language: "us"}
	withTemplate, withFile := base, base
	withTemplate.TemplateID = templateID
	withFile.File = "nda.html"
	tests := []struct {
		name    string
		in      CreateInput
		want    string
		wantErr string
	}{
		{name: "from a template", in: withTemplate,
			want: `{"entityType":"RELATION","entityId":"` + relationID + `","legalEntityId":"` + legalEntityID +
				`","title":"NDA Acme","type":"NDA","language":"EN","templateId":"` + templateID + `"}`},
		{name: "from a file sends no template", in: withFile,
			want: `{"entityType":"RELATION","entityId":"` + relationID + `","legalEntityId":"` + legalEntityID +
				`","title":"NDA Acme","type":"NDA","language":"EN","templateId":null}`},
		{name: "no source", in: base, wantErr: "pass exactly one of --template and --file"},
		{name: "blank title", in: CreateInput{RelationID: relationID, LegalEntityID: legalEntityID, Title: " ", Type: "nda",
			Language: "nl", File: "a.html"}, wantErr: "--title is required"},
		{name: "relation not an id", in: CreateInput{RelationID: "Acme", LegalEntityID: legalEntityID, Title: "T", Type: "nda",
			Language: "nl", File: "a.html"}, wantErr: `invalid --relation "Acme": use the ID that 'oba otak relation list' prints`},
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
