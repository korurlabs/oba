package upload

import (
	"bytes"
	"errors"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestPrintReport(t *testing.T) {
	report := newReport([]PageResult{
		{Path: "a", Outcome: OutcomeUnchanged, Version: 3},
		pageError("a/b", &client.APIError{Status: 403, Code: "KLK_SEC_001", Message: "denied"}),
		skippedBelow("a/b/c", "a/b"),
	}, true, nil)
	var out bytes.Buffer
	PrintReport(&out, report)
	want := "unchanged a (version 3)\n" +
		"denied    a/b\n" +
		"skipped   a/b/c: not uploaded: a/b above it was not written (PARENT_NOT_WRITTEN)\n" +
		"3 pages: 1 unchanged, 1 denied, 1 skipped (dry run, nothing written)\n"
	if out.String() != want {
		t.Fatalf("out = %q\nwant  %q", out.String(), want)
	}

	out.Reset()
	PrintReport(&out, EmptyReport())
	if out.String() != "" {
		t.Fatalf("an empty report prints nothing, got %q", out.String())
	}
}

func TestPrintErrorsPrintsEachOnceAndNoSkippedPage(t *testing.T) {
	cause := errors.New("You are not logged in. Run 'oba login'.")
	report := newReport([]PageResult{
		pageError("a/b", &client.APIError{Status: 409, Code: client.CodeKalkanPageChanged, Message: "changed"}),
		skipped("a/c", cause),
		skippedBelow("a/b/c", "a/b"),
	}, false, []error{cause})
	var errOut bytes.Buffer
	PrintErrors(&errOut, report)
	want := "Error: You are not logged in. Run 'oba login'. (CLI_ERROR)\n" +
		"Error: a/b: changed Pass --force to overwrite it. (KLK_STA_004)\n"
	if errOut.String() != want {
		t.Fatalf("errOut = %q\nwant     %q", errOut.String(), want)
	}
}
