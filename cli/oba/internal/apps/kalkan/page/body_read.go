package page

import (
	"fmt"
	"io"
	"os"
)

// readBody reads the Markdown body from a file, or from stdin when name is -.
func readBody(stdin io.Reader, name string) (string, error) {
	if name == "-" {
		data, err := io.ReadAll(stdin)
		if err != nil {
			return "", fmt.Errorf("read the body from stdin: %w", err)
		}
		return string(data), nil
	}
	data, err := os.ReadFile(name) // #nosec G304 -- the user names the file to upload
	if err != nil {
		return "", fmt.Errorf("read the body: %w", err)
	}
	return string(data), nil
}
