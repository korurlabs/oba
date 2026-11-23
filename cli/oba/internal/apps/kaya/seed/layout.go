package seed

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	pathpkg "path"
	"path/filepath"
	"sort"
	"strings"

	"gopkg.in/yaml.v3"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// ContentDir is the folder of a seed tree, beside its layout file, whose content/<pack>/<path>
// files take that path in that pack, whatever the layout's rules place there.
const ContentDir = "content"

// ReasonLeftOut is a source file a leftOut entry of the layout keeps out of every pack.
const ReasonLeftOut = "Left out by the seed layout"

// reasonReplaced is a source file whose path in its pack a file of the seed tree's content/
// folder takes.
const reasonReplaced = "Replaced by %s of the seed tree"

// layout is the layout file of a seed tree: the packs with their parents, the packs each
// repository uses with their mount folders, the ordered rules that place each source file in a
// pack, and the source files no pack receives.
type layout struct {
	Packs        []layoutPack       `yaml:"packs"`
	Repositories []layoutRepository `yaml:"repositories"`
	Rules        []layoutRule       `yaml:"rules"`
	LeftOut      []string           `yaml:"leftOut"`
}

// layoutPack is a pack of the layout; Parent is empty for a root pack.
type layoutPack struct {
	Name   string `yaml:"name"`
	Parent string `yaml:"parent"`
}

// layoutRepository is a repository, by the name its remote gives it, with the packs it uses in
// display order.
type layoutRepository struct {
	Name  string                 `yaml:"name"`
	Packs []layoutRepositoryPack `yaml:"packs"`
}

// layoutRepositoryPack is a pack a repository uses at its root, or mounted in Folder.
type layoutRepositoryPack struct {
	Name   string
	Folder string
}

// UnmarshalYAML takes a bare pack name for a pack at the root, or a mapping with name and folder.
// It reads the mapping itself, so that an unknown key fails here as it does everywhere else in the
// layout.
func (p *layoutRepositoryPack) UnmarshalYAML(node *yaml.Node) error {
	if node.Kind == yaml.ScalarNode {
		p.Name = node.Value
		return nil
	}
	if node.Kind != yaml.MappingNode {
		return fmt.Errorf("line %d: give a pack name, or name and folder", node.Line)
	}
	for i := 0; i+1 < len(node.Content); i += 2 {
		key, value := node.Content[i], node.Content[i+1]
		if value.Kind != yaml.ScalarNode {
			return fmt.Errorf("line %d: %s must be a single value", value.Line, key.Value)
		}
		switch key.Value {
		case "name":
			p.Name = value.Value
		case "folder":
			p.Folder = value.Value
		default:
			return fmt.Errorf("line %d: field %s not found in a repository's pack; give name and folder", key.Line, key.Value)
		}
	}
	return nil
}

// layoutRule places every source file one of its paths matches in its pack.
type layoutRule struct {
	Pack  string   `yaml:"pack"`
	Paths []string `yaml:"paths"`
}

// pathPattern is a path of the layout, <source>:<glob>. The source is agents, the clone with its
// paths as they are in the clone, or a repository with its paths relative to its root. In the
// glob, "**" spans any number of folders and every other segment is matched as path.Match does; a
// trailing "/" stands for everything below the folder.
type pathPattern struct {
	text     string
	source   string
	segments []string
}

func (p pathPattern) matches(file sourceFile) bool {
	return p.source == file.source && matchSegments(p.segments, strings.Split(file.path, "/"))
}

func matchSegments(pattern, path []string) bool {
	for len(pattern) > 0 {
		if pattern[0] == "**" {
			for i := 0; i <= len(path); i++ {
				if matchSegments(pattern[1:], path[i:]) {
					return true
				}
			}
			return false
		}
		if len(path) == 0 {
			return false
		}
		// parsePattern refused every segment path.Match calls malformed, and path.Match reports a
		// malformed pattern whatever the name, so no error can come back here.
		if matched, _ := pathpkg.Match(pattern[0], path[0]); !matched {
			return false
		}
		pattern, path = pattern[1:], path[1:]
	}
	return len(path) == 0
}

// compiledRule is a rule with its paths parsed.
type compiledRule struct {
	pack     string
	patterns []pathPattern
}

// seedLayout is a layout file that passed every check, with its patterns parsed.
type seedLayout struct {
	// dir is the seed tree, the folder of the layout file.
	dir     string
	packs   []layoutPack
	parents map[string]string
	repos   map[string]layoutRepository
	// repoNames are the repositories in layout order.
	repoNames []string
	rules     []compiledRule
	leftOut   []pathPattern
}

