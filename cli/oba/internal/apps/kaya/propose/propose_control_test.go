package propose

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
)

func TestCollect_deliveredLinkRetargetedOrReplacedByAFileOrADirectory(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	skill := filepath.Join(root, ".claude/skills/demo")
	if err := os.Remove(skill); err != nil {
		t.Fatal(err)
	}
	link(t, "../../.agents/skills/other", skill)

	changes, err := Collect(src, nil, nil)
	if err != nil || describe(changes) != "M .claude/skills/demo -> ../../.agents/skills/other" {
		t.Fatalf("retargeted link: %q, %v", describe(changes), err)
	}

	if err := os.Remove(skill); err != nil {
		t.Fatal(err)
	}
	write(t, root, ".claude/skills/demo", "now a file\n", 0o644)
	changes, err = Collect(src, nil, nil)
	if err != nil || describe(changes) != "M .claude/skills/demo (SYMLINK -> FILE)" {
		t.Fatalf("link replaced by a file: %q, %v", describe(changes), err)
	}

	if err := os.Remove(skill); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(skill, 0o750); err != nil {
		t.Fatal(err)
	}
	if _, err := Collect(src, nil, nil); err == nil || !strings.Contains(err.Error(), "is a directory where release 4 delivers a file") {
		t.Fatalf("link replaced by a directory: %v", err)
	}
}

func TestCollect_scanSkipsTemporaryLinksUnallowedFilesAndMemory(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	write(t, root, ".agents/rules/a.md.kaya-tmp", "temporary\n", 0o644)
	write(t, root, ".agents/memory/lessons.md", "git only\n", 0o644)
	write(t, root, ".claude/worktrees/x/AGENTS.md", "a worktree\n", 0o644)
	write(t, root, "README.md", "outside the allow-list\n", 0o644)

	changes, err := Collect(src, nil, nil)

	if err != nil || len(changes) != 0 {
		t.Fatalf("changes = %q, err = %v", describe(changes), err)
	}
}

func TestCollect_newExecutableFileAndNewLinkAreAdded(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	write(t, root, ".agents/hooks/new.sh", "#!/bin/sh\n", 0o755)
	link(t, "../skills/demo/SKILL.md", filepath.Join(root, ".agents/rules/link.md"))

	changes, err := Collect(src, nil, nil)

	want := "A .agents/hooks/new.sh (executable)\nA .agents/rules/link.md -> ../skills/demo/SKILL.md"
	if err != nil || describe(changes) != want {
		t.Fatalf("changes:\n%s\nerr = %v", describe(changes), err)
	}
	if err := Check(src, changes); err != nil {
		t.Fatalf("check: %v", err)
	}
}

func TestCollect_fromScanListsEveryConfigFileOfTheDirectory(t *testing.T) {
	src := checkout(t, baseFiles())
	scratch := t.TempDir()
	write(t, scratch, "AGENTS.md", "# Rules\n", 0o644)
	write(t, scratch, ".agents/rules/new.md", "new\n", 0o644)
	write(t, scratch, "notes.txt", "outside the allow-list\n", 0o644)
	src.From = scratch

	changes, err := Collect(src, nil, nil)

	// A scan of --from never counts an absent delivered path as removed.
	if err != nil || describe(changes) != "A .agents/rules/new.md" {
		t.Fatalf("changes = %q, err = %v", describe(changes), err)
	}
}

func TestCollect_removeConflictsWithANamedPath(t *testing.T) {
	src := checkout(t, baseFiles())

	_, err := Collect(src, []string{"AGENTS.md"}, []string{"AGENTS.md"})

	if err == nil || !strings.Contains(err.Error(), "named to change and to remove") {
		t.Fatalf("err = %v", err)
	}
}

func TestCollect_namedPathNeitherDeliveredNorPresent(t *testing.T) {
	src := checkout(t, baseFiles())

	_, err := Collect(src, []string{".agents/rules/ghost.md"}, nil)

	if err == nil || !strings.Contains(err.Error(), "neither delivered by release 4 nor in the checkout") {
		t.Fatalf("err = %v", err)
	}
}

