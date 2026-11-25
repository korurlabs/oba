package upload

import (
	"errors"
	"io"
	"strings"

	"gopkg.in/yaml.v3"
)

// frontMatter is the optional YAML block at the top of a file. Other keys are ignored, since
// repo docs may carry their own.
type frontMatter struct {
	Title      *string `yaml:"title"`
	Position   *int    `yaml:"position"`
	Compliance *bool   `yaml:"compliance"`
}

// splitFrontMatter takes a leading YAML block between two --- lines off the content.
func splitFrontMatter(content string) (frontMatter, string, error) {
	var fm frontMatter
	normalized := strings.TrimPrefix(content, "\ufeff")
	if !strings.HasPrefix(normalized, "---\n") && !strings.HasPrefix(normalized, "---\r\n") {
		return fm, content, nil
	}
	rest := normalized[strings.Index(normalized, "\n")+1:]
	offset := 0
	for {
		end := strings.Index(rest[offset:], "\n")
		line := rest[offset:]
		if end >= 0 {
			line = rest[offset : offset+end]
		}
		if trimmed := strings.TrimRight(line, "\r"); trimmed == "---" || trimmed == "..." {
			block := rest[:offset]
			body := ""
			if end >= 0 {
				body = rest[offset+end+1:]
			}
			decoder := yaml.NewDecoder(strings.NewReader(block))
			if err := decoder.Decode(&fm); err != nil && !errors.Is(err, io.EOF) {
				return fm, "", err
			}
			return fm, body, nil
		}
		if end < 0 {
			return fm, "", errors.New("no closing --- line")
		}
		offset += end + 1
	}
}
