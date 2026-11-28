package attachment

import (
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const contentID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"

func TestAttachmentListOfContent(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query)

	if _, _, err := runCLI(t, "", "otak", "attachment", "list", "--content", contentID); err != nil {
		t.Fatal(err)
	}
	if query.Get("entityType") != "CAMPAIGN_CONTENT" || query.Get("entityId") != contentID {
		t.Errorf("query = %v", query)
	}
}

func TestAttachmentDownloadWritesTheFile(t *testing.T) {
	api := newAttachmentAPI(t, false)
	item := strings.Replace(attachmentJSON(attachmentID, "../launch.png", "image/png", "CAMPAIGN_CONTENT", contentID),
		`"url":"https://blob/x?sig=s"`, `"url":"`+api.URL()+`/blob/launch.png?sig=s"`, 1)
	var query url.Values
	serveList(api, &query, item)
	api.Respond("GET /blob/launch.png", http.StatusOK, "PNGDATA")
	dir := t.TempDir()

	out, _, err := runCLI(t, "", "otak", "attachment", "download", "--content", contentID, "--id", attachmentID, "-o", dir)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "launch.png")
	if data, _ := os.ReadFile(path); string(data) != "PNGDATA" || out != "Wrote "+path+"\n" {
		t.Errorf("out %q, file %q", out, data)
	}
	if query.Get("entityType") != "CAMPAIGN_CONTENT" {
		t.Errorf("query = %v", query)
	}

	_, _, err = runCLI(t, "", "otak", "attachment", "download", "--content", contentID, "--id", attachmentID, "-o", dir)
	if err == nil || !strings.Contains(err.Error(), "pass --force to overwrite it") {
		t.Fatalf("second download: err = %v", err)
	}
	if _, _, err := runCLI(t, "", "otak", "attachment", "download", "--content", contentID, "--id", attachmentID, "-o", dir, "--force"); err != nil {
		t.Fatalf("--force: %v", err)
	}
}

func TestAttachmentDownloadUnknownID(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query)

	_, _, err := runCLI(t, "", "otak", "attachment", "download", "--content", contentID, "--id", attachmentID, "-o", t.TempDir())
	if err == nil || !strings.Contains(err.Error(), "is not an attachment of content "+contentID) {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}
