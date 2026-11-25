package upload

import (
	"path/filepath"
	"strings"
	"testing"
)

func TestHashFileIsTheHexSHA256OfTheContent(t *testing.T) {
	dir := tree(t, map[string]string{"a.txt": "abc"})
	got, err := hashFile(filepath.Join(dir, "a.txt"))
	if err != nil {
		t.Fatal(err)
	}
	if want := "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"; got != want {
		t.Fatalf("hashFile = %s, want %s", got, want)
	}
}

func TestHashFileNamesTheMissingAsset(t *testing.T) {
	missing := filepath.Join(t.TempDir(), "gone.png")
	if _, err := hashFile(missing); err == nil || !strings.Contains(err.Error(), "read the asset "+missing) {
		t.Fatalf("hashFile = %v", err)
	}
}
