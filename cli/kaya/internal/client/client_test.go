package client

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestDo_parsesTheErrorOfACustomResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnprocessableEntity)
		_, _ = w.Write([]byte(`{"success":false,"message":"refused","data":null,"error":{"code":"KAY_CFG_009",` +
			`"message":"Blobs are missing","errorValues":{"missing":["aa","bb","aa"]}}}`))
	}))
	defer server.Close()
	api := New(server.URL, "org", func(context.Context) (string, error) { return "jwt", nil })
	_, err := api.CreateSeedRun(context.Background(), SeedRequest{SourceRef: "x"})
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Status != 422 || apiErr.Code != CodeSeedBlobsMissing || apiErr.Message != "Blobs are missing" {
		t.Fatalf("err = %#v", err)
	}
	missing := MissingBlobs(err)
	if len(missing) != 2 || missing[0] != "aa" || missing[1] != "bb" {
		t.Errorf("missing = %v, want [aa bb]", missing)
	}
}

func TestDo_errorWithoutEnvelopeKeepsTheBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "bad gateway", http.StatusBadGateway)
	}))
	defer server.Close()
	api := New(server.URL, "", func(context.Context) (string, error) { return "jwt", nil })
	_, err := api.ProvisionMe(context.Background())
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Status != http.StatusBadGateway || apiErr.Message != "bad gateway" {
		t.Fatalf("err = %#v", err)
	}
}

func TestProvisionMe_sendsNoOrganization(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/users/me" || r.Header.Get(HeaderOrganizationID) != "" {
			t.Errorf("%s %s org %q", r.Method, r.URL.Path, r.Header.Get(HeaderOrganizationID))
		}
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"success":true,"data":{"id":"u","email":"a@example.com","profile":null,"organizations":[{"id":"o","name":"Korur","role":"OWNER"}]}}`))
	}))
	defer server.Close()
	api := New(server.URL, "org", func(context.Context) (string, error) { return "jwt", nil })
	me, err := api.ProvisionMe(context.Background())
	if err != nil || me.Email != "a@example.com" || len(me.Memberships) != 1 || me.Memberships[0].OrganizationID != "o" || me.Memberships[0].Role != "OWNER" {
		t.Fatalf("me = %+v, %v", me, err)
	}
}
