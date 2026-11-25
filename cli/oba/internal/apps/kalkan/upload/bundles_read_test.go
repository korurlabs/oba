package upload

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestReadBundlesFromArgumentsKeepsTheProjectOrganization(t *testing.T) {
	dir := tree(t, map[string]string{
		"kalkan.yaml": "organization: Docs Org\nupload:\n  - dir: other\n    to: x\n",
		"d/a.md":      "# A\n",
	})
	bundles, organization, err := ReadBundles(BundlesInput{Dir: filepath.Join(dir, "d"), Target: "/r/", Lang: "tr", WorkDir: dir})
	if err != nil {
		t.Fatal(err)
	}
	if organization != "Docs Org" || len(bundles) != 1 || bundles[0].Target != "r" {
		t.Fatalf("organization %q bundles %+v", organization, bundles)
	}
	if got := titleOf(findPage(t, bundles[0], "r/a"), "TR"); got != "A" {
		t.Fatalf("title %q: --lang sets the locale of files without a suffix", got)
	}
}

func TestReadBundlesFromProjectFile(t *testing.T) {
	dir := tree(t, map[string]string{
		"kalkan.yaml": "upload:\n  - dir: docs\n    to: eng/docs\n  - dir: plans\n    to: eng/plans\n    lang: tr\n",
		"docs/a.md":   "# A\n",
		"plans/b.md":  "# B\n",
	})
	if err := os.Mkdir(filepath.Join(dir, "sub"), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(filepath.Join(dir, ".git"), 0o750); err != nil {
		t.Fatal(err)
	}
	bundles, organization, err := ReadBundles(BundlesInput{Lang: "en", WorkDir: filepath.Join(dir, "sub")})
	if err != nil {
		t.Fatal(err)
	}
	if organization != "" || len(bundles) != 2 {
		t.Fatalf("organization %q bundles %d", organization, len(bundles))
	}
	if titleOf(findPage(t, bundles[0], "eng/docs/a"), "EN") != "A" || titleOf(findPage(t, bundles[1], "eng/plans/b"), "TR") != "B" {
		t.Fatal("an entry's lang sets its locale, else --lang")
	}
}

func TestReadBundlesErrors(t *testing.T) {
	cases := map[string]struct {
		files map[string]string
		in    BundlesInput
		want  string
	}{
		"bad lang":       {nil, BundlesInput{Lang: "de"}, `unknown language "de"`},
		"bad target":     {map[string]string{"d/a.md": "x"}, BundlesInput{Dir: "d", Target: "a b", Lang: "en"}, "not a valid slug"},
		"no project":     {nil, BundlesInput{Lang: "en"}, "no kalkan.yaml in this directory or at the git root; run oba kalkan upload <dir> <target>"},
		"no entries":     {map[string]string{"kalkan.yaml": "organization: o\n"}, BundlesInput{Lang: "en"}, "lists no upload entries"},
		"entry lang":     {map[string]string{"kalkan.yaml": "upload:\n  - dir: d\n    to: r\n    lang: de\n", "d/a.md": "x"}, BundlesInput{Lang: "en"}, `has lang "de"; use tr or en`},
		"entry target":   {map[string]string{"kalkan.yaml": "upload:\n  - dir: d\n    to: bad slug\n", "d/a.md": "x"}, BundlesInput{Lang: "en"}, "not a valid slug"},
		"push key fails": {map[string]string{"kalkan.yaml": "push:\n  - dir: d\n    to: r\n"}, BundlesInput{Lang: "en"}, "push"},
		"missing dir":    {map[string]string{"kalkan.yaml": "upload:\n  - dir: nope\n    to: r\n"}, BundlesInput{Lang: "en"}, "cannot be read"},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			dir := tree(t, tc.files)
			in := tc.in
			in.WorkDir = dir
			if in.Dir != "" {
				in.Dir = filepath.Join(dir, in.Dir)
			}
			_, _, err := ReadBundles(in)
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("err = %v, want %q", err, tc.want)
			}
		})
	}
}
