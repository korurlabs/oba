package jsonout

import (
	"bytes"
	"encoding/json"
	"net/http"
	"testing"
)

func flushed(t *testing.T) string {
	t.Helper()
	var out bytes.Buffer
	dest = &out
	if err := Flush(); err != nil {
		t.Fatal(err)
	}
	var compact bytes.Buffer
	if err := json.Compact(&compact, out.Bytes()); err != nil {
		t.Fatalf("not JSON: %v\n%s", err, out.String())
	}
	return compact.String()
}

func TestFlush(t *testing.T) {
	tests := []struct {
		name   string
		record func()
		want   string
	}{
		{"nothing recorded", func() {}, `null`},
		{"last call wins", func() {
			Record(http.MethodGet, []byte(`{"data":{"id":"a"}}`))
			Record(http.MethodPut, []byte(`{"data":{"id":"a","n":1.50}}`))
		}, `{"id":"a","n":1.50}`},
		{"no data is null", func() { Record(http.MethodDelete, []byte(`{"message":"ok"}`)) }, `null`},
		{"empty body is null", func() { Record(http.MethodDelete, nil) }, `null`},
		{"consecutive list reads join", func() {
			RecordItems([]json.RawMessage{json.RawMessage(`{"id":1}`)})
			RecordItems([]json.RawMessage{json.RawMessage(`{"id":2}`)})
		}, `[{"id":1},{"id":2}]`},
		{"empty list is an empty array", func() { RecordItems(nil) }, `[]`},
		{"a call between lists starts a new one", func() {
			RecordItems([]json.RawMessage{json.RawMessage(`1`)})
			Record(http.MethodGet, []byte(`{"data":{}}`))
			RecordItems([]json.RawMessage{json.RawMessage(`2`)})
		}, `[2]`},
		{"writes collected in order", func() {
			CollectWrites()
			Record(http.MethodGet, []byte(`{"data":"read"}`))
			Record(http.MethodPost, []byte(`{"data":{"id":"lead"}}`))
			Record(http.MethodPost, []byte(`{"data":{"id":"contact"}}`))
		}, `[{"id":"lead"},{"id":"contact"}]`},
		{"no writes is an empty array", func() { CollectWrites() }, `[]`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			Start(&bytes.Buffer{})
			t.Cleanup(Stop)
			tt.record()
			if got := flushed(t); got != tt.want {
				t.Errorf("got %s, want %s", got, tt.want)
			}
		})
	}
}

func TestRecordWhileOffKeepsNothing(t *testing.T) {
	Stop()
	Record(http.MethodGet, []byte(`{"data":1}`))
	RecordItems([]json.RawMessage{json.RawMessage(`1`)})
	if len(results) != 0 || Enabled() {
		t.Fatalf("recorded while off: %v", results)
	}
	if err := Flush(); err != nil {
		t.Fatal(err)
	}
}

func TestKeepOnFailureLastsOneRun(t *testing.T) {
	Start(&bytes.Buffer{})
	t.Cleanup(Stop)
	KeepOnFailure()
	if !KeptOnFailure() {
		t.Fatal("KeptOnFailure = false after KeepOnFailure")
	}
	Start(&bytes.Buffer{})
	if KeptOnFailure() {
		t.Error("KeptOnFailure carried over into the next run")
	}
	KeepOnFailure()
	Stop()
	if KeptOnFailure() {
		t.Error("KeptOnFailure survived Stop")
	}
}
