package page

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestBuildUpsertFromFile(t *testing.T) {
	file := filepath.Join(t.TempDir(), "plans.md")
	if err := os.WriteFile(file, []byte("# Plans\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	compliance := false
	up, err := BuildUpsert(PutInput{Path: "/engineering/plans/", Lang: "TR", Title: "Planlar", File: file,
		Compliance: &compliance, Force: true}, strings.NewReader("unread"))
	if err != nil {
		t.Fatal(err)
	}
	if up.Path != "engineering/plans" || up.Compliance == nil || *up.Compliance || !up.Force || up.Position != nil ||
		len(up.Translations) != 1 ||
		up.Translations[0] != (client.KalkanTranslationInput{Locale: "TR", Title: "Planlar", Body: "# Plans\n"}) {
		t.Errorf("up = %+v", up)
	}
}

func TestBuildUpsertFromStdin(t *testing.T) {
	up, err := BuildUpsert(PutInput{Path: "a", Lang: "en", Title: "A", File: "-"}, strings.NewReader("body"))
	if err != nil {
		t.Fatal(err)
	}
	if up.Compliance != nil || up.Translations[0].Body != "body" || up.Translations[0].Locale != "EN" {
		t.Errorf("up = %+v", up)
	}
}

func TestBuildUpsertRefusesBadInput(t *testing.T) {
	missing := filepath.Join(t.TempDir(), "missing.md")
	cases := []struct {
		name string
		in   PutInput
		want string
	}{
		{"bad path", PutInput{Path: "a/-b", Lang: "en", Title: "T", File: "-"}, "is not a valid slug"},
		{"bad language", PutInput{Path: "a", Lang: "de", Title: "T", File: "-"}, `unknown language "de"`},
		{"no title", PutInput{Path: "a", Lang: "en", Title: " ", File: "-"}, "--title is required"},
		{"no file", PutInput{Path: "a", Lang: "en", Title: "T"}, "--file is required"},
		{"missing file", PutInput{Path: "a", Lang: "en", Title: "T", File: missing}, "read the body"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := BuildUpsert(tc.in, strings.NewReader(""))
			if err == nil || !strings.Contains(err.Error(), tc.want) {
				t.Errorf("err = %v, want %q", err, tc.want)
			}
		})
	}
}
