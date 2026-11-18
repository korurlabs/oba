package client

import (
	"encoding/json"
	"net/http"
	"testing"
)

func TestCreateOrganization(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusCreated,
		`{"data":{"id":"org-9","name":"Korur B.V.","country":"NLD","status":"ACTIVE","createdAt":"2026-11-15T10:00:00Z"}}`)
	body := map[string]string{"name": "Korur B.V.", "country": "NLD"}
	org, err := NewWithToken(srv.URL, "", "tok").CreateOrganization(body)
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"POST", "/organizations", "", `{"country":"NLD","name":"Korur B.V."}`})
	if org.ID != "org-9" || org.Name != "Korur B.V." || org.Country == nil || *org.Country != "NLD" {
		t.Errorf("organization = %+v", org)
	}
}

func TestCreateOrganizationValidation(t *testing.T) {
	srv, _ := adminServer(t, http.StatusUnprocessableEntity,
		`{"error":{"code":"GEN_VAL_001","message":"Validation failed","errorValues":{"name":"Organization name is required"}}}`)
	_, err := NewWithToken(srv.URL, "", "tok").CreateOrganization(map[string]string{"name": ""})
	want := "GEN_VAL_001: Validation failed\n  name: Organization name is required"
	if ExitCode(err) != 1 || err == nil || err.Error() != want {
		t.Errorf("err = %v, want %q", err, want)
	}
}

func TestUpdateOrganization(t *testing.T) {
	srv, reqs := adminServer(t, http.StatusOK, `{"data":{"id":"org-9","name":"Korur B.V.","website":null}}`)
	body := map[string]json.RawMessage{"name": json.RawMessage(`"Korur B.V."`), "website": json.RawMessage(`null`)}
	org, err := NewWithToken(srv.URL, "org-9", "tok").UpdateOrganization("org-9", body)
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"PUT", "/organizations/org-9", "org-9", `{"name":"Korur B.V.","website":null}`})
	if org.ID != "org-9" || org.Name != "Korur B.V." || org.Website != nil {
		t.Errorf("organization = %+v", org)
	}
}

func TestUpdateOrganizationForbidden(t *testing.T) {
	srv, _ := adminServer(t, http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`)
	_, err := NewWithToken(srv.URL, "org-9", "tok").UpdateOrganization("org-9", map[string]string{"name": "x"})
	if err == nil || ExitCode(err) != 3 {
		t.Errorf("err = %v, exit = %d", err, ExitCode(err))
	}
}

func TestOrganizationProfileKeepsValuesAsSent(t *testing.T) {
	var org OrganizationProfile
	data := `{"id":"org-9","name":"Korur B.V.","industry":"Security","website":null,"status":"ACTIVE","createdAt":"2026-11-15T10:00:00Z"}`
	if err := json.Unmarshal([]byte(data), &org); err != nil {
		t.Fatal(err)
	}
	if org.Name != "Korur B.V." || org.Industry == nil || *org.Industry != "Security" || org.Website != nil {
		t.Errorf("typed fields = %+v", org)
	}
	if string(org.Values["industry"]) != `"Security"` || string(org.Values["website"]) != "null" || string(org.Values["status"]) != `"ACTIVE"` || len(org.Values) != 6 {
		t.Errorf("values = %v", org.Values)
	}
}
