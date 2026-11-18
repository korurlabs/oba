package seed

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// symlinkPrefix marks a link in the files map of .agents/.managed.json.
const symlinkPrefix = "symlink:"

// Managed is .agents/.managed.json of a consumer repository: the managed paths with their sha256
// (or "symlink:<target>") and the packs the repository takes, in order.
type Managed struct {
	Files map[string]string `json:"files"`
	Packs []string          `json:"packs"`
}

// ErrNotManaged means the repository has no .agents/.managed.json.
var ErrNotManaged = errors.New("no .agents/.managed.json")

// ReadManaged reads .agents/.managed.json of the checkout at root.
func ReadManaged(root string) (Managed, error) {
	path := filepath.Join(root, ".agents", ".managed.json")
	data, err := os.ReadFile(path) // #nosec G304 -- a fixed file of the checkout the seed was pointed at
	if errors.Is(err, os.ErrNotExist) {
		return Managed{}, ErrNotManaged
	}
	if err != nil {
		return Managed{}, fmt.Errorf("read %s: %w", path, err)
	}
	var managed Managed
	if err := json.Unmarshal(data, &managed); err != nil {
		return Managed{}, fmt.Errorf("parse %s: %w", path, err)
	}
	return managed, nil
}

// Pack is one pack of korurlabs/agents with its files at their consumer paths.
type Pack struct {
	Name  string
	Files map[string]Entry
}

// AgentsScan is what a clone of korurlabs/agents holds.
type AgentsScan struct {
	// Packs by name.
	Packs map[string]Pack
	// Deprecated are the packs Kaya no longer imports, by name; they are read only to tell which
	// managed files of a repository they delivered.
	Deprecated map[string]Pack
	// NotImported are the clone's paths that no consumer receives, prefixed "agents:".
	NotImported []client.NotImported
}

// agentsSource prefixes the clone's paths in the report.
const agentsSource = "agents"

// ScanAgents reads every pack under packs/ of the clone at root, mapped to the consumer paths
// the sync writes.
func ScanAgents(root string) (AgentsScan, error) {
	scan := AgentsScan{Packs: map[string]Pack{}, Deprecated: map[string]Pack{}}
	top, err := os.ReadDir(root)
	if err != nil {
		return AgentsScan{}, fmt.Errorf("read the agents clone %s: %w", root, err)
	}
	for _, entry := range top {
		switch {
		case entry.Name() == ".git":
		case entry.Name() == "packs" && entry.IsDir():
		case entry.IsDir():
			scan.notImported(entry.Name()+"/", ReasonAgentsRepo)
		default:
			scan.notImported(entry.Name(), ReasonAgentsRepo)
		}
	}
	packsDir := filepath.Join(root, "packs")
	packDirs, err := os.ReadDir(packsDir)
	if err != nil {
		return AgentsScan{}, fmt.Errorf("read %s: %w", packsDir, err)
	}
	for _, packDir := range packDirs {
		if !packDir.IsDir() {
			scan.notImported("packs/"+packDir.Name(), ReasonPackExtra)
			continue
		}
		name := packDir.Name()
		if !setName.MatchString(name) {
			return AgentsScan{}, fmt.Errorf("pack %q is not a valid set name (%s)", name, setName)
		}
		if deprecatedPacks[name] {
			// Its own extras are not worth reporting: the whole pack is.
			var discarded AgentsScan
			pack, err := discarded.readPack(filepath.Join(packsDir, name), name)
			if err != nil {
				return AgentsScan{}, err
			}
			scan.Deprecated[name] = pack
			scan.notImported("packs/"+name+"/", ReasonDeprecatedPack)
			continue
		}
		pack, err := scan.readPack(filepath.Join(packsDir, name), name)
		if err != nil {
			return AgentsScan{}, err
		}
		scan.Packs[name] = pack
	}
	sort.Slice(scan.NotImported, func(i, j int) bool { return scan.NotImported[i].Path < scan.NotImported[j].Path })
	return scan, nil
}

