package delivery

import (
	"encoding/json"
	"fmt"
	"strings"
	"testing"
)

const guardrailPath = ".agents/rules/guardrails/protect-secrets.md"

func denyOf(t *testing.T, content []byte) []string {
	t.Helper()
	var parsed struct {
		Permissions struct {
			Deny []string `json:"deny"`
		} `json:"permissions"`
	}
	if err := json.Unmarshal(content, &parsed); err != nil {
		t.Fatalf("settings %s: %v", content, err)
	}
	return parsed.Permissions.Deny
}

func TestMergeDeny_guardrailRulesAfterTheFilesOwnEachOnce(t *testing.T) {
	content := []byte("{\n  \"model\": \"opus\",\n  \"permissions\": {\n    \"deny\": [\"Bash(rm:*)\"]\n  }\n}\n")
	// Two guardrails share Read(./.env), and one repeats the file's own rule.
	guardrails := []string{"Read(./.env)", "Bash(rm:*)", "Bash(git push --force:*)", "Read(./.env)"}

	got, err := MergeDeny(content, guardrails...)
	if err != nil {
		t.Fatal(err)
	}

	want := "Bash(rm:*),Read(./.env),Bash(git push --force:*)"
	if deny := strings.Join(denyOf(t, got), ","); deny != want {
		t.Fatalf("deny = %s, want %s", deny, want)
	}
	if !strings.Contains(string(got), `"model": "opus"`) || strings.Index(string(got), "model") > strings.Index(string(got), "permissions") {
		t.Fatalf("the rest of the file changed: %s", got)
	}
}

func TestMergeDeny_addsPermissionsToAFileWithout(t *testing.T) {
	content := []byte("{\n  \"model\": \"sonnet\"\n}\n")

	got, err := MergeDeny(content, "Read(./.env)", "Read(./.env.*)")
	if err != nil {
		t.Fatal(err)
	}

	if deny := strings.Join(denyOf(t, got), ","); deny != "Read(./.env),Read(./.env.*)" {
		t.Fatalf("deny = %s", deny)
	}
}

func TestMergeDeny_byteExactWithoutRules(t *testing.T) {
	// Spacing and key order a re-encoding would change.
	content := []byte("{\"permissions\":{\"deny\":[\"Bash(rm:*)\"],   \"allow\":[\"Read\"]}}")
	for name, guardrails := range map[string][]string{"nil": nil, "empty": {}} {
		t.Run(name, func(t *testing.T) {
			got, err := MergeDeny(content, guardrails...)
			if err != nil || string(got) != string(content) {
				t.Fatalf("got %q, %v", got, err)
			}
		})
	}
}

func TestGuardrailDeny_listsEveryRuleInManifestOrder(t *testing.T) {
	m := Manifest{Files: []ManifestFile{
		{Path: ".agents/rules/guardrails/a.md", Deny: []string{"Bash(rm:*)", "Read(./.env)"}},
		{Path: ".agents/rules/core.md"},
		{Path: ".agents/rules/guardrails/b.md", Deny: []string{"Read(./.env)"}},
	}}

	if got := strings.Join(m.GuardrailDeny(), ","); got != "Bash(rm:*),Read(./.env),Read(./.env)" {
		t.Fatalf("rules = %s", got)
	}
}

func TestManifestProblems_denyOnlyOnAGuardrail(t *testing.T) {
	guardrail := func(path string, deny ...string) ManifestFile {
		return ManifestFile{Path: path, Mode: ModeFile, SHA256: sha(path), AlwaysOn: true, Deny: deny}
	}
	ok := Manifest{Files: []ManifestFile{
		guardrail(guardrailPath, "Read(./.env)", "Bash(cat .env:*)"),
		guardrail(".agents/rules/guardrails/english.md"),
	}}
	if problems := ManifestProblems(ok); len(problems) != 0 {
		t.Fatalf("refused guardrails: %v", problems)
	}
	tooMany := make([]string, maxDenyRules+1)
	for i := range tooMany {
		tooMany[i] = fmt.Sprintf("Bash(tool-%d:*)", i)
	}
	cases := map[string]ManifestFile{
		"deny on a rule":                 guardrail(".agents/rules/core.md", "Bash(rm:*)"),
		"deny on settings":               guardrail(SettingsPath, "Bash(rm:*)"),
		"deny on a non-markdown file":    guardrail(".agents/rules/guardrails/notes.txt", "Bash(rm:*)"),
		"deny on a link":                 {Path: ".agents/rules/guardrails/x.md", Mode: ModeSymlink, Target: "../core.md", Deny: []string{"Bash(rm:*)"}},
		"an empty rule":                  guardrail(guardrailPath, "Read(./.env)", ""),
		"a blank rule":                   guardrail(guardrailPath, "  "),
		"more than twenty rules":         guardrail(guardrailPath, tooMany...),
		"a path that only looks similar": guardrail(".agents/rules/guardrails-old/x.md", "Bash(rm:*)"),
	}
	for name, file := range cases {
		t.Run(name, func(t *testing.T) {
			if problems := ManifestProblems(Manifest{Files: []ManifestFile{file}}); len(problems) == 0 {
				t.Fatal("accepted")
			}
		})
	}
}

func withDeny(path string, deny ...string) func(*Manifest) {
	return func(m *Manifest) {
		for i := range m.Files {
			if m.Files[i].Path == path {
				m.Files[i].Deny = deny
			}
		}
	}
}

func TestSync_refusesDenyOutsideAGuardrailAndKeepsTheActiveRelease(t *testing.T) {
	f := newFixture(t)
	f.release(1, baseFiles("v1\n"))
	f.sync(Pin)
	f.release(2, baseFiles("v2\n"), withDeny("AGENTS.md", "Bash(rm:*)"))

	out := f.sync(Pin)

	if out.Switched || out.Active != 1 || out.Offline == nil || !strings.Contains(out.Offline.Error(), "AGENTS.md: only a guardrail file") {
		t.Fatalf("outcome = %+v", out)
	}
	if active := mustActive(t, f); active.Manifest.Number != 1 {
		t.Fatalf("active release = %d", active.Manifest.Number)
	}
	if got := f.read("AGENTS.md"); got != "v1\n" {
		t.Fatalf("AGENTS.md = %q", got)
	}
}
