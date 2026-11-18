package pr

import (
	"strings"
	"testing"
)

func TestParsePullRef(t *testing.T) {
	cases := []struct {
		name, arg, repo string
		want            PullRef
		wantErr         string
	}{
		{"url", "https://github.com/senseylabs/village/pull/688", "", PullRef{"senseylabs/village", 688}, ""},
		{"url with files path", "https://github.com/senseylabs/village/pull/688/files", "", PullRef{"senseylabs/village", 688}, ""},
		{"url with query and fragment", "https://github.com/senseylabs/village/pull/688?diff=split#discussion_r1", "", PullRef{"senseylabs/village", 688}, ""},
		{"url without scheme", "github.com/senseylabs/village/pull/7", "", PullRef{"senseylabs/village", 7}, ""},
		{"www host and http", "http://www.GitHub.com/senseylabs/village/pull/7", "", PullRef{"senseylabs/village", 7}, ""},
		{"owner/name#N", "senseylabs/village#688", "", PullRef{"senseylabs/village", 688}, ""},
		{"name#N", "village#688", "", PullRef{"village", 688}, ""},
		{"N with --repo", "688", "village", PullRef{"village", 688}, ""},
		{"#N with --repo", "#688", "senseylabs/village", PullRef{"senseylabs/village", 688}, ""},
		{"padded", "  #688 ", " village ", PullRef{"village", 688}, ""},
		{"bare number without repo", "688", "", PullRef{}, "names no repository"},
		{"other host", "https://gitlab.com/senseylabs/village/pull/688", "", PullRef{}, "invalid pull request URL"},
		{"issue url", "https://github.com/senseylabs/village/issues/688", "", PullRef{}, "invalid pull request URL"},
		{"zero", "village#0", "", PullRef{}, "invalid pull request"},
		{"text", "soon", "village", PullRef{}, "invalid pull request"},
		{"repo in argument differs from --repo", "village#688", "agents", PullRef{}, "but --repo names agents"},
		{"name#N with its full name as --repo", "village#688", "senseylabs/village", PullRef{"senseylabs/village", 688}, ""},
		{"owner/name#N with its short name as --repo", "senseylabs/village#688", "village", PullRef{"senseylabs/village", 688}, ""},
		{"short name is the owner, not the name", "senseylabs#688", "senseylabs/village", PullRef{}, "but --repo names senseylabs/village"},
		{"same name, other owner", "other/village#688", "senseylabs/village", PullRef{}, "but --repo names senseylabs/village"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, err := ParsePullRef(tc.arg, tc.repo)
			if tc.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tc.wantErr) {
					t.Fatalf("error = %v, want one containing %q", err, tc.wantErr)
				}
				return
			}
			if err != nil || got != tc.want {
				t.Fatalf("got %+v, %v; want %+v", got, err, tc.want)
			}
		})
	}
}
