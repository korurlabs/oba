package seed

import (
	"errors"
	"fmt"
	"net/url"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// Limits of a seed request the server enforces; the CLI checks them first to name the cause.
const (
	maxSets          = 200
	maxFilesPerSet   = 5000
	maxReportedPaths = 5000
	maxSourceRef     = 100
	// maxFilesPerRun and maxContentBytes bound one run as a whole (413 KAY_CFG_011).
	maxFilesPerRun  = 20000
	maxContentBytes = 64 << 20
)

// Options are the inputs of a seed.
type Options struct {
	// AgentsDir is a clone of korurlabs/agents; empty seeds the repositories' own packs only and
	// keeps the packs the server holds.
	AgentsDir string
	// RepoDirs are checkouts of consumer repositories.
	RepoDirs []string
	// SourceRef overrides the git commit recorded for the run.
	SourceRef string
	// AfterCutover allows seeding a repository that no longer has .agents/.managed.json.
	AfterCutover bool
	// Mounts are packs mounted in folders of the repositories (--pack <name>=<folder>).
	Mounts []Mount
	// Parents name the parent of a seeded pack by its name (--pack-parent <child>=<parent>); a
	// pack not named here is a child of org, a mounted pack outside the clone a child of its
	// repository's family pack, and org is the root.
	Parents map[string]string
	// RepoPacks name the family pack of a repository by the repository's name, or of every
	// repository under the key "" (--repo-pack [<repository>=]<pack>).
	RepoPacks map[string]string
	// Resolver replaces ssh host aliases in the remotes; nil uses `ssh -G`.
	Resolver *RemoteResolver
}

// Repository is one consumer repository of the plan, with what its disk holds at delivered paths.
type Repository struct {
	// Name is the repository's name from its remote.
	Name      string
	Root      string
	RemoteURL string
	// FamilyPack is the pack the repository uses at its root that holds its own files.
	FamilyPack string
	// Disk is every delivered path on disk, managed files included, for the release comparison.
	Disk map[string]Entry
	// CutOver is true when the repository has no .agents/.managed.json, so its disk is Kaya's output.
	CutOver bool
}

// Plan is a seed ready to send.
type Plan struct {
	Request      client.SeedRequest
	Repositories []Repository
	// Blobs holds the content of every FILE and EXECUTABLE entry, by sha256.
	Blobs map[string]Entry
	// Warnings are findings the request cannot carry, such as a managed link that drifted.
	Warnings []string
}

// ErrCutOver means a repository has been cut over to Kaya, so its disk is Kaya's output now.
var ErrCutOver = errors.New("cut over")

// repoBuild is a repository while the plan is built: its own files still to place, and the packs
// it uses in .agents/.managed.json order.
type repoBuild struct {
	Repository
	managed Managed
	own     map[string]Entry
	family  string
	mounts  []Mount
}

// builder collects the sets by pack name before they are written into the request.
type builder struct {
	plan   *Plan
	opts   Options
	agents AgentsScan
	repos  []*repoBuild
	sets   map[string]map[string]Entry
	order  []string
	// parents are the default parents of sets that are not a child of org.
	parents map[string]string
}

// Build reads the agents clone and the repositories and builds the seed request: the packs of the
// clone, each repository's own files folded into its family pack, the mounted packs, and each
// repository's pack list.
func Build(opts Options) (Plan, error) {
	if len(opts.RepoDirs) == 0 {
		return Plan{}, fmt.Errorf("name at least one repository with --repo")
	}
	plan := Plan{Blobs: map[string]Entry{}}
	b := &builder{plan: &plan, opts: opts, sets: map[string]map[string]Entry{}, parents: map[string]string{}}

	if opts.AgentsDir != "" {
		scan, err := ScanAgents(opts.AgentsDir)
		if err != nil {
			return Plan{}, err
		}
		b.agents = scan
		for _, name := range sortedPackNames(scan.Packs) {
			b.addFiles(name, scan.Packs[name].Files)
		}
		plan.Request.NotImported = append(plan.Request.NotImported, scan.NotImported...)
	}

	names := map[string]string{}
	for _, dir := range opts.RepoDirs {
		root, err := filepath.Abs(dir)
		if err != nil {
			return Plan{}, fmt.Errorf("resolve %s: %w", dir, err)
		}
		if err := b.addRepo(root, names); err != nil {
			return Plan{}, err
		}
	}
	if err := b.checkRepoPacks(); err != nil {
		return Plan{}, err
	}
	if err := b.checkMounts(); err != nil {
		return Plan{}, err
	}
	mounted := map[string]map[string]Entry{}
	for _, mount := range opts.Mounts {
		repo, err := b.repositoryOf(mount)
		if err != nil {
			return Plan{}, err
		}
		if err := b.needsAgents(repo, mount.Name); err != nil {
			return Plan{}, err
		}
		files, err := b.addMount(repo, mount)
		if err != nil {
			return Plan{}, err
		}
		repo.mounts = append(repo.mounts, mount)
		mounted[mount.Name] = files
		if _, inClone := b.agents.Packs[mount.Name]; !inClone {
			b.parents[mount.Name] = repo.family
		}
	}
	if err := b.foldOwnFiles(); err != nil {
		return Plan{}, err
	}
	for _, mount := range opts.Mounts {
		b.addFiles(mount.Name, mounted[mount.Name])
	}
	if err := b.writeSets(); err != nil {
		return Plan{}, err
	}
	b.writeRepositories()

	ref, err := sourceRef(opts)
	if err != nil {
		return Plan{}, err
	}
	plan.Request.SourceRef = ref
	return plan, plan.checkLimits()
}

func (b *builder) addRepo(root string, names map[string]string) error {
	managed, err := ReadManaged(root)
	cutOver := errors.Is(err, ErrNotManaged)
	switch {
	case cutOver && !b.opts.AfterCutover:
		return fmt.Errorf("%s has no .agents/.managed.json, so it was cut over to Kaya and its files are Kaya's output; "+
			"pass --after-cutover to seed it anyway: %w", root, ErrCutOver)
	case cutOver && b.opts.AgentsDir == "":
		// Only the clone tells which disk files a pack delivers; without it every file would land
		// in the family pack, which would then override org and repeat the mounted packs' paths.
		return fmt.Errorf("%s was cut over to Kaya, so its disk holds the files of every pack; pass --agents so that "+
			"the files a pack of korurlabs/agents delivers are told apart from the repository's own: %w", root, ErrCutOver)
	case cutOver:
	case err != nil:
		return err
	}

	rawRemote, err := gitOutput(root, "remote", "get-url", "origin")
	if err != nil {
		return fmt.Errorf("read the origin remote of %s: %w", root, err)
	}
	resolver := b.opts.Resolver
	if resolver == nil {
		resolver = defaultResolver
	}
	remote, warning := resolver.Resolve(CleanRemote(rawRemote))
	if warning != "" {
		b.plan.Warnings = append(b.plan.Warnings, warning)
	}
	name := RepositoryName(remote)
	if !setName.MatchString(name) {
		return fmt.Errorf("the remote %s of %s gives no valid repository name", remote, root)
	}
	if other, taken := names[name]; taken {
		return fmt.Errorf("%s and %s both give the repository name %q", other, root, name)
	}
	names[name] = root

	scan, err := ScanRepo(root)
	if err != nil {
		return err
	}
	for _, item := range scan.NotImported {
		b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(name, item.Path), Reason: item.Reason})
	}
	b.checkManaged(name, scan, managed)

	own := map[string]Entry{}
	for path, entry := range scan.Delivered {
		if _, isManaged := managed.Files[path]; isManaged {
			continue
		}
		// A cut-over disk holds every pack's files: only what no pack of the clone delivers is
		// the repository's own.
		if cutOver && b.agents.delivers(entry) {
			continue
		}
		if entry.Mode != client.ModeSymlink && entry.SHA256 == "" {
			b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(name, path), Reason: ReasonTooLarge})
			continue
		}
		own[path] = entry
	}

	repo := &repoBuild{
		Repository: Repository{Name: name, Root: root, RemoteURL: remote, Disk: scan.Delivered, CutOver: cutOver},
		managed:    managed,
		own:        own,
	}
	family, err := b.familyPack(repo)
	if err != nil {
		return err
	}
	repo.family = family
	repo.FamilyPack = family
	if err := b.needsAgents(repo, family); err != nil {
		return err
	}
	b.repos = append(b.repos, repo)
	return nil
}

