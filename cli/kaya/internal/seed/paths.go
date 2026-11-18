// Package seed reads today's agent config from disk (the packs of a korurlabs/agents clone and
// the consumer repositories' own files), builds the seed request, and compares a seeded release
// with the repository it came from.
package seed

import (
	pathpkg "path"
	"regexp"
	"strings"
)

// MaxBlobBytes is the largest file the server stores as one blob.
const MaxBlobBytes = 5 << 20

// ConventionsDir holds the convention docs the rules cite. Only its Markdown files are delivered,
// so that the cited paths resolve in every checkout.
const ConventionsDir = "docs/private/conventions"

// allowedPath is the server's path allow-list: only these paths are delivered. .claude/rules and
// .claude/templates are the tool links Claude Code finds the shared rules and
// templates of .agents/ through; they are allowed exactly, never a path below them, so that a
// release cannot write into .agents/ through the link. The last alternative is the AGENTS.md of a
// pack mounted in a folder: one to four segments that never start with a dot, so that it can name
// neither .agents/, .claude/ nor a parent directory.
var allowedPath = regexp.MustCompile(`^(AGENTS\.md|\.agents/AGENTS\.md|\.claude/settings\.json` +
	`|\.claude/(rules|templates)` +
	`|\.agents/(rules|skills|roles|workflows|templates|hooks)/.+|\.claude/(agents|skills)/.+` +
	`|docs/private/conventions/.+\.md|([A-Za-z0-9_][A-Za-z0-9._-]*/){1,4}AGENTS\.md)$`)

// setName is the server's pattern of a pack name.
var setName = regexp.MustCompile(`^[a-z0-9-]{1,60}$`)

// mountRoot is the server's pattern of a mount folder.
var mountRoot = regexp.MustCompile(`^[A-Za-z0-9_][A-Za-z0-9._-]*(/[A-Za-z0-9_][A-Za-z0-9._-]*){0,3}$`)

// maxPackName is the longest name setName takes.
const maxPackName = 60

// rootPackName is the root of the pack tree: every other pack is its child unless the seed is
// told otherwise.
const rootPackName = "org"

// deprecatedPacks are packs of korurlabs/agents Kaya no longer imports.
var deprecatedPacks = map[string]bool{"mdf": true}

// reservedRoots are first segments a mount folder may not have: the agent config and the docs
// belong to the repository root, wherever a pack is mounted.
var reservedRoots = map[string]bool{".agents": true, ".claude": true, "docs": true}

// namespaceRoles are the directories of .agents/ whose <pack>/ subdirectory is the pack's.
var namespaceRoles = []string{"roles", "workflows", "templates"}

// inPackNamespace reports whether path belongs to the namespace of the pack of that name, the
// paths a pack mounted in a folder may deliver: its rules, skills and skill links, subagents,
// roles, workflows, templates and convention docs. The pack's <folder>/AGENTS.md is not
// a namespace path; the seed reads it from the mount folder.
func inPackNamespace(path, pack string) bool {
	segments := strings.Split(path, "/")
	prefixed := func(segment string) bool {
		return strings.HasPrefix(segment, pack+"-") && len(segment) > len(pack)+1
	}
	switch {
	case strings.HasPrefix(path, ".agents/rules/"+pack+"/"):
		return strings.HasSuffix(path, ".md")
	case strings.HasPrefix(path, ConventionsDir+"/projects/"+pack+"/"):
		return strings.HasSuffix(path, ".md")
	case len(segments) >= 4 && segments[0] == ".agents" && segments[1] == "skills":
		return prefixed(segments[2])
	case len(segments) == 3 && segments[0] == ".claude" && segments[1] == "skills":
		return prefixed(segments[2])
	case len(segments) == 3 && segments[0] == ".claude" && segments[1] == "agents":
		return prefixed(segments[2]) && strings.HasSuffix(segments[2], ".md")
	}
	for _, role := range namespaceRoles {
		if strings.HasPrefix(path, ".agents/"+role+"/"+pack+"/") {
			return true
		}
	}
	return false
}