// readLayout reads and checks the layout file at file.
func readLayout(file string) (*seedLayout, error) {
	data, err := os.ReadFile(file) // #nosec G304 -- the layout file the user named with --layout
	if err != nil {
		return nil, fmt.Errorf("read the layout: %w", err)
	}
	decoder := yaml.NewDecoder(bytes.NewReader(data))
	decoder.KnownFields(true)
	var raw layout
	if err := decoder.Decode(&raw); err != nil {
		if errors.Is(err, io.EOF) {
			return nil, fmt.Errorf("%s is empty; it names the packs, the repositories and the rules", file)
		}
		return nil, fmt.Errorf("parse %s: %w", file, err)
	}
	var extra yaml.Node
	switch err := decoder.Decode(&extra); {
	case err == nil:
		return nil, fmt.Errorf("%s holds more than one YAML document; keep the layout in one", file)
	case !errors.Is(err, io.EOF):
		return nil, fmt.Errorf("parse %s: %w", file, err)
	}
	compiled, err := compileLayout(raw, filepath.Dir(file))
	if err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	return compiled, nil
}

// compileLayout checks the packs, the repositories, the rules and the leftOut entries, and parses
// their paths.
func compileLayout(raw layout, dir string) (*seedLayout, error) {
	l := &seedLayout{dir: dir, packs: raw.Packs, parents: map[string]string{}, repos: map[string]layoutRepository{}}
	if len(raw.Packs) == 0 {
		return nil, errors.New("the layout names no pack")
	}
	for i, pack := range raw.Packs {
		if !setName.MatchString(pack.Name) {
			return nil, fmt.Errorf("packs[%d]: the name %q must be 1 to %d of a-z, 0-9 and '-'", i, pack.Name, maxPackName)
		}
		if _, taken := l.parents[pack.Name]; taken {
			return nil, fmt.Errorf("packs[%d]: the pack %s is named twice", i, pack.Name)
		}
		l.parents[pack.Name] = pack.Parent
	}
	for _, pack := range raw.Packs {
		if _, known := l.parents[pack.Parent]; pack.Parent != "" && !known {
			return nil, fmt.Errorf("the parent %s of the pack %s is not a pack of the layout", pack.Parent, pack.Name)
		}
		seen := map[string]bool{}
		for name := pack.Name; name != ""; name = l.parents[name] {
			if seen[name] {
				return nil, fmt.Errorf("the parents of the pack %s form a cycle", pack.Name)
			}
			seen[name] = true
		}
	}
	for i := range raw.Repositories {
		if err := l.addRepository(i, raw.Repositories[i]); err != nil {
			return nil, err
		}
	}
	for i, rule := range raw.Rules {
		if _, known := l.parents[rule.Pack]; !known {
			return nil, fmt.Errorf("rules[%d]: %q is not a pack of the layout", i, rule.Pack)
		}
		if len(rule.Paths) == 0 {
			return nil, fmt.Errorf("rules[%d] for the pack %s names no path", i, rule.Pack)
		}
		compiled := compiledRule{pack: rule.Pack}
		for _, text := range rule.Paths {
			pattern, err := l.parsePattern(text)
			if err != nil {
				return nil, fmt.Errorf("rules[%d]: %w", i, err)
			}
			compiled.patterns = append(compiled.patterns, pattern)
		}
		l.rules = append(l.rules, compiled)
	}
	for i, text := range raw.LeftOut {
		pattern, err := l.parsePattern(text)
		if err != nil {
			return nil, fmt.Errorf("leftOut[%d]: %w", i, err)
		}
		l.leftOut = append(l.leftOut, pattern)
	}
	return l, nil
}

