package attachment

import (
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const campaignID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

func TestAttachmentListOfCampaign(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query)

	if _, _, err := runCLI(t, "", "otak", "attachment", "list", "--campaign", campaignID); err != nil {
		t.Fatal(err)
	}
	if query.Get("entityType") != "CAMPAIGN" || query.Get("entityId") != campaignID {
		t.Errorf("query = %v", query)
	}
}

func TestAttachmentDownloadWritesTheFile(t *testing.T) {
	api := newAttachmentAPI(t, false)
	item := strings.Replace(attachmentJSON(attachmentID, "../launch.png", "image/png", "CAMPAIGN", campaignID),
		`"url":"https://blob/x?sig=s"`, `"url":"`+api.URL()+`/blob/launch.png?sig=s"`, 1)
	var query url.Values
	serveList(api, &query, item)
	api.Respond("GET /blob/launch.png", http.StatusOK, "PNGDATA")
	dir := t.TempDir()

	out, _, err := runCLI(t, "", "otak", "attachment", "download", "--campaign", campaignID, "--id", attachmentID, "-o", dir)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "launch.png")
	if data, _ := os.ReadFile(path); string(data) != "PNGDATA" || out != "Wrote "+path+"\n" {
		t.Errorf("out %q, file %q", out, data)
	}
	if query.Get("entityType") != "CAMPAIGN" {
		t.Errorf("query = %v", query)
	}

	_, _, err = runCLI(t, "", "otak", "attachment", "download", "--campaign", campaignID, "--id", attachmentID, "-o", dir)
	if err == nil || !strings.Contains(err.Error(), "pass --force to overwrite it") {
		t.Fatalf("second download: err = %v", err)
	}
	if _, _, err := runCLI(t, "", "otak", "attachment", "download", "--campaign", campaignID, "--id", attachmentID, "-o", dir, "--force"); err != nil {
		t.Fatalf("--force: %v", err)
	}
}

func TestAttachmentDownloadUnknownID(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query)

	_, _, err := runCLI(t, "", "otak", "attachment", "download", "--campaign", campaignID, "--id", attachmentID, "-o", t.TempDir())
	if err == nil || !strings.Contains(err.Error(), "is not an attachment of campaign "+campaignID) {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}