// familyPack is the pack that takes the repository's own files: the one --repo-pack names, else
// the last pack of .agents/.managed.json other than org and the deprecated ones, else a pack named
// after the repository.
func (b *builder) familyPack(repo *repoBuild) (string, error) {
	family, named := b.opts.RepoPacks[repo.Name]
	if !named {
		family, named = b.opts.RepoPacks[""]
	}
	switch {
	case named && family == rootPackName:
		return "", fmt.Errorf("--repo-pack %s: org is shared by every repository and cannot hold the own files of %s", family, repo.Name)
	case named && deprecatedPacks[family]:
		return "", fmt.Errorf("--repo-pack %s: the pack is deprecated and is not imported", family)
	case named:
		return family, nil
	case repo.CutOver:
		return "", fmt.Errorf("%s has no .agents/.managed.json to tell its family pack; name it with --repo-pack %s=<pack>",
			repo.Root, repo.Name)
	}
	for i := len(repo.managed.Packs) - 1; i >= 0; i-- {
		if name := repo.managed.Packs[i]; name != rootPackName && !deprecatedPacks[name] {
			return name, nil
		}
	}
	return repo.Name, nil
}

// checkRepoPacks refuses a --repo-pack that names a repository the seed does not read.
func (b *builder) checkRepoPacks() error {
	known := map[string]bool{"": true}
	for _, repo := range b.repos {
		known[repo.Name] = true
	}
	for name, pack := range b.opts.RepoPacks {
		if !known[name] {
			return fmt.Errorf("--repo-pack %s=%s names a repository no --repo gives", name, pack)
		}
	}
	return nil
}

