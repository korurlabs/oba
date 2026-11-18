package client

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"reflect"
	"testing"
)

func tokenFunc(context.Context) (string, error) { return "jwt", nil }

func TestUploadBlob_sendsBytesUnderTheHashWithTheOrganization(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		if r.Method != http.MethodPut || r.URL.Path != "/kaya/blobs/abc123" ||
			r.Header.Get("Content-Type") != "application/octet-stream" || string(body) != "bytes" ||
			r.Header.Get("Authorization") != "Bearer jwt" || r.Header.Get(HeaderOrganizationID) != "org-1" {
			t.Errorf("%s %s %q %q headers %v", r.Method, r.URL.Path, r.Header.Get("Content-Type"), body, r.Header)
		}
		_, _ = w.Write([]byte(`{"success":true,"data":{"sha256":"abc123","size":5,"stored":false}}`))
	}))
	defer server.Close()
	api := New(server.URL, "org-1", tokenFunc)

	upload, err := api.UploadBlob(context.Background(), "abc123", []byte("bytes"))

	if err != nil || upload.SHA256 != "abc123" || upload.Size != 5 || upload.Stored {
		t.Fatalf("upload = %+v, %v", upload, err)
	}
}

func TestUploadBlob_hashMismatchIsAnAPIError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte(`{"success":false,"error":{"code":"KAY_CFG_005","message":"hash","errorValues":{"expectedSha256":"a","actualSha256":"b"}}}`))
	}))
	defer server.Close()
	api := New(server.URL, "org", tokenFunc)

	_, err := api.UploadBlob(context.Background(), "a", []byte("x"))

	if !IsCode(err, CodeBlobHashMismatch) {
		t.Fatalf("err = %v, want %s", err, CodeBlobHashMismatch)
	}
}

func TestCreateSeedRun_sendsTheWireShapeAndDecodesTheReport(t *testing.T) {
	var sent map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/kaya/seed-runs" {
			t.Errorf("%s %s", r.Method, r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&sent); err != nil {
			t.Error(err)
		}
		_, _ = w.Write([]byte(`{"success":true,"data":{"id":"run-1","sourceRef":"abc","status":"FAILED","createdAt":"2026-10-23T10:00:00Z","report":{
			"applied":true,"entities":{"AGENTS_FILE":1,"RULE":2},
			"sets":[{"packId":"p1","kind":"PACK","name":"village","outcome":"CREATED","versionId":"v1","versionNumber":1,"fileCount":3,"refusedCount":1,"executableChange":true}],
			"releases":[{"repositoryId":"r1","number":1,"manifestSha256":"m","fileCount":3}],
			"refused":[{"setKind":"PACK","setName":"org","path":"x","errorCode":null,"reason":null,"violations":[{"path":"x","rule":"SYMLINK_TARGET_NOT_ALLOWED","message":"no"}]},{"setKind":"PACK","setName":"village","path":"CLAUDE.md","errorCode":"KAY_CFG_028","reason":"no kind","violations":[]}],
			"violations":[],
			"notImported":[{"path":"CLAUDE.md","reason":"r"}],
			"drift":[{"path":"AGENTS.md","pack":"org","expectedSha256":"e","actualSha256":"a"}]}}}`))
	}))
	defer server.Close()
	api := New(server.URL, "org", tokenFunc)

	run, err := api.CreateSeedRun(context.Background(), SeedRequest{
		SourceRef: "abc",
		Sets: []SeedSet{
			{Kind: KindPack, Name: "org", Files: []SeedFile{{Path: "l", Mode: ModeSymlink, SymlinkTarget: "t"}}},
			{Kind: KindPack, Name: "village", Parent: "org", Files: []SeedFile{}},
			{Kind: KindPack, Name: "kagi", Parent: "village", Files: []SeedFile{{Path: "AGENTS.md", SHA256: "a", Mode: ModeFile}}},
		},
		Repositories: []SeedRepository{{RemoteURL: "git@github.com:a/village.git", Name: "village",
			Packs: []SeedRepositoryPack{{Name: "org"}, {Name: "village"}, {Name: "kagi", RootPath: "projects/kagi"}}}},
		Drift: []Drift{{Path: "AGENTS.md", ExpectedSHA256: "e"}},
	})

	if err != nil {
		t.Fatal(err)
	}
	sets := sent["sets"].([]any)
	pack := sets[0].(map[string]any)
	for _, removed := range []string{"parent", "packs", "remoteUrl", "rootPath"} {
		if _, has := pack[removed]; has {
			t.Errorf("a root pack sends no %s: %v", removed, pack)
		}
	}
	file := pack["files"].([]any)[0].(map[string]any)
	if _, has := file["sha256"]; has || file["symlinkTarget"] != "t" {
		t.Errorf("a symlink file sends no sha256: %v", file)
	}
	if child := sets[2].(map[string]any); child["kind"] != "PACK" || child["parent"] != "village" || len(child) != 4 {
		t.Errorf("child pack = %v, want kind, name, parent and files only", child)
	}
	want := []any{
		map[string]any{"remoteUrl": "git@github.com:a/village.git", "name": "village", "packs": []any{
			map[string]any{"name": "org"}, map[string]any{"name": "village"},
			map[string]any{"name": "kagi", "rootPath": "projects/kagi"},
		}},
	}
	if !reflect.DeepEqual(sent["repositories"], want) {
		t.Errorf("repositories = %v, want %v", sent["repositories"], want)
	}
	if _, has := sent["notImported"]; has {
		t.Error("an empty notImported is left out")
	}
	if run.Status != SeedFailed || run.ID != "run-1" || !run.Report.Applied || run.Report.Entities["RULE"] != 2 ||
		run.Report.Sets[0].PackID != "p1" || run.Report.Sets[0].Kind != KindPack ||
		run.Report.Refused[1].ErrorCode != "KAY_CFG_028" || run.Report.Refused[1].Reason != "no kind" ||
		*run.Report.Sets[0].VersionNumber != 1 || !run.Report.Sets[0].ExecutableChange ||
		run.Report.Releases[0].FileCount != 3 || run.Report.Refused[0].Violations[0].Rule != "SYMLINK_TARGET_NOT_ALLOWED" ||
		run.Report.NotImported[0].Path != "CLAUDE.md" || run.Report.Drift[0].ActualSHA256 != "a" {
		t.Errorf("run = %+v", run)
	}
}