// IsAllowedPath reports whether Kaya delivers path: on the allow-list, no empty, "." or ".."
// segment, no backslash, at most 500 characters.
func IsAllowedPath(path string) bool {
	if path == "" || len(path) > 500 || strings.Contains(path, `\`) || !allowedPath.MatchString(path) {
		return false
	}
	for _, segment := range strings.Split(path, "/") {
		if segment == "" || segment == "." || segment == ".." {
			return false
		}
	}
	return true
}

// IsAllowedDir reports whether a delivered path can lie below dir: any file there, or a Markdown
// file for the convention docs.
func IsAllowedDir(dir string) bool {
	return IsAllowedPath(dir+"/x") || IsAllowedPath(dir+"/x.md")
}

// Reasons of a path the seed does not import. The server keeps them in the report as given.
const (
	ReasonNotDelivered  = "Not a path Kaya delivers"
	ReasonAdapterLink   = "Adapter link into .agents/; Kaya delivers the files it points to"
	ReasonLinkElsewhere = "Link to a path Kaya does not deliver"
	ReasonLessons       = "Lessons stay in git"
	ReasonAgentDocs     = "Agent docs stay in git"
	ReasonManagedState  = "Sync state of korurlabs/agents; Kaya replaces it"
	ReasonKayaState     = "Written by the Kaya CLI"
	ReasonLocalSettings = "Personal settings are not shared"
	ReasonWorktrees     = "Git worktrees are checkouts, not config"
	ReasonTooLarge      = "Over the 5 MiB blob limit"
	ReasonNotRegular    = "Not a regular file or symlink"
	ReasonNotInPacks    = "Managed by korurlabs/agents, but none of the repository's packs holds it"
	ReasonPackExtra     = "Not a pack path the sync delivers"
	ReasonAgentsRepo    = "Tooling or docs of korurlabs/agents, not config"
	// ReasonNotConventionDoc is a file under ConventionsDir that is not Markdown.
	ReasonNotConventionDoc = "Only Markdown convention docs are delivered"
	// ReasonNestedAgentFiles is an agent file below a mount folder other than <folder>/AGENTS.md:
	// a nested AGENTS.md or CLAUDE.md, or a file of a .claude/ or .agents/ directory there.
	ReasonNestedAgentFiles = "Nested agent files stay in git"
	// ReasonDeprecatedPack is a pack of korurlabs/agents Kaya no longer imports, and each managed
	// file of a repository that only such a pack holds.
	ReasonDeprecatedPack = "Deprecated pack"
)

// collapsedDirs are reported as one entry and never walked: they are large and hold no config.
var collapsedDirs = map[string]string{
	".claude/worktrees": ReasonWorktrees,
}

// reasonFor is why a path off the allow-list is not imported. target is the link target of a
// symlink, empty otherwise.
func reasonFor(path, target string, symlink bool) string {
	switch {
	case path == ".agents/.managed.json":
		return ReasonManagedState
	case path == ".agents/.kaya-release.json" || strings.HasPrefix(path, ".kaya/"):
		return ReasonKayaState
	case path == ".claude/settings.local.json":
		return ReasonLocalSettings
	case strings.HasPrefix(path, ".agents/memory/"):
		return ReasonLessons
	case strings.HasPrefix(path, ".agents/docs/"):
		return ReasonAgentDocs
	case symlink && pointsIntoDelivered(path, target):
		return ReasonAdapterLink
	case symlink:
		return ReasonLinkElsewhere
	case strings.HasPrefix(path, ConventionsDir+"/"):
		return ReasonNotConventionDoc
	default:
		return ReasonNotDelivered
	}
}

// pointsIntoDelivered reports whether the link at path resolves to a delivered path or into a
// delivered directory, as .claude/rules -> ../.agents/rules does.
func pointsIntoDelivered(path, target string) bool {
	if target == "" || strings.HasPrefix(target, "/") {
		return false
	}
	resolved := pathpkg.Join(pathpkg.Dir(path), target)
	return IsAllowedPath(resolved) || IsAllowedDir(resolved)
}

// packLayout says which files of a pack directory the sync delivers.
type packLayout int

const (
	// layoutFiles delivers only the files directly inside the directory.
	layoutFiles packLayout = iota
	// layoutMarkdown delivers only the <name>.md files directly inside the directory.
	layoutMarkdown
	// layoutHooks delivers the files directly inside the directory except README.md, which
	// documents the source directory; subdirectories such as the hook tests stay in the source.
	layoutHooks
	// layoutTrees delivers one directory per skill or plugin with everything below it.
	layoutTrees
)

// packTarget is the consumer directory a pack directory is written to, and its layout.
type packTarget struct {
	dir    string
	layout packLayout
}

// packTargets maps a directory of a pack in korurlabs/agents to the consumer directory the sync
// writes it to, as PACK_LAYOUT of tools/sync.py there does. A skill also gets a link under
// .claude/skills/.
var packTargets = map[string]packTarget{
	"rules":     {".agents/rules/", layoutFiles},
	"agents":    {".claude/agents/", layoutMarkdown},
	"roles":     {".agents/roles/", layoutMarkdown},
	"workflows": {".agents/workflows/", layoutMarkdown},
	"templates": {".agents/templates/", layoutFiles},
	"hooks":     {".agents/hooks/", layoutHooks},
	"skills":    {".agents/skills/", layoutTrees},
	"plugins":   {".claude/skills/", layoutTrees},
}

// ignoredPackFiles and ignoredPackDirs are never synced, wherever they sit in a pack: placeholders
// that keep an empty directory in git, and local litter.
var (
	ignoredPackFiles = map[string]bool{".gitkeep": true, ".DS_Store": true}
	ignoredPackDirs  = map[string]bool{"__pycache__": true}
)

// hookFilesNotSynced stay in the hooks directory of the source.
var hookFilesNotSynced = map[string]bool{"README.md": true}

// RepositoryName turns a git remote into the repository's name, also the name of its own pack
// when it uses no family pack: the repository name, lower case, with every character outside
// [a-z0-9-] replaced by '-', at most 60 characters as every pack name.
func RepositoryName(remoteURL string) string {
	name := strings.TrimSpace(remoteURL)
	name = strings.TrimSuffix(strings.TrimRight(name, "/"), ".git")
	if i := strings.LastIndexAny(name, "/:"); i >= 0 {
		name = name[i+1:]
	}
	var b strings.Builder
	for _, r := range strings.ToLower(name) {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') || r == '-' {
			b.WriteRune(r)
		} else {
			b.WriteRune('-')
		}
	}
	out := strings.Trim(b.String(), "-")
	if len(out) > maxPackName {
		out = strings.Trim(out[:maxPackName], "-")
	}
	return out
}