// needsAgents refuses to send a pack of korurlabs/agents without the clone: a seeded set
// replaces every file of its pack, so it must carry the clone's files too.
func (b *builder) needsAgents(repo *repoBuild, pack string) error {
	if b.opts.AgentsDir != "" {
		return nil
	}
	for _, name := range repo.managed.Packs {
		if name == pack {
			return fmt.Errorf("the pack %s of %s comes from korurlabs/agents, and a seed replaces every file of a pack; "+
				"pass --agents so that its files are sent too", pack, repo.Name)
		}
	}
	return nil
}

// foldOwnFiles adds each repository's own files to its family pack; on a path the pack also
// holds, the repository's file wins, since it is what the repository set itself. Two repositories
// with own files cannot share a family pack: each would receive the other's files.
func (b *builder) foldOwnFiles() error {
	owners := map[string]string{}
	for _, repo := range b.repos {
		if len(repo.own) > 0 {
			if other, shared := owners[repo.family]; shared {
				return fmt.Errorf("%s and %s would both put their own files into the pack %s; give one of them another "+
					"family pack with --repo-pack <repository>=<pack>", other, repo.Name, repo.family)
			}
			owners[repo.family] = repo.Name
		}
		if held, ok := b.sets[repo.family]; ok {
			for _, path := range sortedPaths(repo.own) {
				if _, clash := held[path]; clash {
					b.plan.Warnings = append(b.plan.Warnings, fmt.Sprintf("%s: the repository's file replaces that of the pack %s",
						prefixed(repo.Name, path), repo.family))
				}
			}
		}
		b.addFiles(repo.family, repo.own)
	}
	return nil
}