// addRepository checks a repository of the layout: a valid name, known packs each listed once,
// and mount folders that are valid and do not overlap.
func (l *seedLayout) addRepository(i int, repo layoutRepository) error {
	switch {
	case !setName.MatchString(repo.Name):
		return fmt.Errorf("repositories[%d]: the name %q must be the repository's name as its remote gives it, "+
			"1 to %d of a-z, 0-9 and '-'", i, repo.Name, maxPackName)
	case repo.Name == agentsSource:
		return fmt.Errorf("repositories[%d]: %s names the agents clone in the layout's paths, so no repository can take that name", i, agentsSource)
	}
	if _, taken := l.repos[repo.Name]; taken {
		return fmt.Errorf("repositories[%d]: the repository %s is named twice", i, repo.Name)
	}
	listed := map[string]bool{}
	var folders []layoutRepositoryPack
	for j := range repo.Packs {
		pack := &repo.Packs[j]
		where := fmt.Sprintf("repositories[%d] %s, packs[%d]", i, repo.Name, j)
		if _, known := l.parents[pack.Name]; !known {
			return fmt.Errorf("%s: %q is not a pack of the layout", where, pack.Name)
		}
		if listed[pack.Name] {
			return fmt.Errorf("%s: the pack %s is listed twice", where, pack.Name)
		}
		listed[pack.Name] = true
		if pack.Folder == "" {
			continue
		}
		pack.Folder = strings.TrimSuffix(filepath.ToSlash(pack.Folder), "/")
		if !mountRoot.MatchString(pack.Folder) {
			return fmt.Errorf("%s: the folder %q must be 1 to 4 folders of the repository, each of A-Z, a-z, 0-9, '_', '.' "+
				"and '-' and not starting with '.'", where, pack.Folder)
		}
		if first, _, _ := strings.Cut(pack.Folder, "/"); reservedRoots[first] {
			return fmt.Errorf("%s: a pack cannot be mounted in %s/", where, first)
		}
		for _, other := range folders {
			if pack.Folder == other.Folder || strings.HasPrefix(pack.Folder, other.Folder+"/") || strings.HasPrefix(other.Folder, pack.Folder+"/") {
				return fmt.Errorf("%s: the folders of the packs %s (%s) and %s (%s) overlap; nested mounts are not supported",
					where, other.Name, other.Folder, pack.Name, pack.Folder)
			}
		}
		folders = append(folders, *pack)
	}
	l.repos[repo.Name] = repo
	l.repoNames = append(l.repoNames, repo.Name)
	return nil
}

// parsePattern reads a path of the layout, <source>:<glob>.
func (l *seedLayout) parsePattern(text string) (pathPattern, error) {
	source, glob, found := strings.Cut(text, ":")
	if !found || source == "" || glob == "" {
		return pathPattern{}, fmt.Errorf("%q: give <source>:<path>, as %s:packs/<pack>/** or <repository>:.agents/rules/*.md", text, agentsSource)
	}
	if _, known := l.repos[source]; !known && source != agentsSource {
		return pathPattern{}, fmt.Errorf("%q: the source %s is neither %s nor a repository of the layout", text, source, agentsSource)
	}
	if strings.HasSuffix(glob, "/") {
		glob += "**"
	}
	segments := strings.Split(glob, "/")
	for _, segment := range segments {
		switch {
		case segment == "" || segment == "." || segment == "..":
			return pathPattern{}, fmt.Errorf("%q: the path has an empty, '.' or '..' folder", text)
		case segment == "**":
		case strings.Contains(segment, "**"):
			return pathPattern{}, fmt.Errorf("%q: ** stands for whole folders, so it must be a segment of its own", text)
		default:
			if _, err := pathpkg.Match(segment, ""); err != nil {
				return pathPattern{}, fmt.Errorf("%q: %w", text, err)
			}
		}
	}
	return pathPattern{text: text, source: source, segments: segments}, nil
}

// sourcesOf names the sources each pack takes files from: those of its rules' paths, and the
// repositories it is mounted in, whose folder AGENTS.md it holds.
func (l *seedLayout) sourcesOf() map[string]map[string]bool {
	sources := map[string]map[string]bool{}
	add := func(pack, source string) {
		if sources[pack] == nil {
			sources[pack] = map[string]bool{}
		}
		sources[pack][source] = true
	}
	for _, rule := range l.rules {
		for _, pattern := range rule.patterns {
			add(rule.pack, pattern.source)
		}
	}
	for _, name := range l.repoNames {
		for _, pack := range l.repos[name].Packs {
			if pack.Folder != "" {
				add(pack.Name, name)
			}
		}
	}
	return sources
}

