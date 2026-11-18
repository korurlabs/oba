package client

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"slices"
	"testing"
)

func TestPlanFeedback_sendsBothQueryFormsAndTheAckBody(t *testing.T) {
	var queries []map[string][]string
	var ackBody map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer kyt_test" || r.Header.Get(HeaderOrganizationID) != "" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/kaya/delivery/plan-feedback":
			queries = append(queries, r.URL.Query())
			_, _ = w.Write([]byte(`{"success":true,"data":[{"id":"f-1","planId":"p-1","planUrl":"https://kaya.korur.com/plans/p-1",` +
				`"kind":"SPEC","path":"docs/claude/plans/kaya/04_X.md","versionNumber":2,"latestVersionNumber":3,"sessionId":"sess-1",` +
				`"reviewerName":"Ada","decision":"REQUEST_CHANGES","comment":"Split step 3","lineStart":40,"lineEnd":44,` +
				`"quote":"3. Do it","createdAt":"2026-10-25T10:00:00Z"}]}`))
		case r.Method == http.MethodPost && r.URL.Path == "/kaya/delivery/plan-feedback/acks":
			_ = json.NewDecoder(r.Body).Decode(&ackBody)
			_, _ = w.Write([]byte(`{"success":true,"data":{"sessionId":"sess-2","acknowledged":1}}`))
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()
	c := New(server.URL, "", func(context.Context) (string, error) { return "kyt_test", nil })

	items, err := c.PendingFeedback(context.Background(), []string{"sess-1", "sess-2"})
	if err != nil || len(items) != 1 {
		t.Fatalf("items = %+v, err = %v", items, err)
	}
	want := PlanFeedback{ID: "f-1", PlanID: "p-1", PlanURL: "https://kaya.korur.com/plans/p-1", Kind: "SPEC", Path: "docs/claude/plans/kaya/04_X.md",
		VersionNumber: 2, LatestVersionNumber: 3, SessionID: "sess-1", ReviewerName: "Ada", Decision: DecisionRequestChanges,
		Comment: "Split step 3", LineStart: 40, LineEnd: 44, Quote: "3. Do it", CreatedAt: "2026-10-25T10:00:00Z"}
	if items[0] != want {
		t.Fatalf("item = %+v", items[0])
	}
	if _, err := c.PendingFeedbackForBranch(context.Background(), "git@github.com:sensey/demo.git", "feat/x"); err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(queries[0]["sessionId"], []string{"sess-1", "sess-2"}) || len(queries[0]) != 1 {
		t.Fatalf("session query = %v", queries[0])
	}
	if queries[1]["remoteUrl"][0] != "git@github.com:sensey/demo.git" || queries[1]["branch"][0] != "feat/x" || len(queries[1]) != 2 {
		t.Fatalf("branch query = %v", queries[1])
	}

	acknowledged, err := c.AckFeedback(context.Background(), "sess-2", []string{"f-1"})
	if err != nil || acknowledged != 1 {
		t.Fatalf("acknowledged = %d, err = %v", acknowledged, err)
	}
	if ackBody["sessionId"] != "sess-2" || len(ackBody["feedbackIds"].([]any)) != 1 {
		t.Fatalf("ack body = %v", ackBody)
	}
}

func TestPlanFeedback_refusalIsAnAPIError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte(`{"success":false,"error":{"code":"KAY_PLN_004","message":"one form"}}`))
	}))
	defer server.Close()
	c := New(server.URL, "", func(context.Context) (string, error) { return "kyt_test", nil })

	if _, err := c.PendingFeedbackForBranch(context.Background(), "r", ""); !IsCode(err, "KAY_PLN_004") {
		t.Fatalf("err = %v", err)
	}
}