// addFiles adds files to the set of the pack, creating it in plan order; a file given later wins.
func (b *builder) addFiles(name string, files map[string]Entry) {
	set, ok := b.sets[name]
	if !ok {
		set = map[string]Entry{}
		b.sets[name] = set
		b.order = append(b.order, name)
	}
	for path, entry := range files {
		set[path] = entry
	}
}

// writeSets writes the sets into the request with their parents.
func (b *builder) writeSets() error {
	for _, name := range sortedParentNames(b.opts.Parents) {
		if _, ok := b.sets[name]; !ok {
			return fmt.Errorf("--pack-parent %s=%s names a pack this seed does not send", name, b.opts.Parents[name])
		}
	}
	for _, name := range b.order {
		parent, named := b.opts.Parents[name]
		switch {
		case named:
		case name == rootPackName:
			parent = ""
		case b.parents[name] != "":
			parent = b.parents[name]
		default:
			parent = rootPackName
		}
		b.plan.addSet(client.SeedSet{Kind: client.KindPack, Name: name, Parent: parent}, b.sets[name])
	}
	return nil
}

// writeRepositories gives each repository its pack list: the packs of .agents/.managed.json at
// its root, in order and without the deprecated and the mounted ones, then its family pack, then
// its mounted packs. A cut-over repository keeps its stored list.
func (b *builder) writeRepositories() {
	for _, repo := range b.repos {
		b.plan.Repositories = append(b.plan.Repositories, repo.Repository)
		if repo.CutOver {
			continue
		}
		mounted := map[string]bool{}
		for _, mount := range repo.mounts {
			mounted[mount.Name] = true
		}
		request := client.SeedRepository{RemoteURL: repo.RemoteURL, Name: repo.Name, Packs: []client.SeedRepositoryPack{}}
		listed := map[string]bool{}
		for _, name := range repo.managed.Packs {
			if deprecatedPacks[name] {
				b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(repo.Name, "packs/"+name), Reason: ReasonDeprecatedPack})
				continue
			}
			if mounted[name] || listed[name] {
				continue
			}
			listed[name] = true
			request.Packs = append(request.Packs, client.SeedRepositoryPack{Name: name})
		}
		if !listed[repo.family] {
			request.Packs = append(request.Packs, client.SeedRepositoryPack{Name: repo.family})
		}
		for _, mount := range repo.mounts {
			request.Packs = append(request.Packs, client.SeedRepositoryPack{Name: mount.Name, RootPath: mount.Root})
		}
		b.plan.Request.Repositories = append(b.plan.Request.Repositories, request)
	}
}

// checkManaged reports drift of the managed files against .agents/.managed.json, and the managed
// paths the agents clone does not deliver. A managed file only a deprecated pack holds is not
// delivered any more, so it leaves the release comparison.
func (b *builder) checkManaged(name string, scan RepoScan, managed Managed) {
	haveAgents := b.opts.AgentsDir != ""
	paths := make([]string, 0, len(managed.Files))
	for path := range managed.Files {
		paths = append(paths, path)
	}
	sort.Strings(paths)
	for _, path := range paths {
		expected := managed.Files[path]
		pack := b.agents.packOf(path, managed.Packs)
		if pack == "" && b.agents.deprecatedOf(path, managed.Packs) != "" {
			delete(scan.Delivered, path)
			b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(name, path), Reason: ReasonDeprecatedPack})
			continue
		}
		if haveAgents && pack == "" {
			b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(name, path), Reason: ReasonNotInPacks})
		}
		disk, onDisk := scan.Delivered[path]
		if target, isLink := strings.CutPrefix(expected, symlinkPrefix); isLink {
			if !onDisk || disk.Mode != client.ModeSymlink || disk.Target != target {
				b.plan.Warnings = append(b.plan.Warnings, fmt.Sprintf("%s: the managed link should point to %s", prefixed(name, path), target))
			}
			continue
		}
		if onDisk && disk.SHA256 == expected {
			continue
		}
		drift := client.Drift{Path: prefixed(name, path), Pack: pack, ExpectedSHA256: expected}
		if onDisk {
			drift.ActualSHA256 = disk.SHA256
		}
		b.plan.Request.Drift = append(b.plan.Request.Drift, drift)
	}
}