// readContent reads content/<pack>/<path> of the seed tree, by pack and path. A seed tree without
// the folder has no content.
func (l *seedLayout) readContent() (map[string]map[string]Entry, error) {
	base := filepath.Join(l.dir, ContentDir)
	content := map[string]map[string]Entry{}
	info, err := os.Lstat(base)
	switch {
	case errors.Is(err, os.ErrNotExist):
		return content, nil
	case err != nil:
		return nil, fmt.Errorf("read %s: %w", base, err)
	case !info.IsDir():
		return nil, fmt.Errorf("%s is not a folder; it holds content/<pack>/<path>", base)
	}
	entries, err := os.ReadDir(base)
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", base, err)
	}
	var problems []string
	for _, entry := range entries {
		name := entry.Name()
		if ignoredPackFiles[name] {
			continue
		}
		if _, known := l.parents[name]; !known || !entry.IsDir() {
			problems = append(problems, ContentDir+"/"+name+": not a folder named after a pack of the layout")
			continue
		}
		files, packProblems, err := readContentPack(filepath.Join(base, name), name)
		if err != nil {
			return nil, err
		}
		problems = append(problems, packProblems...)
		content[name] = files
	}
	if len(problems) > 0 {
		return nil, fmt.Errorf("the seed tree's %s folder holds files the seed cannot send:\n  %s", ContentDir, strings.Join(problems, "\n  "))
	}
	return content, nil
}

// readContentPack reads the files of one pack's content folder, and names each one the seed cannot
// send. Placeholders and local litter are skipped as in a pack of the clone.
func readContentPack(dir, pack string) (map[string]Entry, []string, error) {
	files := map[string]Entry{}
	var problems []string
	err := filepath.WalkDir(dir, func(abs string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if d.IsDir() {
			if abs != dir && ignoredPackDirs[d.Name()] {
				return filepath.SkipDir
			}
			return nil
		}
		if ignoredPackFiles[d.Name()] {
			return nil
		}
		rel, err := filepath.Rel(dir, abs)
		if err != nil {
			return err
		}
		rel = filepath.ToSlash(rel)
		name := contentName(pack, rel)
		entry, ok, err := readEntry(abs, rel)
		switch {
		case err != nil:
			return err
		case !ok:
			problems = append(problems, name+": "+ReasonNotRegular)
		case !IsAllowedPath(rel):
			problems = append(problems, name+": "+ReasonNotDelivered)
		case entry.Mode != client.ModeSymlink && entry.SHA256 == "":
			problems = append(problems, name+": "+ReasonTooLarge)
		default:
			files[rel] = entry
		}
		return nil
	})
	if err != nil {
		return nil, nil, fmt.Errorf("walk %s: %w", dir, err)
	}
	return files, problems, nil
}

// contentName names a file of the seed tree's content folder.
func contentName(pack, path string) string {
	return ContentDir + "/" + pack + "/" + path
}

// sourceFile is a file a source offers the layout.
type sourceFile struct {
	// source is agents or a repository's name.
	source string
	// group is where a skill name is unique within the source: packs/<pack> for the clone, empty
	// for a repository.
	group string
	// path is the file as the layout's paths see it: its path in the clone, or in the repository.
	path string
	// entry is the file with the path its pack stores it at.
	entry Entry
	// mount is the pack mounted in a folder of the repository whose AGENTS.md this is: the file goes
	// to that pack whatever the rules say.
	mount string
	// generated marks a skill link the seed derives from a skill of the clone, as the sync writes it.
	generated bool
}

func (f sourceFile) name() string {
	return prefixed(f.source, f.path)
}

// agentsSourceFiles lists the files of the clone's packs by their path in the clone.
func agentsSourceFiles(root string, scan AgentsScan) ([]sourceFile, error) {
	var files []sourceFile
	for _, pack := range sortedPackNames(scan.Packs) {
		group := "packs/" + pack
		entries := scan.Packs[pack].Files
		for _, path := range sortedPaths(entries) {
			entry := entries[path]
			if entry.Source == "" {
				skill := pathpkg.Base(path)
				files = append(files, sourceFile{source: agentsSource, group: group, path: group + "/skills/" + skill,
					entry: entry, generated: true})
				continue
			}
			rel, err := filepath.Rel(root, entry.Source)
			if err != nil {
				return nil, fmt.Errorf("locate %s in the agents clone: %w", entry.Source, err)
			}
			files = append(files, sourceFile{source: agentsSource, group: group, path: filepath.ToSlash(rel), entry: entry})
		}
	}
	return files, nil
}

// skillLink names the skill whose link entry is: .claude/skills/<name> pointing at
// ../../.agents/skills/<name>, as the sync writes it beside each skill.
func skillLink(entry Entry) (string, bool) {
	name, found := strings.CutPrefix(entry.Path, ".claude/skills/")
	if !found || name == "" || strings.Contains(name, "/") || entry.Mode != client.ModeSymlink {
		return "", false
	}
	return name, entry.Target == "../../.agents/skills/"+name
}

