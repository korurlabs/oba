package delivery

import (
	"bytes"
	"context"
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

func TestStartSession_aTrackedPathInANewerReleaseRegistersTheSessionOnTheActiveOne(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	f.commit(map[string]string{"docs/private/conventions/team.md": "ours\n"})
	f.release(2, filesWith("v2\n", spec{path: "docs/private/conventions/team.md", content: "team\n"}))
	f.d.ExcludePID = os.Getpid()
	var stderr bytes.Buffer

	session, offline := startSession(context.Background(), f.d, &stderr)

	if session.Release != 1 || session.State == StateUnverified || session.State == StateNone || offline != nil {
		t.Fatalf("session = %+v, offline = %v\n%s", session, offline, stderr.String())
	}
	printed := stderr.String()
	for _, want := range []string{
		"oba kaya: Warning: release 2 was not placed: git tracks 1 path(s)",
		"git rm --cached -- docs/private/conventions/team.md",
	} {
		if !strings.Contains(printed, want) {
			t.Fatalf("stderr lacks %q:\n%s", want, printed)
		}
	}
	if f.read("AGENTS.md") != "v1\n" || f.read("docs/private/conventions/team.md") != "ours\n" {
		t.Fatalf("AGENTS.md = %q, team.md = %q", f.read("AGENTS.md"), f.read("docs/private/conventions/team.md"))
	}
	live, err := f.d.Checkout.LiveSessions(0)
	if err != nil || len(live) != 1 || live[0].PID != os.Getpid() || live[0].Release != 1 || live[0].State != session.State {
		t.Fatalf("registered sessions = %+v, %v", live, err)
	}
}