func (s *AgentsScan) notImported(path, reason string) {
	s.NotImported = append(s.NotImported, client.NotImported{Path: prefixed(agentsSource, path), Reason: reason})
}

func (s *AgentsScan) readPack(dir, name string) (Pack, error) {
	pack := Pack{Name: name, Files: map[string]Entry{}}
	err := filepath.WalkDir(dir, func(abs string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if d.IsDir() {
			return nil
		}
		rel, err := filepath.Rel(dir, abs)
		if err != nil {
			return err
		}
		rel = filepath.ToSlash(rel)
		sourcePath := "packs/" + name + "/" + rel
		consumer, skill := consumerPath(rel)
		if consumer == "" || !IsAllowedPath(consumer) {
			s.notImported(sourcePath, ReasonPackExtra)
			return nil
		}
		entry, ok, err := readEntry(abs, consumer)
		if err != nil {
			return err
		}
		if !ok {
			s.notImported(sourcePath, ReasonNotRegular)
			return nil
		}
		if entry.Mode != client.ModeSymlink && entry.SHA256 == "" {
			s.notImported(sourcePath, ReasonTooLarge)
			return nil
		}
		pack.Files[consumer] = entry
		if skill != "" {
			link := ".claude/skills/" + skill
			pack.Files[link] = Entry{Path: link, Mode: client.ModeSymlink, Target: "../../.agents/skills/" + skill}
		}
		return nil
	})
	if err != nil {
		return Pack{}, fmt.Errorf("walk pack %s: %w", name, err)
	}
	return pack, nil
}

// consumerPath maps a path inside a pack to the consumer path, and names the skill whose link
// under .claude/skills/ the sync adds. An empty path means the sync does not deliver it.
func consumerPath(rel string) (path, skill string) {
	segments := strings.Split(rel, "/")
	for i, segment := range segments {
		last := i == len(segments)-1
		if (last && ignoredPackFiles[segment]) || (!last && ignoredPackDirs[segment]) {
			return "", ""
		}
	}
	dir, rest, found := strings.Cut(rel, "/")
	if !found || rest == "" {
		return "", ""
	}
	target, ok := packTargets[dir]
	if !ok {
		return "", ""
	}
	switch target.layout {
	case layoutTrees:
		name, inner, found := strings.Cut(rest, "/")
		if !found || inner == "" {
			return "", ""
		}
		if dir == "skills" {
			skill = name
		}
	case layoutHooks:
		if strings.Contains(rest, "/") || hookFilesNotSynced[rest] {
			return "", ""
		}
	case layoutMarkdown:
		if strings.Contains(rest, "/") || !strings.HasSuffix(rest, ".md") {
			return "", ""
		}
	case layoutFiles:
		if strings.Contains(rest, "/") {
			return "", ""
		}
	}
	return target.dir + rest, skill
}

// packOf names the first of packs that holds path, or "".
func (s AgentsScan) packOf(path string, packs []string) string {
	for _, name := range packs {
		if pack, ok := s.Packs[name]; ok {
			if _, held := pack.Files[path]; held {
				return name
			}
		}
	}
	return ""
}

// deprecatedOf names the first of packs that is deprecated and holds path, or "".
func (s AgentsScan) deprecatedOf(path string, packs []string) string {
	for _, name := range packs {
		if pack, ok := s.Deprecated[name]; ok {
			if _, held := pack.Files[path]; held {
				return name
			}
		}
	}
	return ""
}

// delivers reports whether some pack of the clone delivers entry exactly: the same path with
// the same mode, hash and link target.
func (s AgentsScan) delivers(entry Entry) bool {
	for _, pack := range s.Packs {
		if held, ok := pack.Files[entry.Path]; ok && held.Mode == entry.Mode && held.SHA256 == entry.SHA256 && held.Target == entry.Target {
			return true
		}
	}
	return false
}