// skillOf names the skill a file under .agents/skills/<name>/ belongs to, or "".
func skillOf(path string) string {
	rest, found := strings.CutPrefix(path, ".agents/skills/")
	if !found {
		return ""
	}
	name, _, below := strings.Cut(rest, "/")
	if !below {
		return ""
	}
	return name
}

func sameEntry(a, b Entry) bool {
	return a.Mode == b.Mode && a.SHA256 == b.SHA256 && a.Target == b.Target
}

// PlacementError names every source file the layout places nowhere, and every conflict of its
// placement, so that one run shows all there is to fix.
type PlacementError struct {
	// Unplaced are the files no rule and no leftOut entry matches, as <source>:<path>.
	Unplaced []string
	// Conflicts are placements the seed cannot send, such as two files for one path of a pack.
	Conflicts []string
}

func (e *PlacementError) Error() string {
	var text strings.Builder
	if len(e.Unplaced) > 0 {
		_, _ = fmt.Fprintf(&text, "%d files match no rule and no leftOut entry of the layout; place each with a rule or leave it out:", len(e.Unplaced))
		for _, name := range e.Unplaced {
			text.WriteString("\n  " + name)
		}
	}
	if len(e.Conflicts) > 0 {
		if text.Len() > 0 {
			text.WriteString("\n")
		}
		text.WriteString("the layout places files in conflict:")
		for _, conflict := range e.Conflicts {
			text.WriteString("\n  " + conflict)
		}
	}
	return text.String()
}

// patternRef is a path of the layout by position: a rule and its path, or rule -1 for a leftOut
// entry.
type patternRef struct {
	rule, path int
}

// placement is where the layout puts each file of the sources a run reads.
type placement struct {
	// packs holds the files of each pack by the path the pack stores them at.
	packs     map[string]map[string]sourceFile
	leftOut   []sourceFile
	unplaced  []string
	conflicts []string
	used      map[patternRef]bool
}

// place gives each file of the sources, in order, its pack: a mount folder's AGENTS.md goes to the
// mounted pack, a file a leftOut entry matches to none, any other file to the pack of the first rule
// that matches it. A skill link follows the pack of its skill's files.
func (l *seedLayout) place(order []string, sources map[string][]sourceFile) placement {
	p := placement{packs: map[string]map[string]sourceFile{}, used: map[patternRef]bool{}}
	for _, source := range order {
		p.placeSource(l, sources[source])
	}
	sort.Strings(p.unplaced)
	sort.Strings(p.conflicts)
	return p
}

type skillKey struct {
	source, group, name string
}

func (p *placement) placeSource(l *seedLayout, files []sourceFile) {
	skillPacks := map[skillKey]map[string]bool{}
	skillLeftOut := map[skillKey]bool{}
	var links []sourceFile
	for _, file := range files {
		if file.mount != "" {
			p.put(file.mount, file)
			continue
		}
		if _, isLink := skillLink(file.entry); isLink {
			links = append(links, file)
			continue
		}
		pack, leftOut := p.route(l, file)
		name := skillOf(file.entry.Path)
		if name == "" {
			continue
		}
		key := skillKey{file.source, file.group, name}
		switch {
		case pack != "":
			if skillPacks[key] == nil {
				skillPacks[key] = map[string]bool{}
			}
			skillPacks[key][pack] = true
		case leftOut:
			skillLeftOut[key] = true
		}
	}
	for _, file := range links {
		name, _ := skillLink(file.entry)
		key := skillKey{file.source, file.group, name}
		packs := sortedNames(skillPacks[key])
		switch {
		case len(packs) == 1:
			p.put(packs[0], file)
		case len(packs) > 1:
			// The split skill is a conflict of its own, named once below.
		case skillLeftOut[key]:
			p.leftOut = append(p.leftOut, file)
		case file.generated:
			// Its skill's files are all unplaced and named as such, which fails the seed.
		default:
			p.route(l, file)
		}
	}
	keys := make([]skillKey, 0, len(skillPacks))
	for key := range skillPacks {
		keys = append(keys, key)
	}
	sort.Slice(keys, func(i, j int) bool { return keys[i].group+"/"+keys[i].name < keys[j].group+"/"+keys[j].name })
	for _, key := range keys {
		if packs := sortedNames(skillPacks[key]); len(packs) > 1 {
			owner := key.source
			if key.group != "" {
				owner = prefixed(key.source, key.group)
			}
			p.conflicts = append(p.conflicts, fmt.Sprintf("the files of the skill %s of %s go to the packs %s; a skill and its link belong to one pack",
				key.name, owner, strings.Join(packs, ", ")))
		}
	}
}

