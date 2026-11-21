package delivery

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestIsExecutable_needsAnAbsoluteExecutableFile(t *testing.T) {
	dir := t.TempDir()
	binary := filepath.Join(dir, "claude")
	if err := os.WriteFile(binary, []byte("#!/bin/sh\n"), 0o700); err != nil {
		t.Fatal(err)
	}
	if !isExecutable(binary) || isExecutable("claude") || isExecutable("--resume") || isExecutable(dir) {
		t.Fatal("wrong wrapper argument rule")
	}
}

func TestWithoutSessionVars_dropsAnEnclosingSessionsRelease(t *testing.T) {
	kept := withoutSessionVars([]string{"PATH=/bin", EnvLaunched + "=1", EnvRelease + "=4", EnvReleaseState + "=FRESH", EnvCheckout + "=/x", EnvOffline + "=down"})
	if len(kept) != 1 || kept[0] != "PATH=/bin" {
		t.Fatalf("kept = %v", kept)
	}
}

func TestOfflineReason_isOneShortNonEmptyLine(t *testing.T) {
	if got := offlineReason(errors.New("a\n  b\tc")); got != "a b c" {
		t.Fatalf("reason = %q", got)
	}
	if got := offlineReason(errors.New(strings.Repeat("x", 2*maxOfflineReason))); len(got) != maxOfflineReason {
		t.Fatalf("length = %d", len(got))
	}
	if got := offlineReason(errors.New(" ")); got != "unknown" {
		t.Fatalf("empty reason = %q", got)
	}
}
