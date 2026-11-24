package install

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestSaveInForce_writesAProofThatLoadInForceReadsAndInstallTrusts(t *testing.T) {
	opts := testOptions(t)
	sha := installDropIn(t, opts)
	provedAt := time.Date(2026, 11, 23, 15, 4, 5, 0, time.FixedZone("CET", 3600))
	if err := SaveInForce(opts.InForcePath, InForceProof{DropInSha256: sha, ProvedAt: provedAt}); err != nil {
		t.Fatal(err)
	}
	raw := readFile(t, opts.InForcePath)
	for _, want := range []string{`"dropInSha256": "` + sha + `"`, `"provedAt": "2026-11-23T14:04:05Z"`} {
		if !strings.Contains(raw, want) {
			t.Fatalf("the proof lacks %s:\n%s", want, raw)
		}
	}
	info, err := os.Stat(opts.InForcePath)
	if err != nil || info.Mode().Perm() != 0o600 {
		t.Fatalf("the proof's mode = %v, %v; want 0600", info.Mode().Perm(), err)
	}
	proof, ok, err := LoadInForce(opts.InForcePath)
	if err != nil || !ok || proof.DropInSha256 != sha || !proof.ProvedAt.Equal(provedAt) {
		t.Fatalf("LoadInForce = %+v, %v, %v", proof, ok, err)
	}
	if state := checkDropIn(opts); !state.inForce {
		t.Fatalf("install does not trust the saved proof: %+v", state)
	}
}

func TestSaveInForce_refusesAProofLoadInForceWouldRefuse(t *testing.T) {
	path := filepath.Join(t.TempDir(), InForceName)
	upper := strings.Repeat("A", 64)
	for name, proof := range map[string]InForceProof{
		"upper-case hex": {DropInSha256: upper, ProvedAt: time.Now()},
		"short sha":      {DropInSha256: "abc", ProvedAt: time.Now()},
		"no time":        {DropInSha256: strings.Repeat("a", 64)},
	} {
		if err := SaveInForce(path, proof); err == nil {
			t.Fatalf("%s: SaveInForce accepted %+v", name, proof)
		}
	}
	if _, err := os.Stat(path); !os.IsNotExist(err) {
		t.Fatalf("a refused proof was written: %v", err)
	}
}

func TestRemoveInForce_removesTheProofAndAcceptsNone(t *testing.T) {
	opts := testOptions(t)
	if err := RemoveInForce(opts.InForcePath); err != nil {
		t.Fatalf("removing a missing proof: %v", err)
	}
	writeProof(t, opts, installDropIn(t, opts))
	if err := RemoveInForce(opts.InForcePath); err != nil {
		t.Fatal(err)
	}
	if _, ok, err := LoadInForce(opts.InForcePath); ok || err != nil {
		t.Fatalf("the proof is still there: %v, %v", ok, err)
	}
}

func TestDropInSha256_isLowerCaseHex(t *testing.T) {
	got := DropInSha256([]byte("{}\n"))
	if !sha256Hex.MatchString(got) {
		t.Fatalf("DropInSha256 = %q", got)
	}
}

func TestUserSettingsKayaHooks_countsOnlyKayasOwnGroups(t *testing.T) {
	opts := testOptions(t)
	if count, err := UserSettingsKayaHooks(opts); err != nil || count != 0 {
		t.Fatalf("no settings file: %d, %v", count, err)
	}
	write(t, ClaudeSettingsPath(opts), `{"hooks": {
  "SessionStart": [
    {"hooks": [{"type": "command", "command": "/usr/local/bin/oba kaya hook session-start"}]},
    {"hooks": [{"type": "command", "command": "/home/someone/.local/bin/kaya hook record"}]},
    {"hooks": [{"type": "command", "command": "echo mine"}]}
  ],
  "Stop": [{"hooks": [{"type": "command", "command": "/opt/homebrew/bin/oba kaya hook record"}]}]
}}`)
	if count, err := UserSettingsKayaHooks(opts); err != nil || count != 3 {
		t.Fatalf("count = %d, %v; want 3", count, err)
	}
	write(t, ClaudeSettingsPath(opts), `{"hooks": `)
	if _, err := UserSettingsKayaHooks(opts); err == nil || !strings.Contains(err.Error(), ClaudeSettingsPath(opts)) {
		t.Fatalf("a damaged settings file: %v", err)
	}
}

func TestMissingShellFunction_namesEachRcFileWithoutTheFunction(t *testing.T) {
	opts := testOptions(t)
	missing, err := MissingShellFunction(opts)
	if err != nil || len(missing) != 1 || missing[0] != filepath.Join(opts.Home, ".bashrc") {
		t.Fatalf("missing = %v, %v; want the .bashrc of a bash user", missing, err)
	}
	apply(t, opts)
	if missing, err := MissingShellFunction(opts); err != nil || len(missing) != 0 {
		t.Fatalf("after install: %v, %v", missing, err)
	}
	opts.ObaPath = "/opt/homebrew/bin/oba"
	if missing, err := MissingShellFunction(opts); err != nil || len(missing) != 1 {
		t.Fatalf("an rc file naming another oba: %v, %v", missing, err)
	}
}