func TestLatestRelease_noReleaseIsNil(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/kaya/repositories/r1/releases" || r.URL.Query().Get("page") != "1" || r.URL.Query().Get("amount") != "1" {
			t.Errorf("url = %s", r.URL)
		}
		_, _ = w.Write([]byte(`{"success":true,"data":[]}`))
	}))
	defer server.Close()
	api := New(server.URL, "org", tokenFunc)

	release, err := api.LatestRelease(context.Background(), "r1")

	if err != nil || release != nil {
		t.Fatalf("release = %+v, %v", release, err)
	}
}

func TestLatestRelease_readsTheNewestWithItsFiles(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/kaya/repositories/r1/releases":
			_, _ = w.Write([]byte(`{"success":true,"data":[{"id":"x","number":7}]}`))
		case "/kaya/repositories/r1/releases/7":
			_, _ = w.Write([]byte(`{"success":true,"data":{"id":"x","repositoryId":"r1","number":7,"fileCount":1,"files":[{"path":"AGENTS.md","sha256":"h","mode":"FILE"}]}}`))
		default:
			t.Errorf("unexpected %s", r.URL)
		}
	}))
	defer server.Close()
	api := New(server.URL, "org", tokenFunc)

	release, err := api.LatestRelease(context.Background(), "r1")

	if err != nil || release == nil || release.Number != 7 || len(release.Files) != 1 || release.Files[0].SHA256 != "h" {
		t.Fatalf("release = %+v, %v", release, err)
	}
}

func TestMissingBlobs_otherErrorsAndShapesYieldNothing(t *testing.T) {
	if got := MissingBlobs(nil); got != nil {
		t.Errorf("nil error = %v", got)
	}
	if got := MissingBlobs(&APIError{Code: "KAY_CFG_005", ErrorValues: map[string]any{"missing": []any{"a"}}}); got != nil {
		t.Errorf("other code = %v", got)
	}
	if got := MissingBlobs(&APIError{Code: CodeSeedBlobsMissing, ErrorValues: map[string]any{"missing": "a"}}); got != nil {
		t.Errorf("wrong shape = %v", got)
	}
}
