package client

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"reflect"
	"testing"
)

func TestPacks_listsThePacksOfTheOrganization(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/kaya/packs" || r.Header.Get(HeaderOrganizationID) != "org-1" ||
			r.Header.Get("Authorization") != "Bearer jwt" {
			t.Errorf("%s %s headers %v", r.Method, r.URL.Path, r.Header)
		}
		_, _ = w.Write([]byte(`{"success":true,"data":[
			{"id":"p1","name":"base","description":null,"parentPackId":null,"currentVersionId":"v1","required":true,"mountPaths":[],"createdAt":"2026-10-23T09:15:00Z","updatedAt":"2026-10-23T09:15:00Z"},
			{"id":"p2","name":"web","description":"Web rules","parentPackId":"p1","currentVersionId":null,"required":false,"mountPaths":["apps/web"]}]}`))
	}))
	defer server.Close()
	api := New(server.URL, "org-1", tokenFunc)

	packs, err := api.Packs(context.Background())

	if err != nil {
		t.Fatal(err)
	}
	want := []Pack{
		{ID: "p1", Name: "base", CurrentVersionID: "v1", Required: true, MountPaths: []string{},
			CreatedAt: "2026-10-23T09:15:00Z", UpdatedAt: "2026-10-23T09:15:00Z"},
		{ID: "p2", Name: "web", Description: "Web rules", ParentPackID: "p1", MountPaths: []string{"apps/web"}},
	}
	if !reflect.DeepEqual(packs, want) {
		t.Errorf("packs = %+v, want %+v", packs, want)
	}
}

func TestSetPackRequired_sendsTheFlagAndDecodesTheReleaseCount(t *testing.T) {
	for _, required := range []bool{true, false} {
		var sent map[string]any
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != http.MethodPut || r.URL.Path != "/kaya/packs/p1/required" ||
				r.Header.Get("Content-Type") != "application/json" || r.Header.Get(HeaderOrganizationID) != "org-1" {
				t.Errorf("%s %s headers %v", r.Method, r.URL.Path, r.Header)
			}
			if err := json.NewDecoder(r.Body).Decode(&sent); err != nil {
				t.Error(err)
			}
			_ = json.NewEncoder(w).Encode(map[string]any{"success": true,
				"data": map[string]any{"packId": "p1", "required": required, "releaseCount": 3}})
		}))
		api := New(server.URL, "org-1", tokenFunc)

		result, err := api.SetPackRequired(context.Background(), "p1", required)

		server.Close()
		if err != nil {
			t.Fatal(err)
		}
		if !reflect.DeepEqual(sent, map[string]any{"required": required}) {
			t.Errorf("body = %v, want only required=%v", sent, required)
		}
		if result != (PackRequired{PackID: "p1", Required: required, ReleaseCount: 3}) {
			t.Errorf("result = %+v", result)
		}
	}
}

func TestRefusalViolations_readsTheViolationsOfARefusal(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnprocessableEntity)
		_, _ = w.Write([]byte(`{"success":false,"error":{"code":"KAY_CFG_031","message":"overridden","errorValues":{"violations":[
			{"path":".agents/rules/style.md","rule":"LOCKED_ITEM_OVERRIDDEN","message":"web overrides it","ownerIds":["p1","p2"]},
			{"rule":"GUARDRAIL_SETTINGS_MISSING","message":"no settings","repositoryId":"r1"},
			"not an object"]}}}`))
	}))
	defer server.Close()
	api := New(server.URL, "org-1", tokenFunc)

	_, err := api.SetPackRequired(context.Background(), "p1", true)

	want := []Violation{
		{Path: ".agents/rules/style.md", Rule: "LOCKED_ITEM_OVERRIDDEN", Message: "web overrides it", OwnerIDs: []string{"p1", "p2"}},
		{Rule: "GUARDRAIL_SETTINGS_MISSING", Message: "no settings", RepositoryID: "r1"},
	}
	if got := RefusalViolations(err); !reflect.DeepEqual(got, want) {
		t.Errorf("violations = %+v, want %+v", got, want)
	}
	if got := RefusalViolations(errors.New("offline")); got != nil {
		t.Errorf("violations of a network error = %+v, want none", got)
	}
}
