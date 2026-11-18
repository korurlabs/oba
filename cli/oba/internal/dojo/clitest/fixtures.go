package clitest

import (
	_ "embed"
	"net/http"
	"testing"
)

// The relation the Otak command tests of several domains read and change.
const (
	RelationID   = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
	RelationPath = "/otak/relations/" + RelationID
)

// apiDocs is the API document with the relation and contact request schemas that the tests of
// several command and logic packages serve.
//
//go:embed testdata/api-docs.json
var apiDocs string

// ServeAPIDocs serves the shared API document with the relation and contact request schemas.
func (m *MockAPI) ServeAPIDocs() {
	m.Respond("GET /v3/api-docs", http.StatusOK, apiDocs)
}

// NewSignedInAPI starts a mock API that serves the shared API document, in an isolated home with
// a session in OrgIDAcme.
func NewSignedInAPI(t *testing.T) *MockAPI {
	t.Helper()
	SetupHome(t)
	api := NewMockAPI(t)
	api.ServeAPIDocs()
	LoginTo(t, api.URL(), OrgIDAcme)
	return api
}

// WriteJSON answers a mock API request with body as JSON.
func WriteJSON(w http.ResponseWriter, body string) {
	w.Header().Set("Content-Type", "application/json")
	_, _ = w.Write([]byte(body))
}
