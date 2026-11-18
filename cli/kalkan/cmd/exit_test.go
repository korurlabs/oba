package cmd

import (
	"errors"
	"fmt"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/kalkan-cli/internal/auth"
	"github.com/korurlabs/kalkan-cli/internal/client"
)

func TestExitCodeOf(t *testing.T) {
	cases := []struct {
		name string
		err  error
		want int
	}{
		{"nil", nil, ExitOK},
		{"plain error", errors.New("boom"), ExitFailure},
		{"not signed in", auth.ErrNotSignedIn, ExitNotSignedIn},
		{"wrapped not signed in", fmt.Errorf("refresh: %w", auth.ErrNotSignedIn), ExitNotSignedIn},
		{"401", &client.APIError{Status: 401}, ExitNotSignedIn},
		{"conflict code", &client.APIError{Status: 409, Code: client.CodeChangedSincePush}, ExitConflict},
		{"concurrent write", &client.APIError{Status: 409, Code: client.CodeConcurrentWrite}, ExitConflict},
		{"403", &client.APIError{Status: 403, Code: "KLK_SEC_001"}, ExitDenied},
		{"404", &client.APIError{Status: 404, Code: client.CodePageNotFound}, ExitDenied},
		{"no app grant", &client.APIError{Status: 400, Code: client.CodeNoAppAccess}, ExitDenied},
		{"422", &client.APIError{Status: 422, Code: "KLK_VAL_001"}, ExitFailure},
		{"500", &client.APIError{Status: 500}, ExitFailure},
		{"wrapped api error", fmt.Errorf("upload: %w", &client.APIError{Status: 403}), ExitDenied},
		{"a message that says 403 is not a 403", errors.New("HTTP 403 forbidden"), ExitFailure},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := exitCodeOf(tc.err); got != tc.want {
				t.Fatalf("exitCodeOf = %d, want %d", got, tc.want)
			}
		})
	}
}

func TestWorseExitPrecedence(t *testing.T) {
	// 1 > 2 > 4 > 3 > 0
	order := []int{ExitFailure, ExitNotSignedIn, ExitDenied, ExitConflict, ExitOK}
	for i, higher := range order {
		for _, lower := range order[i:] {
			if got := worseExit(lower, higher); got != higher {
				t.Errorf("worseExit(%d, %d) = %d, want %d", lower, higher, got, higher)
			}
			if got := worseExit(higher, lower); got != higher {
				t.Errorf("worseExit(%d, %d) = %d, want %d", higher, lower, got, higher)
			}
		}
	}
	if got := worseExit(ExitOK, 42); got != 42 {
		t.Errorf("an unknown code ranks as a failure, above OK: got %d", got)
	}
	if got := worseExit(ExitNotSignedIn, 42); got != 42 {
		t.Errorf("an unknown code ranks as a failure, above not signed in: got %d", got)
	}
	if got := worseExit(42, ExitDenied); got != 42 {
		t.Errorf("an unknown current code stays a failure: got %d", got)
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
		{"401 hint", &client.APIError{Status: http.StatusUnauthorized, Message: "expired"}, "HTTP_401", "run kalkan login"},
		{"not signed in", auth.ErrNotSignedIn, codeNotSignedIn, "kalkan login"},
		{"cli", errors.New("plain"), codeCLIError, "plain"},
		{"conflict detail", &client.APIError{Status: 409, Code: client.CodeChangedSincePush, Message: "changed",
			ErrorValues: map[string]any{"updatedByName": "Ada", "updatedAt": "T"}}, client.CodeChangedSincePush, "Last edited by Ada at T. Pass --force"},
		{"conflict detail time only", &client.APIError{Status: 409, Code: client.CodeChangedSincePush, Message: "changed",
			ErrorValues: map[string]any{"updatedAt": "T"}}, client.CodeChangedSincePush, "Last edited at T."},
		{"wrapped keeps context", fmt.Errorf("upload a.png: a hint: %w", &client.APIError{Status: 422, Code: "STG_VAL_005", Message: "dup"}), "STG_VAL_005", "upload a.png: a hint: dup"},
		{"joined keeps context", errors.Join(errors.New("first"), &client.APIError{Status: 422, Code: "X", Message: "dup"}), "X", "first"},
		{"wrapped 401 keeps hint", fmt.Errorf("list: %w", &client.APIError{Status: http.StatusUnauthorized, Message: "expired"}), "HTTP_401", "list: expired; run kalkan login"},
		{"conflict detail none", &client.APIError{Status: 409, Code: client.CodeChangedSincePush, Message: "changed"}, client.CodeChangedSincePush, "Pass --force to overwrite it."},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			code, message := describe(tc.err)
			if code != tc.code || !strings.Contains(message, tc.substr) {
				t.Fatalf("describe = %q, %q; want %q containing %q", code, message, tc.code, tc.substr)
			}
		})
	}
	if _, message := describe(&client.APIError{Status: 409, Code: client.CodeChangedSincePush, Message: "m", ErrorValues: map[string]any{"updatedAt": "T"}}); strings.Contains(message, "Last edited by") {
		t.Fatal("no editor name, no 'by'")
	}
}

func TestExitErrorMessage(t *testing.T) {
	if got := (&ExitError{Code: 3}).Error(); got != "exit status 3" {
		t.Fatalf("Error() = %q", got)
	}
}
