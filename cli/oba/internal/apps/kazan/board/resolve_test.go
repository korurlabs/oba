package board

import (
	"strings"
	"testing"
)

func TestResolve(t *testing.T) {
	tests := []struct {
		name, ref, wantID, wantErr string
		wantCalls                  int
	}{
		{name: "id without a call", ref: idKagi, wantID: idKagi, wantCalls: 0},
		{name: "key prefix, case ignored", ref: "ops", wantID: idOps, wantCalls: 1},
		{name: "name", ref: "Kagi", wantID: idKagi, wantCalls: 1},
		{name: "child board by name", ref: " infra ", wantErr: "several boards match \"infra\"", wantCalls: 1},
		{name: "child board by prefix", ref: "INF", wantID: idInfra, wantCalls: 1},
		{name: "unknown", ref: "nope", wantErr: "no board of yours matches \"nope\"", wantCalls: 1},
		{name: "empty", ref: " ", wantErr: "no board given", wantCalls: 0},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api, c := newAPI(t)
			got, err := Resolve(c, tt.ref)
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v, want %q", err, tt.wantErr)
				}
			} else if err != nil || got.ID != tt.wantID {
				t.Fatalf("got %+v, %v; want %s", got, err, tt.wantID)
			}
			if n := len(api.Calls()); n != tt.wantCalls {
				t.Errorf("calls = %v", api.Calls())
			}
		})
	}
}

func TestResolveTwoMatchesNamesBothWithIDs(t *testing.T) {
	_, c := newAPI(t)
	_, err := Resolve(c, "infra")
	if err == nil {
		t.Fatal("no error")
	}
	for _, part := range []string{"Infra (INF, " + idInfra + ")", "kagi2 (INFRA, " + idOther + ")", "use the id instead"} {
		if !strings.Contains(err.Error(), part) {
			t.Errorf("err = %v, missing %q", err, part)
		}
	}
}

func TestResolverReadsTheTreeOnce(t *testing.T) {
	api, c := newAPI(t)
	r := NewResolver(c)
	for _, ref := range []string{"OPS", "KAGI", "Operations"} {
		if _, err := r.Resolve(ref); err != nil {
			t.Fatal(err)
		}
	}
	if len(api.Calls()) != 1 {
		t.Errorf("calls = %v", api.Calls())
	}
	q := api.Request("GET /kazan/boards")
	if q.Org != "11111111-1111-4111-8111-111111111111" {
		t.Errorf("org = %q", q.Org)
	}
}

func TestRefLabel(t *testing.T) {
	if got := (Ref{ID: "x"}).Label(); got != "x" {
		t.Errorf("label = %q", got)
	}
	if got := (Ref{ID: "x", Name: "Ops", Prefix: "OPS"}).Label(); got != "Ops (OPS)" {
		t.Errorf("label = %q", got)
	}
}
