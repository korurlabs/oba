package upload

import "testing"

func TestUnsignedDropsTheSignature(t *testing.T) {
	cases := map[string]string{
		"https://blob/x/abc-logo.png?sig=1&se=2": "https://blob/x/abc-logo.png",
		"https://blob/x/logo.png":                "https://blob/x/logo.png",
	}
	for in, want := range cases {
		if got := unsigned(in); got != want {
			t.Errorf("unsigned(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestRememberAssetOnlyHashedNames(t *testing.T) {
	byHash := map[string]string{}
	rememberAsset(byHash, "0123456789ab-logo.png", "https://blob/1")
	rememberAsset(byHash, "logo.png", "https://blob/2")
	rememberAsset(byHash, "0123456789AB-logo.png", "https://blob/3")
	if len(byHash) != 1 || byHash["0123456789ab"] != "https://blob/1" {
		t.Fatalf("byHash = %v: only names an upload gave are remembered", byHash)
	}
}