func (p *Plan) addSet(set client.SeedSet, files map[string]Entry) {
	set.Files = make([]client.SeedFile, 0, len(files))
	for _, path := range sortedPaths(files) {
		entry := files[path]
		set.Files = append(set.Files, entry.SeedFile())
		if entry.Mode != client.ModeSymlink {
			p.Blobs[entry.SHA256] = entry
		}
	}
	p.Request.Sets = append(p.Request.Sets, set)
}

func (p *Plan) checkLimits() error {
	if len(p.Request.Sets) > maxSets {
		return fmt.Errorf("%d sets; a seed takes at most %d", len(p.Request.Sets), maxSets)
	}
	files := 0
	for _, set := range p.Request.Sets {
		if len(set.Files) > maxFilesPerSet {
			return fmt.Errorf("set %s has %d files; a set takes at most %d", set.Name, len(set.Files), maxFilesPerSet)
		}
		files += len(set.Files)
	}
	if files > maxFilesPerRun {
		return fmt.Errorf("%d files in all; a seed takes at most %d", files, maxFilesPerRun)
	}
	var content int64
	for _, blob := range p.Blobs {
		content += blob.Size
	}
	if content > maxContentBytes {
		return fmt.Errorf("%d bytes of content; a seed takes at most %d", content, maxContentBytes)
	}
	if len(p.Request.NotImported) > maxReportedPaths {
		return fmt.Errorf("%d paths not imported; a seed reports at most %d", len(p.Request.NotImported), maxReportedPaths)
	}
	if len(p.Request.Drift) > maxReportedPaths {
		return fmt.Errorf("%d drifted files; a seed reports at most %d", len(p.Request.Drift), maxReportedPaths)
	}
	return nil
}

// sourceRef is the commit of the agents clone, or of the first repository without one.
func sourceRef(opts Options) (string, error) {
	ref := strings.TrimSpace(opts.SourceRef)
	if ref == "" {
		dir := opts.AgentsDir
		if dir == "" {
			dir = opts.RepoDirs[0]
		}
		head, err := gitOutput(dir, "rev-parse", "HEAD")
		if err != nil {
			return "", fmt.Errorf("read the commit of %s (or pass --source-ref): %w", dir, err)
		}
		ref = head
	}
	if len(ref) > maxSourceRef {
		return "", fmt.Errorf("the source ref is %d characters; at most %d", len(ref), maxSourceRef)
	}
	return ref, nil
}

// CleanRemote drops the user name and password an https remote may carry, so a token in a remote
// never reaches the server; the recorder cleans its remotes the same way.
func CleanRemote(remote string) string {
	parsed, err := url.Parse(remote)
	if err != nil || parsed.Scheme == "" || parsed.User == nil {
		return remote
	}
	if parsed.Scheme == "ssh" {
		parsed.User = url.User(parsed.User.Username())
		return parsed.String()
	}
	parsed.User = nil
	return parsed.String()
}

func gitOutput(dir string, args ...string) (string, error) {
	cmd := exec.Command("git", append([]string{"-C", dir}, args...)...) // #nosec G204 -- fixed git subcommands; dir is the checkout the user named
	out, err := cmd.Output()
	if err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) && len(exitErr.Stderr) > 0 {
			return "", fmt.Errorf("git %s: %s", strings.Join(args, " "), strings.TrimSpace(string(exitErr.Stderr)))
		}
		return "", fmt.Errorf("git %s: %w", strings.Join(args, " "), err)
	}
	return strings.TrimSpace(string(out)), nil
}

func sortedParentNames(parents map[string]string) []string {
	names := make([]string, 0, len(parents))
	for name := range parents {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

func sortedPackNames(packs map[string]Pack) []string {
	names := make([]string, 0, len(packs))
	for name := range packs {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}