// route leaves file out when a leftOut entry matches it, else places it in the pack of the first
// rule that matches it, else counts it unplaced. It returns the pack, or whether it was left out.
func (p *placement) route(l *seedLayout, file sourceFile) (pack string, leftOut bool) {
	for i, pattern := range l.leftOut {
		if pattern.matches(file) {
			p.used[patternRef{-1, i}] = true
			p.leftOut = append(p.leftOut, file)
			return "", true
		}
	}
	for i, rule := range l.rules {
		for j, pattern := range rule.patterns {
			if pattern.matches(file) {
				p.used[patternRef{i, j}] = true
				p.put(rule.pack, file)
				return rule.pack, false
			}
		}
	}
	p.unplaced = append(p.unplaced, file.name())
	return "", false
}

// put adds file to pack at the path the pack stores it at. The same file from a second source
// changes nothing; a different one is a conflict.
func (p *placement) put(pack string, file sourceFile) {
	files := p.packs[pack]
	if files == nil {
		files = map[string]sourceFile{}
		p.packs[pack] = files
	}
	if held, taken := files[file.entry.Path]; taken {
		if !sameEntry(held.entry, file.entry) {
			p.conflicts = append(p.conflicts, fmt.Sprintf("the pack %s gets %s from both %s and %s", pack, file.entry.Path, held.name(), file.name()))
		}
		return
	}
	files[file.entry.Path] = file
}

