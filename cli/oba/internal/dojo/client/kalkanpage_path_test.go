package client

import (
	"strings"
	"testing"
)

func TestNormalizeKalkanPagePath(t *testing.T) {
	deep := strings.Repeat("a/", KalkanMaxPageDepth)
	tests := []struct {
		name, raw, want, wantErr string
	}{
		{"plain", "engineering/plans", "engineering/plans", ""},
		{"surrounding slashes and spaces", " /a/b/ ", "a/b", ""},
		{"ten deep", strings.TrimSuffix(deep, "/"), strings.TrimSuffix(deep, "/"), ""},
		{"empty", "", "", "the path is empty"},
		{"only slashes", "//", "", "the path is empty"},
		{"eleven deep", deep + "a", "", "is deeper than 10 pages"},
		{"bad slug", "a/-b", "", `"-b" in "a/-b" is not a valid slug`},
		{"empty segment", "a//b", "", `"" in "a//b" is not a valid slug`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := NormalizeKalkanPagePath(tt.raw)
			if tt.wantErr == "" {
				if err != nil || got != tt.want {
					t.Errorf("got %q, %v; want %q", got, err, tt.want)
				}
				return
			}
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Errorf("err = %v, want it to contain %q", err, tt.wantErr)
			}
		})
	}
}

func TestValidKalkanSlug(t *testing.T) {
	for _, slug := range []string{"a", "0", "v1.2_final-draft", "A" + strings.Repeat("b", 99)} {
		if !ValidKalkanSlug(slug) {
			t.Errorf("ValidKalkanSlug(%q) = false", slug)
		}
	}
	for _, slug := range []string{"", ".a", "_a", "-a", "a b", "a/b", "ş", "A" + strings.Repeat("b", 100)} {
		if ValidKalkanSlug(slug) {
			t.Errorf("ValidKalkanSlug(%q) = true", slug)
		}
	}
}
