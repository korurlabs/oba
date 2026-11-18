package seed

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// Mount is a pack mounted in a folder of a consumer repository (--pack <name>=<folder>).
type Mount struct {
	// Name is the pack's name, also the prefix of its namespace paths.
	Name string
	// Root is the folder relative to the repository root, with '/' separators.
	Root string
}

// ParseMount reads a --pack value, <name>=<folder>.
func ParseMount(value string) (Mount, error) {
	name, root, ok := strings.Cut(value, "=")
	if !ok {
		return Mount{}, fmt.Errorf("--pack %q: give <name>=<folder>, as kagi=projects/kagi", value)
	}
	mount := Mount{Name: strings.TrimSpace(name), Root: strings.TrimSuffix(strings.TrimSpace(filepath.ToSlash(root)), "/")}
	if !setName.MatchString(mount.Name) {
		return Mount{}, fmt.Errorf("--pack %q: the name must be 1 to %d of a-z, 0-9 and '-'", value, maxPackName)
	}
	if !mountRoot.MatchString(mount.Root) {
		return Mount{}, fmt.Errorf("--pack %q: the folder must be 1 to 4 folders of the repository, each of "+
			"A-Z, a-z, 0-9, '_', '.' and '-' and not starting with '.'", value)
	}
	if first, _, _ := strings.Cut(mount.Root, "/"); reservedRoots[first] {
		return Mount{}, fmt.Errorf("--pack %q: a pack cannot be mounted in %s/", value, first)
	}
	return mount, nil
}

// ParsePackParent reads a --pack-parent value, <child>=<parent>.
func ParsePackParent(value string) (child, parent string, err error) {
	child, parent, ok := strings.Cut(value, "=")
	child, parent = strings.TrimSpace(child), strings.TrimSpace(parent)
	switch {
	case !ok:
		return "", "", fmt.Errorf("--pack-parent %q: give <child>=<parent>, as kagi=village", value)
	case !setName.MatchString(child) || !setName.MatchString(parent):
		return "", "", fmt.Errorf("--pack-parent %q: both names must be 1 to %d of a-z, 0-9 and '-'", value, maxPackName)
	case child == parent:
		return "", "", fmt.Errorf("--pack-parent %q: a pack cannot be its own parent", value)
	}
	return child, parent, nil
}

// ParseRepoPack reads a --repo-pack value, [<repository>=]<pack>. An empty repository applies the
// pack to every repository the seed names.
func ParseRepoPack(value string) (repository, pack string, err error) {
	repository, pack, named := strings.Cut(value, "=")
	if !named {
		repository, pack = "", repository
	}
	repository, pack = strings.TrimSpace(repository), strings.TrimSpace(pack)
	if named && repository == "" {
		return "", "", fmt.Errorf("--repo-pack %q: give <pack> or <repository>=<pack>", value)
	}
	if !setName.MatchString(pack) {
		return "", "", fmt.Errorf("--repo-pack %q: the pack name must be 1 to %d of a-z, 0-9 and '-'", value, maxPackName)
	}
	return repository, pack, nil
}

// checkMounts refuses a pack mounted twice, overlapping folders, and a mount that is the root
// pack, a deprecated pack or a repository's family pack.
func (b *builder) checkMounts() error {
	names := map[string]bool{}
	for i, mount := range b.opts.Mounts {
		switch {
		case names[mount.Name]:
			return fmt.Errorf("the pack %s is mounted twice; a seed mounts a pack in one folder", mount.Name)
		case mount.Name == rootPackName:
			return fmt.Errorf("the pack %s is the root of the pack tree and cannot be mounted in a folder", mount.Name)
		case deprecatedPacks[mount.Name]:
			return fmt.Errorf("the pack %s is deprecated and is not imported", mount.Name)
		}
		for _, repo := range b.repos {
			if repo.family == mount.Name {
				return fmt.Errorf("the pack %s holds the own files of %s at its root, so it cannot be mounted in %s",
					mount.Name, repo.Name, mount.Root)
			}
		}
		names[mount.Name] = true
		for _, other := range b.opts.Mounts[:i] {
			if mount.Root == other.Root || strings.HasPrefix(mount.Root, other.Root+"/") || strings.HasPrefix(other.Root, mount.Root+"/") {
				return fmt.Errorf("the folders of the packs %s (%s) and %s (%s) overlap; nested mounts are not supported",
					other.Name, other.Root, mount.Name, mount.Root)
			}
		}
	}
	return nil
}

// repositoryOf is the one repository whose checkout holds the mount folder.
func (b *builder) repositoryOf(mount Mount) (*repoBuild, error) {
	var found *repoBuild
	for _, repo := range b.repos {
		info, err := os.Lstat(filepath.Join(repo.Root, filepath.FromSlash(mount.Root)))
		switch {
		case errors.Is(err, os.ErrNotExist):
			continue
		case err != nil:
			return nil, fmt.Errorf("read the folder of the pack %s: %w", mount.Name, err)
		case !info.IsDir():
			return nil, fmt.Errorf("%s of %s is not a directory, so the pack %s cannot be mounted there", mount.Root, repo.Root, mount.Name)
		case found != nil:
			return nil, fmt.Errorf("both %s and %s hold %s; seed the mounted pack %s with only its repository",
				found.Root, repo.Root, mount.Root, mount.Name)
		}
		found = repo
	}
	if found == nil {
		return nil, fmt.Errorf("no repository named with --repo holds %s, the folder of the pack %s", mount.Root, mount.Name)
	}
	if found.CutOver {
		return nil, fmt.Errorf("%s was cut over to Kaya, so the seed keeps its pack list; mount the pack %s in the Kaya web app",
			found.Root, mount.Name)
	}
	return found, nil
}

// addMount gives the mounted pack the files of its namespace out of the repository's own files,
// and its <folder>/AGENTS.md stored as AGENTS.md: Kaya delivers a mounted pack's AGENTS.md in the
// mount folder, so one pack can be mounted in any folder. The agent files nested below the folder
// are reported as not imported.
func (b *builder) addMount(repo *repoBuild, mount Mount) (map[string]Entry, error) {
	files := map[string]Entry{}
	for path, entry := range repo.own {
		if inPackNamespace(path, mount.Name) {
			files[path] = entry
			delete(repo.own, path)
		}
	}
	scan, err := scanMountFolder(repo.Root, mount.Root)
	if err != nil {
		return nil, err
	}
	for _, item := range scan.NotImported {
		b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(repo.Name, item.Path), Reason: item.Reason})
	}
	for path, entry := range scan.Delivered {
		// The release comparison expects every delivered path on disk, the oversized one too.
		repo.Disk[path] = entry
		if entry.Mode != client.ModeSymlink && entry.SHA256 == "" {
			b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(repo.Name, path), Reason: ReasonTooLarge})
			continue
		}
		stored := entry
		stored.Path = agentsFile
		files[agentsFile] = stored
	}
	return files, nil
}

// agentsFile is the path a pack stores its AGENTS.md at, wherever it is delivered.
const agentsFile = "AGENTS.md"