func sortedNames(set map[string]bool) []string {
	names := make([]string, 0, len(set))
	for name := range set {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

// ContentFile is a file of the seed tree's content folder in the pack it takes a path of.
type ContentFile struct {
	Pack string
	Path string
	// Replaces names the source file it takes the place of as <source>:<path>; empty when it adds the
	// path to the pack.
	Replaces string
}

// buildLayout builds the seed from a seed tree's layout instead of the clone's pack folders: the
// layout's packs with their parents, each file of the clone and of the repositories in the pack the
// layout gives it, the content folder's files over them, and each repository's pack list from the
// layout. It is incremental: a pack is sent only when the run reads every source the pack takes
// files from, and a repository the run does not read keeps its stored pack list, so a later run
// can add packs and repositories while Kaya leaves every pack with equal files as it is.
func buildLayout(opts Options) (Plan, error) {
	if err := checkLayoutOptions(opts); err != nil {
		return Plan{}, err
	}
	l, err := readLayout(opts.Layout)
	if err != nil {
		return Plan{}, err
	}
	content, err := l.readContent()
	if err != nil {
		return Plan{}, err
	}

	plan := Plan{Blobs: map[string]Entry{}}
	b := &builder{plan: &plan, opts: opts, sets: map[string]map[string]Entry{}, parents: map[string]string{}}
	sources := map[string][]sourceFile{}
	var order []string
	if opts.AgentsDir != "" {
		scan, err := ScanAgents(opts.AgentsDir)
		if err != nil {
			return Plan{}, err
		}
		b.agents = scan
		plan.Request.NotImported = append(plan.Request.NotImported, scan.NotImported...)
		files, err := agentsSourceFiles(opts.AgentsDir, scan)
		if err != nil {
			return Plan{}, err
		}
		sources[agentsSource] = files
		order = append(order, agentsSource)
	}
	names := map[string]string{}
	for _, dir := range opts.RepoDirs {
		root, err := filepath.Abs(dir)
		if err != nil {
			return Plan{}, fmt.Errorf("resolve %s: %w", dir, err)
		}
		repo, files, err := b.readLayoutRepo(l, root, names)
		if err != nil {
			return Plan{}, err
		}
		b.repos = append(b.repos, repo)
		sources[repo.Name] = files
		order = append(order, repo.Name)
	}

	skipped := l.skippedPacks(sources)
	placed := l.place(order, sources)
	if len(placed.unplaced) > 0 || len(placed.conflicts) > 0 {
		return Plan{}, &PlacementError{Unplaced: placed.unplaced, Conflicts: placed.conflicts}
	}
	l.writeSets(&plan, placed, content, skipped)
	for _, file := range placed.leftOut {
		if !file.generated {
			plan.Request.NotImported = append(plan.Request.NotImported, client.NotImported{Path: file.name(), Reason: ReasonLeftOut})
		}
	}
	for _, repo := range b.repos {
		request := client.SeedRepository{RemoteURL: repo.RemoteURL, Name: repo.Name, Packs: []client.SeedRepositoryPack{}}
		for _, pack := range l.repos[repo.Name].Packs {
			request.Packs = append(request.Packs, client.SeedRepositoryPack{Name: pack.Name, RootPath: pack.Folder})
		}
		plan.Request.Repositories = append(plan.Request.Repositories, request)
		repo.OnPurpose = l.onPurpose(repo.Name, content, placed.leftOut)
		plan.Repositories = append(plan.Repositories, repo.Repository)
	}
	plan.Warnings = append(plan.Warnings, l.warnings(sources, placed, skipped)...)

	ref, err := sourceRef(opts)
	if err != nil {
		return Plan{}, err
	}
	plan.Request.SourceRef = ref
	return plan, plan.checkLimits()
}

// checkLayoutOptions refuses the flags whose job the layout does.
func checkLayoutOptions(opts Options) error {
	switch {
	case len(opts.RepoDirs) == 0:
		return fmt.Errorf("name at least one repository with --repo")
	case len(opts.Mounts) > 0:
		return fmt.Errorf("--pack does not apply with --layout: the layout names the packs mounted in each repository's folders")
	case len(opts.Parents) > 0:
		return fmt.Errorf("--pack-parent does not apply with --layout: the layout names the parent of each pack")
	case len(opts.RepoPacks) > 0:
		return fmt.Errorf("--repo-pack does not apply with --layout: the layout's rules place each repository's files")
	case opts.AfterCutover:
		return fmt.Errorf("--after-cutover does not apply with --layout: a repository the layout names is read with or without .agents/.managed.json")
	}
	return nil
}

// readLayoutRepo reads a repository the layout names: its own files, and the AGENTS.md of each
// folder a pack is mounted in. A repository without .agents/.managed.json is read as it is.
func (b *builder) readLayoutRepo(l *seedLayout, root string, names map[string]string) (*repoBuild, []sourceFile, error) {
	managed, err := ReadManaged(root)
	cutOver := errors.Is(err, ErrNotManaged)
	if err != nil && !cutOver {
		return nil, nil, err
	}
	repo, err := b.readRepo(root, names, managed, cutOver)
	if err != nil {
		return nil, nil, err
	}
	entry, listed := l.repos[repo.Name]
	if !listed {
		return nil, nil, fmt.Errorf("%s is the repository %s, which the layout does not name; add it under repositories with its packs",
			root, repo.Name)
	}
	files := make([]sourceFile, 0, len(repo.own))
	for _, path := range sortedPaths(repo.own) {
		files = append(files, sourceFile{source: repo.Name, path: path, entry: repo.own[path]})
	}
	for _, pack := range entry.Packs {
		if pack.Folder == "" {
			continue
		}
		mounted, err := b.readMountFolder(repo, pack)
		if err != nil {
			return nil, nil, err
		}
		files = append(files, mounted...)
	}
	return repo, files, nil
}

// readMountFolder reads the AGENTS.md of the folder pack is mounted in, stored as the pack's
// AGENTS.md; the other agent files below the folder stay in git and are reported.
func (b *builder) readMountFolder(repo *repoBuild, pack layoutRepositoryPack) ([]sourceFile, error) {
	info, err := os.Lstat(filepath.Join(repo.Root, filepath.FromSlash(pack.Folder)))
	switch {
	case errors.Is(err, os.ErrNotExist):
		return nil, fmt.Errorf("the layout mounts the pack %s in %s, which %s does not have", pack.Name, pack.Folder, repo.Root)
	case err != nil:
		return nil, fmt.Errorf("read the folder of the pack %s: %w", pack.Name, err)
	case !info.IsDir():
		return nil, fmt.Errorf("%s of %s is not a directory, so the pack %s cannot be mounted there", pack.Folder, repo.Root, pack.Name)
	}
	scan, err := scanMountFolder(repo.Root, pack.Folder)
	if err != nil {
		return nil, err
	}
	for _, item := range scan.NotImported {
		b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(repo.Name, item.Path), Reason: item.Reason})
	}
	var files []sourceFile
	for _, path := range sortedPaths(scan.Delivered) {
		entry := scan.Delivered[path]
		// The release comparison expects every delivered path on disk, the oversized one too.
		repo.Disk[path] = entry
		if entry.Mode != client.ModeSymlink && entry.SHA256 == "" {
			b.plan.Request.NotImported = append(b.plan.Request.NotImported, client.NotImported{Path: prefixed(repo.Name, path), Reason: ReasonTooLarge})
			continue
		}
		stored := entry
		stored.Path = agentsFile
		files = append(files, sourceFile{source: repo.Name, path: path, entry: stored, mount: pack.Name})
	}
	return files, nil
}

