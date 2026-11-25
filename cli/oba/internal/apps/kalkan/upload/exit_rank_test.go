package upload

import (
	"errors"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestWorseExitPrecedence(t *testing.T) {
	// 1 > 3 > 2 > 4 > 0
	order := []int{exitFailure, exitForbidden, exitNotFound, exitConflict, exitOK}
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
	if got := worseExit(exitForbidden, 42); got != 42 {
		t.Errorf("an unknown status ranks as a failure: got %d", got)
	}
}

func TestExitStatusReadsPagesAndRunErrors(t *testing.T) {
	cases := []struct {
		name   string
		report Report
		want   int
	}{
		{"all written", newReport([]PageResult{{Outcome: OutcomeCreated}, {Outcome: OutcomeUnchanged}}, false, nil), 0},
		{"empty", EmptyReport(), 0},
		{"conflict", newReport([]PageResult{pageError("a", &client.APIError{Status: 409, Code: client.CodeKalkanPageChanged})}, false, nil), 4},
		{"hidden over conflict", newReport([]PageResult{
			pageError("a", &client.APIError{Status: 409, Code: client.CodeConcurrentWrite}),
			pageError("b", &client.APIError{Status: 404}),
		}, false, nil), 2},
		{"skipped pages add nothing", newReport([]PageResult{skippedBelow("a/b", "a")}, false, nil), 0},
		{"run error 403", newReport(nil, false, []error{&client.APIError{Status: 403}}), 3},
		{"run error plain", newReport([]PageResult{pageError("a", &client.APIError{Status: 404})}, false, []error{errors.New("no session")}), 1},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := ExitStatus(tc.report); got != tc.want {
				t.Fatalf("ExitStatus = %d, want %d", got, tc.want)
			}
		})
	}
}
