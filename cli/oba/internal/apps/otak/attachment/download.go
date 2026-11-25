package attachment

import (
	"fmt"
	"path"
	"path/filepath"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Download writes attachment attachmentID of the target into dir under its own file name and
// returns the path written. The attachment is looked up in the target's list first, so that a
// mistyped id never fetches a file of another parent, and its file is read from the signed URL
// the list returned. An existing file is not replaced unless overwrite is true.
func Download(c *client.Client, t Target, attachmentID, dir string, overwrite bool) (string, error) {
	found, err := Find(c, t, attachmentID)
	if err != nil {
		return "", err
	}
	if found.URL == "" {
		return "", fmt.Errorf("attachment %s of %s has no download address", found.ID, t.Label)
	}
	data, err := client.Download(found.URL)
	if err != nil {
		return "", fmt.Errorf("failed to download attachment %s of %s: %w", found.ID, t.Label, err)
	}
	target := filepath.Join(dir, localName(found.FileName, found.ID))
	if err := docfile.WriteFile(target, data, overwrite); err != nil {
		return "", err
	}
	return target, nil
}

// localName is the last element of the stored file name, so that a name sent by the API never
// writes outside the chosen directory; fallback is used when nothing usable is left.
func localName(fileName, fallback string) string {
	name := guardrail.OneLine(strings.ReplaceAll(fileName, `\`, "/"))
	name = path.Base(path.Clean("/" + name))
	if name == "/" || name == "." || name == ".." || strings.TrimSpace(name) == "" {
		return fallback
	}
	return name
}
