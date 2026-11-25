package attachment

import (
	"strings"
	"testing"
)

func TestReadFilesNeedsAPath(t *testing.T) {
	for _, paths := range [][]string{nil, {" "}} {
		if _, err := ReadFiles(paths); err == nil || !strings.Contains(err.Error(), "pass --file") {
			t.Fatalf("%q: err = %v", paths, err)
		}
	}
}
