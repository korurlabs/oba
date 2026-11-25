package page

import (
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// serveUpsert answers the upsert with result and records its query and decoded body.
func serveUpsert(t *testing.T, api *clitest.MockAPI, status int, result string) (*string, *map[string]any) {
	t.Helper()
	var query string
	var body map[string]any
	api.Handle("PUT "+upsertURL, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		data, err := io.ReadAll(r.Body)
		if err != nil {
			t.Errorf("read body: %v", err)
		}
		if err := json.Unmarshal(data, &body); err != nil {
			t.Errorf("body %q: %v", data, err)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		_, _ = io.WriteString(w, result)
	})
	return &query, &body
}

const updatedJSON = `{"data":{"id":"` + pageID + `","path":"engineering/plans","outcome":"UPDATED","version":4}}`

func TestPagePutFromFile(t *testing.T) {
	api := newPageAPI(t, false)
	query, body := serveUpsert(t, api, http.StatusOK, updatedJSON)
	file := writeFile(t, t.TempDir(), "plans.md", "# Plans\n")

	out, _, err := runCLI(t, "", "kalkan", "page", "put", "/engineering/plans/", "--title", "Plans", "--file", file)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Updated page engineering/plans (version 4)\n" {
		t.Errorf("out = %q", out)
	}
	if *query != "dryRun=false" {
		t.Errorf("query = %q", *query)
	}
	got, err := json.Marshal(*body)
	if err != nil {
		t.Fatal(err)
	}
	want := `{"force":false,"path":"engineering/plans","translations":[{"body":"# Plans\n","locale":"EN","title":"Plans"}]}`
	if string(got) != want {
		t.Errorf("body = %s\nwant   %s", got, want)
	}
	if strings.Join(api.Calls(), ";") != "PUT "+upsertURL {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestPagePutFromStdinWithLangAndForce(t *testing.T) {
	api := newPageAPI(t, false)
	_, body := serveUpsert(t, api, http.StatusOK,
		`{"data":{"id":"`+pageID+`","path":"engineering/plans","outcome":"CREATED","version":1}}`)

	out, _, err := runCLI(t, "# Planlar\n", "kalkan", "page", "put", "engineering/plans",
		"--lang", "TR", "--title", "Planlar", "--file", "-", "--force")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Created page engineering/plans (version 1)\n" {
		t.Errorf("out = %q", out)
	}
	translations, ok := (*body)["translations"].([]any)
	if !ok || len(translations) != 1 {
		t.Fatalf("body = %v", *body)
	}
	tr, ok := translations[0].(map[string]any)
	if !ok || tr["locale"] != "TR" || tr["title"] != "Planlar" || tr["body"] != "# Planlar\n" || (*body)["force"] != true {
		t.Errorf("body = %v", *body)
	}
}

// --compliance sets the mark, --compliance=false clears it, and without the flag it is left out
// so that the stored mark is kept.
func TestPagePutComplianceThreeStates(t *testing.T) {
	cases := []struct {
		flags []string
		want  any
	}{
		{[]string{"--compliance"}, true},
		{[]string{"--compliance=false"}, false},
		{nil, nil},
	}
	for _, tc := range cases {
		t.Run(strings.Join(tc.flags, " "), func(t *testing.T) {
			api := newPageAPI(t, false)
			_, body := serveUpsert(t, api, http.StatusOK, updatedJSON)
			args := append([]string{"kalkan", "page", "put", "engineering/plans", "--title", "P", "--file", "-"}, tc.flags...)
			if _, _, err := runCLI(t, "x", args...); err != nil {
				t.Fatal(err)
			}
			got, present := (*body)["compliance"]
			if tc.want == nil && present || tc.want != nil && got != tc.want {
				t.Errorf("compliance = %v (present %v), want %v", got, present, tc.want)
			}
		})
	}
}

func TestPagePutConflictExits1(t *testing.T) {
	api := newPageAPI(t, false)
	serveUpsert(t, api, http.StatusConflict, `{"error":{"code":"KLK_STA_004","message":"changed in Kalkan since the last push"}}`)
	_, _, err := runCLI(t, "x", "kalkan", "page", "put", "engineering/plans", "--title", "P", "--file", "-")
	if err == nil || !strings.HasPrefix(err.Error(), "KLK_STA_004") || client.ExitCode(err) != 1 {
		t.Errorf("err = %v, exit %d", err, client.ExitCode(err))
	}
}

func TestPagePutJSONIsTheOutcome(t *testing.T) {
	api := newPageAPI(t, false)
	serveUpsert(t, api, http.StatusOK, updatedJSON)
	buf := captureJSON(t)
	if _, _, err := runCLI(t, "x", "kalkan", "page", "put", "engineering/plans", "--title", "P", "--file", "-"); err != nil {
		t.Fatal(err)
	}
	var result client.KalkanPageUpsertResult
	if err := json.Unmarshal([]byte(flushJSON(t, buf)), &result); err != nil {
		t.Fatal(err)
	}
	if result != (client.KalkanPageUpsertResult{ID: pageID, Path: "engineering/plans", Outcome: "UPDATED", Version: 4}) {
		t.Errorf("result = %+v", result)
	}
}

func TestPagePutRefusesBadInputBeforeAnyCall(t *testing.T) {
	api := newPageAPI(t, false)
	cases := map[string][]string{
		"is deeper than 10 pages": {strings.Repeat("a/", 11), "--title", "T", "--file", "-"},
		"--title is required":     {"a", "--file", "-"},
		"--file is required":      {"a", "--title", "T"},
		`unknown language "de"`:   {"a", "--title", "T", "--file", "-", "--lang", "de"},
	}
	for want, args := range cases {
		_, _, err := runCLI(t, "x", append([]string{"kalkan", "page", "put"}, args...)...)
		if err == nil || !strings.Contains(err.Error(), want) || client.ExitCode(err) != 1 {
			t.Errorf("%v: err = %v, want %q", args, err, want)
		}
	}
	noCalls(t, api)
}
