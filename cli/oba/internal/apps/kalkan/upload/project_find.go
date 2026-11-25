package upload

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

// ProjectFileName is the committed project file, looked up in the current directory and then at
// the git root.
const ProjectFileName = "kalkan.yaml"

// Mapping is one upload entry: the directory Dir, relative to the project file, goes under the
// page path To. Lang is the locale of files without a language suffix; empty means the upload
// default.
type Mapping struct {
	Dir  string `yaml:"dir"`
	To   string `yaml:"to"`
	Lang string `yaml:"lang"`
}

// Project is a parsed kalkan.yaml. Dir is the directory that holds it; mapping directories are
// resolved against it, so an upload runs the same from any subdirectory of the repository.
// Organization is an id or a name, as --org takes.
type Project struct {
	Path         string    `yaml:"-"`
	Dir          string    `yaml:"-"`
	Organization string    `yaml:"organization"`
	Upload       []Mapping `yaml:"upload"`
}

// FindProject returns the kalkan.yaml of dir, else the one at the root of the git checkout that
// holds dir, else nil. A file that exists but does not parse, or names an unknown key, is an
// error, so a typo never silently uploads to the wrong organization.
func FindProject(dir string) (*Project, error) {
	abs, err := filepath.Abs(dir)
	if err != nil {
		return nil, fmt.Errorf("resolve %s: %w", dir, err)
	}
	candidates := []string{abs}
	if root := gitRoot(abs); root != "" && root != abs {
		candidates = append(candidates, root)
	}
	for _, candidate := range candidates {
		path := filepath.Join(candidate, ProjectFileName)
		data, err := os.ReadFile(path) // #nosec G304 -- the repository's own committed file
		if errors.Is(err, fs.ErrNotExist) {
			continue
		}
		if err != nil {
			return nil, fmt.Errorf("read %s: %w", path, err)
		}
		return parseProject(path, data)
	}
	return nil, nil
}

// gitRoot walks up from dir to the nearest directory holding .git (a directory in a clone, a file
// in a linked worktree); empty when dir is in no checkout.
func gitRoot(dir string) string {
	for current := dir; ; {
		if _, err := os.Lstat(filepath.Join(current, ".git")); err == nil {
			return current
		}
		parent := filepath.Dir(current)
		if parent == current {
			return ""
		}
		current = parent
	}
}

func parseProject(path string, data []byte) (*Project, error) {
	p := &Project{Path: path, Dir: filepath.Dir(path)}
	decoder := yaml.NewDecoder(bytes.NewReader(data))
	decoder.KnownFields(true)
	if err := decoder.Decode(p); err != nil && !errors.Is(err, io.EOF) {
		return nil, fmt.Errorf("%s: %w", path, err)
	}
	p.Organization = strings.TrimSpace(p.Organization)
	for i, m := range p.Upload {
		if strings.TrimSpace(m.Dir) == "" {
			return nil, fmt.Errorf("%s: upload entry %d has no dir", path, i+1)
		}
		if strings.TrimSpace(m.To) == "" {
			return nil, fmt.Errorf("%s: upload entry %d (%s) has no to", path, i+1, m.Dir)
		}
		switch strings.ToLower(strings.TrimSpace(m.Lang)) {
		case "", "tr", "en":
		default:
			return nil, fmt.Errorf("%s: upload entry %d (%s) has lang %q; use tr or en", path, i+1, m.Dir, m.Lang)
		}
	}
	return p, nil
}

// DirOf resolves a mapping directory against the directory of the project file.
func (p *Project) DirOf(m Mapping) string {
	if filepath.IsAbs(m.Dir) {
		return filepath.Clean(m.Dir)
	}
	return filepath.Join(p.Dir, m.Dir)
}