// skippedPacks names each pack that takes files from a source the run does not read, with those
// sources: sending it would replace its stored files with only the ones this run has.
func (l *seedLayout) skippedPacks(sources map[string][]sourceFile) map[string][]string {
	skipped := map[string][]string{}
	for pack, from := range l.sourcesOf() {
		for _, source := range sortedNames(from) {
			if _, read := sources[source]; !read {
				skipped[pack] = append(skipped[pack], source)
			}
		}
	}
	return skipped
}

// writeSets writes each pack the run sends, in layout order: the files the layout placed in it,
// with the content folder's files over them.
func (l *seedLayout) writeSets(plan *Plan, placed placement, content map[string]map[string]Entry, skipped map[string][]string) {
	for _, pack := range l.packs {
		if len(skipped[pack.Name]) > 0 {
			continue
		}
		files := map[string]Entry{}
		for path, file := range placed.packs[pack.Name] {
			files[path] = file.entry
		}
		for _, path := range sortedPaths(content[pack.Name]) {
			change := ContentFile{Pack: pack.Name, Path: path}
			if replaced, held := placed.packs[pack.Name][path]; held {
				change.Replaces = replaced.name()
				plan.Request.NotImported = append(plan.Request.NotImported, client.NotImported{
					Path: replaced.name(), Reason: fmt.Sprintf(reasonReplaced, contentName(pack.Name, path))})
			}
			files[path] = content[pack.Name][path]
			plan.Content = append(plan.Content, change)
		}
		plan.addSet(client.SeedSet{Kind: client.KindPack, Name: pack.Name, Parent: pack.Parent}, files)
	}
}

// onPurpose names the paths where the release of the repository differs from its disk by design:
// each content file of a pack the repository composes (the packs it uses and their ancestors), and
// each file left out of the clone or of the repository.
func (l *seedLayout) onPurpose(repo string, content map[string]map[string]Entry, leftOut []sourceFile) map[string]string {
	folders := map[string]string{}
	composed := map[string]bool{}
	for _, pack := range l.repos[repo].Packs {
		if pack.Folder != "" {
			folders[pack.Name] = pack.Folder
		}
		for name := pack.Name; name != "" && !composed[name]; name = l.parents[name] {
			composed[name] = true
		}
	}
	reasons := map[string]string{}
	for _, pack := range l.packs {
		if !composed[pack.Name] {
			continue
		}
		for _, path := range sortedPaths(content[pack.Name]) {
			release := path
			if folder := folders[pack.Name]; folder != "" && path == agentsFile {
				release = folder + "/" + agentsFile
			}
			reasons[release] = "from " + contentName(pack.Name, path) + " of the seed tree"
		}
	}
	for _, file := range leftOut {
		if file.source == agentsSource || file.source == repo {
			reasons[file.entry.Path] = "left out by the seed layout"
		}
	}
	return reasons
}

// warnings name what the run leaves as Kaya holds it, and the layout's paths that took no file.
func (l *seedLayout) warnings(sources map[string][]sourceFile, placed placement, skipped map[string][]string) []string {
	var warnings []string
	for _, pack := range l.packs {
		if from := skipped[pack.Name]; len(from) > 0 {
			warnings = append(warnings, fmt.Sprintf("the pack %s is not sent: it takes files from %s, which this run does not read, "+
				"so Kaya keeps it as it is", pack.Name, strings.Join(from, ", ")))
		}
	}
	for _, name := range l.repoNames {
		if _, read := sources[name]; !read {
			warnings = append(warnings, fmt.Sprintf("the repository %s is not read in this run, so Kaya keeps its pack list as it is", name))
		}
	}
	for i, rule := range l.rules {
		for j, pattern := range rule.patterns {
			if _, read := sources[pattern.source]; read && !placed.used[patternRef{i, j}] {
				warnings = append(warnings, fmt.Sprintf("the rule %d for the pack %s places no file with %s", i+1, rule.pack, pattern.text))
			}
		}
	}
	for i, pattern := range l.leftOut {
		if _, read := sources[pattern.source]; read && !placed.used[patternRef{-1, i}] {
			warnings = append(warnings, fmt.Sprintf("the leftOut entry %s leaves out no file", pattern.text))
		}
	}
	return warnings
}