func TestResolveArgs_relativeToCwdAndRefusesTheRootAndOutsiders(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root

	paths, err := ResolveArgs(src, filepath.Join(root, ".agents/skills"), []string{"demo", "../hooks/dispatch.sh"})
	if err != nil || strings.Join(paths, ",") != ".agents/skills/demo,.agents/hooks/dispatch.sh" {
		t.Fatalf("paths = %v, err = %v", paths, err)
	}
	if _, err := ResolveArgs(src, root, []string{"."}); err == nil || !strings.Contains(err.Error(), "is the root") {
		t.Fatalf("root: %v", err)
	}
	if _, err := ResolveArgs(src, root, []string{"../elsewhere/AGENTS.md"}); err == nil || !strings.Contains(err.Error(), "is outside") {
		t.Fatalf("outside: %v", err)
	}
}

func TestCheck_settingsLinkAndNonObjectSettingsAreRefused(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	if err := os.Remove(filepath.Join(root, ".claude/settings.json")); err != nil {
		t.Fatal(err)
	}
	link(t, "../AGENTS.md", filepath.Join(root, ".claude/settings.json"))

	changes, err := Collect(src, []string{".claude/settings.json"}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if err := Check(src, changes); err == nil || !strings.Contains(err.Error(), ".claude/settings.json: settings are never a link") {
		t.Fatalf("settings link: %v", err)
	}

	for name, content := range map[string]string{"null": "null\n", "text": "not json\n", "string": "\"x\"\n", "empty": ""} {
		if err := os.Remove(filepath.Join(root, ".claude/settings.json")); err != nil {
			t.Fatal(err)
		}
		write(t, root, ".claude/settings.json", content, 0o644)
		changes, err := Collect(src, []string{".claude/settings.json"}, nil)
		if err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		if err := Check(src, changes); err == nil || !strings.Contains(err.Error(), "is not a JSON object") {
			t.Errorf("%s: %v", name, err)
		}
	}
}

func TestCheck_acceptsAnEmptyObjectAndHookLinkIsRefusedOnTheReleasePath(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	if err := os.Remove(filepath.Join(root, ".claude/settings.json")); err != nil {
		t.Fatal(err)
	}
	write(t, root, ".claude/settings.json", "{}\n", 0o644)
	changes, err := Collect(src, nil, nil)
	if err != nil || Check(src, changes) != nil {
		t.Fatalf("empty object: %v / %v", err, Check(src, changes))
	}

	// The delivered hook replaced by a link is refused as well.
	if err := os.Remove(filepath.Join(root, ".agents/hooks/dispatch.sh")); err != nil {
		t.Fatal(err)
	}
	link(t, "elsewhere.sh", filepath.Join(root, ".agents/hooks/dispatch.sh"))
	changes, err = Collect(src, nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	if err := Check(src, changes); err == nil || !strings.Contains(err.Error(), ".agents/hooks/dispatch.sh: a hook is never a link") {
		t.Fatalf("hook link: %v", err)
	}
}

func TestCheck_caseConflictBetweenTwoNewPathsAndWithADeliveredOne(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	write(t, root, ".agents/rules/Alpha.md", "a\n", 0o644)
	write(t, root, ".agents/rules/alpha.md", "b\n", 0o644)
	write(t, root, "agents.md", "case of a delivered path\n", 0o644)

	changes, err := Collect(src, []string{".agents/rules", "agents.md"}, nil)
	if err != nil {
		t.Fatal(err)
	}
	err = Check(src, changes)

	if err == nil || !strings.Contains(err.Error(), "differs from") || !strings.Contains(err.Error(), "only in letter case") {
		t.Fatalf("err = %v", err)
	}
}

func TestCheck_twoHundredFilesPassAndTwoHundredAndOneAreRefused(t *testing.T) {
	src := checkout(t, baseFiles())
	root := src.Checkout.Root
	for i := 0; i < 200; i++ {
		write(t, root, fmt.Sprintf(".agents/rules/r%03d.md", i), "x\n", 0o644)
	}
	changes, err := Collect(src, []string{".agents/rules"}, nil)
	if err != nil || len(changes) != 200 {
		t.Fatalf("changes = %d, err = %v", len(changes), err)
	}
	if err := Check(src, changes); err != nil {
		t.Fatalf("200 files: %v", err)
	}

	write(t, root, ".agents/rules/r200.md", "x\n", 0o644)
	changes, err = Collect(src, []string{".agents/rules"}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if err := Check(src, changes); err == nil || !strings.Contains(err.Error(), "201 files: Kaya takes at most 200 in one proposal") {
		t.Fatalf("201 files: %v", err)
	}
}

func TestCheck_aFileOverTheLimitIsNotReadAndIsRefusedWhenNewOrGrown(t *testing.T) {
	huge := strings.Repeat("x", maxFileBytes+10)
	files := append(baseFiles(), file{path: ".agents/rules/old-huge.md", content: huge})
	src := checkout(t, files)
	root := src.Checkout.Root

	// Shrinking a file that was already over the limit is waived.
	replace(t, root, ".agents/rules/old-huge.md", huge[:maxFileBytes+5])
	changes, err := Collect(src, []string{".agents/rules/old-huge.md"}, nil)
	if err != nil || len(changes) != 1 {
		t.Fatalf("changes = %q, err = %v", describe(changes), err)
	}
	if !changes[0].TooLarge() || changes[0].Content != nil {
		t.Fatalf("an oversized file was read: %+v", changes[0])
	}
	if err := Check(src, changes); err != nil {
		t.Fatalf("a shrinking oversized file: %v", err)
	}

	replace(t, root, ".agents/rules/old-huge.md", huge+"more")
	changes, _ = Collect(src, []string{".agents/rules/old-huge.md"}, nil)
	if err := Check(src, changes); err == nil || !strings.Contains(err.Error(), "a config file holds at most") {
		t.Fatalf("a growing oversized file: %v", err)
	}

	// Exactly 5 MiB is within the limit.
	write(t, root, ".agents/rules/exact.md", strings.Repeat("y", maxFileBytes), 0o644)
	changes, _ = Collect(src, []string{".agents/rules/exact.md"}, nil)
	if err := Check(src, changes); err != nil {
		t.Fatalf("5 MiB: %v", err)
	}
}

func TestCheck_listingAndLineWaiversForASkillAlreadyOverTheLimit(t *testing.T) {
	wordy := func(n int) string {
		return "---\nname: wordy\ndescription: " + strings.Repeat("w", n) + "\n---\nbody\n"
	}
	files := append(baseFiles(), file{path: ".agents/skills/wordy/SKILL.md", content: wordy(1600)})
	src := checkout(t, files)
	root := src.Checkout.Root

	replace(t, root, ".agents/skills/wordy/SKILL.md", wordy(1550))
	changes, _ := Collect(src, nil, nil)
	if err := Check(src, changes); err != nil {
		t.Fatalf("a shorter listing over the limit: %v", err)
	}
	replace(t, root, ".agents/skills/wordy/SKILL.md", wordy(1601))
	changes, _ = Collect(src, nil, nil)
	if err := Check(src, changes); err == nil || !strings.Contains(err.Error(), "1601 characters; at most 1536") {
		t.Fatalf("a longer listing: %v", err)
	}
	// 1536 is the limit itself.
	replace(t, root, ".agents/skills/wordy/SKILL.md", wordy(1536))
	changes, _ = Collect(src, nil, nil)
	if err := Check(src, changes); err != nil {
		t.Fatalf("1536 characters: %v", err)
	}
	// 1499 lines pass, 1500 are refused, for a new skill.
	body := func(lines int) string { return "---\nname: n\n---\n" + strings.Repeat("l\n", lines-3) }
	write(t, root, ".agents/skills/n1499/SKILL.md", body(1499), 0o644)
	changes, _ = Collect(src, []string{".agents/skills/n1499"}, nil)
	if err := Check(src, changes); err != nil {
		t.Fatalf("1499 lines: %v", err)
	}
}

func TestCompare_guardrailDenyRulesAreNeverProposed(t *testing.T) {
	files := append(baseFiles(), file{path: ".agents/rules/guardrails/no-secrets.md", content: "# No secrets\n"})
	src := checkout(t, files)
	root := src.Checkout.Root
	for i := range src.Release.Files {
		if src.Release.Files[i].Path == ".agents/rules/guardrails/no-secrets.md" {
			src.Release.Files[i].Deny = []string{"Read(./.env)", "Bash(rm:*)"}
		}
	}
	base := "{\n  \"permissions\": {\n    \"deny\": [\"Bash(rm:*)\"]\n  }\n}\n"
	merged, err := delivery.MergeDeny([]byte(base), src.Release.GuardrailDeny()...)
	if err != nil {
		t.Fatal(err)
	}

	// The settings as delivered, guardrail rules merged in, are no change.
	replace(t, root, ".claude/settings.json", string(merged))
	changes, err := Collect(src, nil, nil)
	if err != nil || len(changes) != 0 {
		t.Fatalf("the merged settings are a change: %q, %v", describe(changes), err)
	}

	// An unrelated edit is proposed without the guardrail rules, and keeps the
	// rule the delivered settings deny themselves although a guardrail denies it too.
	edited := strings.Replace(string(merged), "\"permissions\"", "\"model\": \"opus\",\n  \"permissions\"", 1)
	replace(t, root, ".claude/settings.json", edited)
	changes, err = Collect(src, nil, nil)
	if err != nil || len(changes) != 1 {
		t.Fatalf("changes = %q, err = %v", describe(changes), err)
	}
	sent := string(changes[0].Content)
	if strings.Contains(sent, ".env") || !strings.Contains(sent, "Bash(rm:*)") || !strings.Contains(sent, `"model": "opus"`) {
		t.Fatalf("proposed settings = %s", sent)
	}
	if err := Check(src, changes); err != nil {
		t.Fatalf("check: %v", err)
	}
}

func TestWithoutMergedDeny_stripsGuardrailRules(t *testing.T) {
	base := []byte("{\n  \"model\": \"sonnet\"\n}\n")
	guardrails := []string{"Read(./.env)", "Bash(rm:*)", "Read(./.env)"}
	merged, err := delivery.MergeDeny(base, guardrails...)
	if err != nil {
		t.Fatal(err)
	}

	got, err := withoutMergedDeny(merged, base, guardrails)
	if err != nil || string(got) != string(base) {
		t.Fatalf("unchanged settings = %q, %v", got, err)
	}

	edited := strings.Replace(string(merged), "sonnet", "opus", 1)
	got, err = withoutMergedDeny([]byte(edited), base, guardrails)
	if err != nil || string(got) != "{\n  \"model\": \"opus\"\n}\n" {
		t.Fatalf("edited settings = %q, %v", got, err)
	}

	// A rule the proposer adds next to the guardrail rules is proposed.
	added := []byte("{\n  \"permissions\": {\n    \"deny\": [\"Read(./.env)\", \"Bash(rm:*)\", \"Bash(ls:*)\"]\n  }\n}\n")
	got, err = withoutMergedDeny(added, base, guardrails)
	if err != nil || !strings.Contains(string(got), "Bash(ls:*)") || strings.Contains(string(got), ".env") || strings.Contains(string(got), "Bash(rm:*)") {
		t.Fatalf("a mixed deny list = %q, %v", got, err)
	}
}

func TestWithoutMergedDeny_keepsARuleTheReleaseAlreadyDeniesAndPassesOtherShapesThrough(t *testing.T) {
	base := []byte("{\n  \"permissions\": {\n    \"deny\": [\"Bash(rm:*)\"]\n  }\n}\n")
	guardrails := []string{"Bash(rm:*)", "Bash(curl:*)"}

	got, err := withoutMergedDeny(base, base, nil)
	if err != nil || string(got) != string(base) {
		t.Fatalf("no guardrails: %q, %v", got, err)
	}
	notObject := []byte("[1]\n")
	got, err = withoutMergedDeny(notObject, base, guardrails)
	if err != nil || string(got) != string(notObject) {
		t.Fatalf("not an object: %q, %v", got, err)
	}
	other := []byte("{\"permissions\": {\"deny\": \"Bash(curl:*)\"}}\n")
	got, err = withoutMergedDeny(other, base, guardrails)
	if err != nil || string(got) != string(other) {
		t.Fatalf("deny of another shape: %q, %v", got, err)
	}
	proposed := []byte("{\n  \"permissions\": {\n    \"deny\": [\"Bash(rm:*)\", \"Bash(curl:*)\", \"Bash(ls:*)\"]\n  }\n}\n")
	got, err = withoutMergedDeny(proposed, base, guardrails)
	if err != nil || !strings.Contains(string(got), "Bash(rm:*)") || !strings.Contains(string(got), "Bash(ls:*)") || strings.Contains(string(got), "curl") {
		t.Fatalf("a mixed deny list: %q, %v", got, err)
	}
}

func TestRequest_bodyLimitIsFourMiBAndLinkAndExecutableModesAreKept(t *testing.T) {
	changes := []Change{{Path: "a.sh", Kind: Modified, Mode: client.ModeExecutable, Content: []byte("#!/bin/sh\n")}}
	request, err := Request("r", 2, "A reason long enough", changes)
	if err != nil || request.Files[0].Mode != "EXECUTABLE" || request.Files[0].Encoding != client.EncodingUTF8 {
		t.Fatalf("request = %+v, %v", request, err)
	}

	// Raw bytes that are not UTF-8 grow by a third as base64 and are measured in the body.
	binary := make([]byte, MaxBodyBytes*3/4)
	for i := range binary {
		binary[i] = 0xff
	}
	_, err = Request("r", 2, "A reason long enough", []Change{{Path: "b", Kind: Added, Mode: client.ModeFile, Content: binary}})
	if err == nil || !strings.Contains(err.Error(), "smaller groups") {
		t.Fatalf("a base64 body over 8 MiB: %v", err)
	}
	small := make([]byte, MaxBodyBytes/2)
	for i := range small {
		small[i] = 0xff
	}
	if _, err := Request("r", 2, "A reason long enough", []Change{{Path: "b", Kind: Added, Mode: client.ModeFile, Content: small}}); err != nil {
		t.Fatalf("a body within 8 MiB: %v", err)
	}
}

func TestCheckReason_boundariesAndUTF16Units(t *testing.T) {
	cases := map[string]struct {
		reason string
		ok     bool
	}{
		"9 characters":      {strings.Repeat("a", 9), false},
		"10 characters":     {strings.Repeat("a", 10), true},
		"1000 characters":   {strings.Repeat("a", 1000), true},
		"1001 characters":   {strings.Repeat("a", 1001), false},
		"empty":             {"", false},
		"emoji count twice": {strings.Repeat("\U0001F600", 501), false},
		"500 emoji":         {strings.Repeat("\U0001F600", 500), true},
		"5 emoji":           {strings.Repeat("\U0001F600", 5), true},
		"4 emoji":           {strings.Repeat("\U0001F600", 4), false},
	}
	for name, c := range cases {
		if err := CheckReason(c.reason); (err == nil) != c.ok {
			t.Errorf("%s: err = %v", name, err)
		}
	}
}

func TestExplain_everyCodeNamesTheNextStep(t *testing.T) {
	cases := []struct {
		name  string
		err   *client.APIError
		wants []string
		wraps string
	}{
		{"two owners path", &client.APIError{Status: 409, Code: "KAY_CFG_003", ErrorValues: map[string]any{"violations": []any{map[string]any{"path": "a.md", "rule": "PATH_IN_TWO_SETS", "message": "two owners"}}}},
			[]string{"breaks these rules", "a.md: PATH_IN_TWO_SETS: two owners", "run oba kaya propose again"}, "KAY_CFG_003"},
		{"violations without a list", &client.APIError{Status: 422, Code: "KAY_CFG_001", ErrorValues: map[string]any{"detail": "x"}},
			[]string{"detail: \"x\""}, "KAY_CFG_001"},
		{"stale without conflicts", &client.APIError{Status: 409, Code: "KAY_CFG_002"},
			[]string{"since release 4", "oba kaya sync"}, "KAY_CFG_002"},
		{"stale left as delivered", &client.APIError{Status: 409, Code: "KAY_CFG_002", ErrorValues: map[string]any{"conflicts": []any{map[string]any{"path": "x.md", "currentChange": "ADDED"}}}},
			[]string{"x.md: you left it as delivered, Kaya added it"}, "KAY_CFG_002"},
		{"in review without a version", &client.APIError{Status: 409, Code: "KAY_CFG_015"},
			[]string{"already in review.", "one change of a pack at a time"}, "KAY_CFG_015"},
		{"unchanged", &client.APIError{Status: 409, Code: "KAY_CFG_016"}, []string{"nothing to propose"}, "KAY_CFG_016"},
		{"unknown release", &client.APIError{Status: 404, Code: "KAY_NF_001", ErrorValues: map[string]any{"repositoryId": "repo-1", "release": 4}},
			[]string{"knows no release 4", "oba kaya sync"}, "KAY_NF_001"},
		{"unknown repository", &client.APIError{Status: 404, Code: "KAY_NF_001", ErrorValues: map[string]any{"remoteUrl": "github.com/example/demo"}},
			[]string{"knows no repository github.com/example/demo", "refreshes the repository list", "`oba kaya status` names"}, "KAY_NF_001"},
		{"not found without values", &client.APIError{Status: 404, Code: "KAY_NF_001", Message: "gone"},
			[]string{"did not find what the proposal names: gone"}, "KAY_NF_001"},
		{"archived", &client.APIError{Status: 422, Code: "KAY_VAL_002"}, []string{"archived", "no config change"}, "KAY_VAL_002"},
		{"not a member", &client.APIError{Status: 403, Code: "GEN_SEC_002", ErrorValues: map[string]any{"organizationId": "org-1"}},
			[]string{"not a member of this organization", "ADMIN of the organization to add you"}, "GEN_SEC_002"},
		{"user disabled", &client.APIError{Status: 403, Code: "GEN_SEC_002", ErrorValues: map[string]any{"userStatus": "DISABLED"}},
			[]string{"user behind your machine token is not active (DISABLED)", "enable your user"}, "GEN_SEC_002"},
		{"membership disabled", &client.APIError{Status: 403, Code: "KAY_SEC_002", ErrorValues: map[string]any{"organizationId": "org-1", "memberStatus": "DISABLED"}},
			[]string{"membership of this organization is disabled (DISABLED)", "enable it"}, "KAY_SEC_002"},
		{"organization inactive", &client.APIError{Status: 403, Code: "KAY_SEC_002", ErrorValues: map[string]any{"organizationId": "org-1", "organizationStatus": "SUSPENDED"}},
			[]string{"organization is not active in Kaya (SUSPENDED)", "OWNER of the organization to reactivate it"}, "KAY_SEC_002"},
		{"email domain removed", &client.APIError{Status: 403, Code: "KAY_SEC_002", ErrorValues: map[string]any{"organizationId": "org-1", "emailDomain": "old.example"}},
			[]string{"no longer owns the email domain of your Kaya user (old.example)", "add the domain back"}, "KAY_SEC_002"},
		{"membership inactive without detail", &client.APIError{Status: 403, Code: "KAY_SEC_002"},
			[]string{"membership of this organization is not active."}, "KAY_SEC_002"},
		{"token by status", &client.APIError{Status: 401}, []string{"refused the machine token", "Register the machine again"}, ""},
		{"body too large", &client.APIError{Status: 413, Code: "KAY_VAL_012"}, []string{"larger than Kaya takes", "8 MiB", "smaller groups"}, "KAY_VAL_012"},
		{"body too large by status", &client.APIError{Status: 413}, []string{"8 MiB"}, ""},
		{"field invalid", &client.APIError{Status: 422, Code: "GEN_VAL_001", Message: "paths repeat", ErrorValues: map[string]any{"paths": []any{"a.md"}}},
			[]string{"refused a field of the proposal: paths repeat", "paths: [\"a.md\"]"}, "GEN_VAL_001"},
		{"bad request", &client.APIError{Status: 400, Code: "GEN_API_001", Message: "no remote"}, []string{"did not accept the request: no remote", "`oba kaya status` names for the checkout"}, "GEN_API_001"},
	}
	for _, c := range cases {
		err := Explain(c.err, 4)
		for _, want := range c.wants {
			if !strings.Contains(err.Error(), want) {
				t.Errorf("%s: missing %q in:\n%s", c.name, want, err)
			}
		}
		if c.wraps != "" && !client.IsCode(err, c.wraps) {
			t.Errorf("%s: the refusal hides Kaya's code %s", c.name, c.wraps)
		}
	}
	for name, apiErr := range map[string]*client.APIError{
		"unknown release": {Status: 404, Code: "KAY_NF_001", ErrorValues: map[string]any{"repositoryId": "repo-1", "release": 4}},
		"not a member":    {Status: 403, Code: "GEN_SEC_002", ErrorValues: map[string]any{"organizationId": "org-1"}},
	} {
		if got := Explain(apiErr, 4).Error(); strings.Contains(got, "no root project in Kaya") || strings.Contains(got, "disabled") {
			t.Errorf("%s is explained as another refusal: %s", name, got)
		}
	}
	unknown := &client.APIError{Status: 500, Code: "GEN_XXX_999", Message: "boom"}
	if Explain(unknown, 4) != error(unknown) {
		t.Fatal("an unknown code was rewritten")
	}
}
