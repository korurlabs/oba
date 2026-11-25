package upload

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestClassify(t *testing.T) {
	cases := []struct {
		name    string
		err     error
		outcome string
		exit    int
	}{
		{"plain error", errors.New("boom"), OutcomeFailed, 1},
		{"page changed", &client.APIError{Status: 409, Code: client.CodeKalkanPageChanged}, OutcomeConflict, 4},
		{"concurrent write", &client.APIError{Status: 409, Code: client.CodeConcurrentWrite}, OutcomeConflict, 4},
		{"403", &client.APIError{Status: 403, Code: "KLK_SEC_001"}, OutcomeDenied, 3},
		{"404", &client.APIError{Status: 404, Code: client.CodeKalkanPageNotFound}, OutcomeDenied, 2},
		{"401", &client.APIError{Status: 401}, OutcomeFailed, 1},
		{"422", &client.APIError{Status: 422, Code: "KLK_VAL_001"}, OutcomeFailed, 1},
		{"wrapped 403", fmt.Errorf("upload: %w", &client.APIError{Status: 403}), OutcomeDenied, 3},
		{"a message that says 403 is not a 403", errors.New("HTTP 403 forbidden"), OutcomeFailed, 1},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if outcome, exit := classify(tc.err); outcome != tc.outcome || exit != tc.exit {
				t.Fatalf("classify = %s, %d; want %s, %d", outcome, exit, tc.outcome, tc.exit)
			}
		})
	}
}

func TestStopsRunOnlyOnARefusedSignIn(t *testing.T) {
	if !stopsRun(fmt.Errorf("list: %w", &client.APIError{Status: http.StatusUnauthorized})) {
		t.Error("a wrapped 401 stops the run")
	}
	if !stopsRun(fmt.Errorf("upload a.png: %w", fmt.Errorf("%w and could not be refreshed (%w)", client.ErrSessionExpired, errors.New("invalid_grant")))) {
		t.Error("a session that could not be refreshed stops the run")
	}
	if stopsRun(&client.APIError{Status: http.StatusForbidden}) || stopsRun(errors.New("401")) {
		t.Error("only an API 401 stops the run")
	}
}

func TestDescribe(t *testing.T) {
	cases := []struct {
		name         string
		err          error
		code, substr string
	}{
		{"coded", &client.APIError{Status: 403, Code: "KLK_SEC_001", Message: "no"}, "KLK_SEC_001", "no"},
		{"codeless", &client.APIError{Status: 502, Message: "bad gateway"}, "HTTP_502", "bad gateway"},
		{"401 hint", &client.APIError{Status: 401, Message: "expired"}, "HTTP_401", "expired; run 'oba login'"},
		{"cli", errors.New("plain"), codeCLIError, "plain"},
		{"conflict detail", &client.APIError{Status: 409, Code: client.CodeKalkanPageChanged, Message: "changed",
			Fields: map[string]string{"updatedByName": "Ada", "updatedAt": "T"}}, client.CodeKalkanPageChanged, "changed Last edited by Ada at T. Pass --force"},
		{"conflict detail time only", &client.APIError{Status: 409, Code: client.CodeKalkanPageChanged, Message: "changed",
			Fields: map[string]string{"updatedAt": "T"}}, client.CodeKalkanPageChanged, "Last edited at T."},
		{"conflict detail none", &client.APIError{Status: 409, Code: client.CodeKalkanPageChanged, Message: "changed"}, client.CodeKalkanPageChanged, "changed Pass --force to overwrite it."},
		{"wrapped keeps context", fmt.Errorf("upload a.png: a hint: %w", &client.APIError{Status: 422, Code: "STG_VAL_005", Message: "dup"}), "STG_VAL_005", "upload a.png: a hint: dup"},
		{"joined keeps context", errors.Join(errors.New("first"), &client.APIError{Status: 422, Code: "X", Message: "dup"}), "X", "first"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			detail := describe(tc.err)
			if detail.Code != tc.code || !strings.Contains(detail.Message, tc.substr) {
				t.Fatalf("describe = %+v; want %q containing %q", detail, tc.code, tc.substr)
			}
		})
	}
	detail := describe(&client.APIError{Status: 409, Code: client.CodeKalkanPageChanged, Message: "m", Fields: map[string]string{"updatedAt": "T"}})
	if strings.Contains(detail.Message, "Last edited by") || strings.Contains(detail.Message, "\n") {
		t.Fatalf("message %q: no editor name, no 'by', and one line", detail.Message)
	}
}

func TestSkippedResults(t *testing.T) {
	got := skipped("a/b", &client.APIError{Status: 500, Message: "list failed"})
	if got.Outcome != OutcomeSkipped || *got.Error != (ErrorDetail{Code: "HTTP_500", Message: "not uploaded: list failed"}) {
		t.Errorf("skipped = %+v %+v", got, got.Error)
	}
	got = skippedBelow("a/b/c", "a/b")
	if *got.Error != (ErrorDetail{Code: CodeParentNotWritten, Message: "not uploaded: a/b above it was not written"}) {
		t.Errorf("skippedBelow = %+v", got.Error)
	}
}

func TestReportJSONShape(t *testing.T) {
	report := newReport([]PageResult{
		{Path: "a", Outcome: OutcomeCreated, Version: 1},
		pageError("a/b", &client.APIError{Status: 403, Code: "KLK_SEC_001", Message: "denied"}),
	}, true, []error{errors.New("left out")})
	encoded, err := json.Marshal(report)
	if err != nil {
		t.Fatal(err)
	}
	want := `{"counts":{"conflict":0,"created":1,"denied":1,"failed":0,"skipped":0,"unchanged":0,"updated":0},` +
		`"pages":[{"path":"a","outcome":"created","version":1},` +
		`{"path":"a/b","outcome":"denied","version":0,"error":{"code":"KLK_SEC_001","message":"denied"}}]}`
	if string(encoded) != want {
		t.Fatalf("report = %s\nwant     %s", encoded, want)
	}
	if encoded, _ := json.Marshal(EmptyReport()); !strings.Contains(string(encoded), `"pages":[]`) {
		t.Fatalf("empty report = %s: pages is an array, never null", encoded)
	}
}
