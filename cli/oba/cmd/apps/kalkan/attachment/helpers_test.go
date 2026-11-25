package attachment

import (
	"bytes"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const (
	pagesPath       = "/kalkan/pages"
	pageID          = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
	attachmentID    = "55555555-5555-4555-8555-555555555555"
	attachmentsPath = "/storage/attachments"
)

// treeJSON is the tree of pages the caller can see, parents first, as the API lists it.
const treeJSON = `{"data":[` +
	`{"id":"r1","slug":"engineering","path":"engineering","position":0,"compliance":false,"titles":{"EN":"Engineering"},"level":"EDIT"},` +
	`{"id":"` + pageID + `","parentId":"r1","rootId":"r1","slug":"plans","path":"engineering/plans","position":0,"compliance":false,"titles":{}}` +
	`]}`

// newRoot builds a fresh "oba kalkan" tree with only this package's commands, as the oba root adds them.
func newRoot() *cobra.Command {
	return clitest.AppRoot("kalkan", NewCmd())
}

// runCLI runs "oba <args>" in process on a fresh tree with stdin; see clitest.RunCLI.
func runCLI(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLI(t, newRoot(), stdin, args...)
}

// newAttachmentAPI starts a mock API signed in to clitest.OrgIDAcme that serves treeJSON.
func newAttachmentAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	api := clitest.NewSignedInAPI(t)
	api.Respond("GET "+pagesPath, http.StatusOK, treeJSON)
	return api
}

func attachmentJSON(id, name, contentType string) string {
	return `{"id":"` + id + `","url":"https://blob/x?sig=s","fileName":"` + name + `","contentType":"` + contentType +
		`","fileSize":1234,"entityType":"KALKAN_PAGE","entityId":"` + pageID + `","createdAt":"2026-11-17T09:00:00Z",` +
		`"width":null,"height":null}`
}

// serveList answers the attachment list with items and records the query it was asked with.
func serveList(api *clitest.MockAPI, query *url.Values, items ...string) {
	api.Handle("GET "+attachmentsPath, func(w http.ResponseWriter, r *http.Request) {
		*query = r.URL.Query()
		clitest.WriteJSON(w, `{"data":[`+strings.Join(items, ",")+`]}`)
	})
}

// captureJSON turns --json on for the test and returns the buffer the result is flushed to.
func captureJSON(t *testing.T) *bytes.Buffer {
	t.Helper()
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	return &buf
}

// flushJSON writes the --json result and returns it without surrounding space.
func flushJSON(t *testing.T, buf *bytes.Buffer) string {
	t.Helper()
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	return string(bytes.TrimSpace(buf.Bytes()))
}

// writeFile writes content to name below dir and returns its path.
func writeFile(t *testing.T, dir, name, content string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}
