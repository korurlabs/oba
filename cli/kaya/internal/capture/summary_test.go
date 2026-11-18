package capture

import "testing"

func TestIsKazanCommand(t *testing.T) {
	cases := map[string]bool{
		"oba kazan ticket get KAI-45 --json":       true,
		"oba --org acme kazan ticket move KAI-7 X": true,
		"oba otak relation list":                   false,
		"kazan ticket get KAI-45":                  false,
		"oba":                                      false,
		"":                                         false,
	}
	for command, want := range cases {
		if got := isKazanCommand(pyFields(command)); got != want {
			t.Errorf("isKazanCommand(%q) = %v, want %v", command, got, want)
		}
	}
}
