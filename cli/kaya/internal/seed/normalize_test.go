package seed

import "testing"

func TestNormalizeRemote(t *testing.T) {
	cases := map[string]string{
		"https://github.com/Sensey/Demo.git":         "github.com/sensey/demo",
		"git@github.com:sensey/demo.git":             "github.com/sensey/demo",
		"ssh://git@github.com:22/sensey/demo":        "github.com/sensey/demo",
		"https://token@github.com/sensey/demo/":      "github.com/sensey/demo",
		"https://github.com/sensey":                  "",
		"not a remote":                               "",
		"git@github.com://sensey/demo":               "",
		"https://gitlab.example.com/group/sub/proj":  "gitlab.example.com/group/sub/proj",
		"  https://github.com/sensey/demo.git  ":     "github.com/sensey/demo",
		"http://github.com/sensey/demo?ref=x#anchor": "github.com/sensey/demo",
	}
	for remote, want := range cases {
		if got := NormalizeRemote(remote); got != want {
			t.Errorf("NormalizeRemote(%q) = %q, want %q", remote, got, want)
		}
	}
}
