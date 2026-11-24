// Package seed builds oba kaya seed, which imports an organization's agent config into Kaya from
// a clone of its agent config repository and the checkouts of its repositories.
package seed

import (
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/seed"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

// Exit codes of oba kaya seed beyond 0 (passed) and 1 (an error before a report).
const (
	exitSeedFailed = 2
)

type seedFlags struct {
	agents       string
	repos        []string
	mounts       []string
	parents      []string
	repoPacks    []string
	sourceRef    string
	afterCutover bool
	dryRun       bool
	layout       string
}

var seedOpts seedFlags

// NewCmd builds the oba kaya seed command.
func NewCmd() *cobra.Command {
	seedOpts = seedFlags{}
	var seedCmd = &cobra.Command{
		Use: "seed --repo <checkout> [--repo <checkout>...] [--agents <clone of your agent config repository>] " +
			"[--pack <name>=<folder>...] [--pack-parent <child>=<parent>...] [--repo-pack [<repository>=]<pack>...] " +
			"[--layout <seed tree>/layout.yaml]",
		Short: "Import today's agent config from the repositories into Kaya (OWNER)",
		Long: `Reads every pack below packs/ of a clone of your agent config repository and, per
repository, its packs from .agents/.managed.json and its own files at the paths Kaya delivers
(AGENTS.md, .agents/AGENTS.md, .agents/{rules,skills,roles,workflows,templates,hooks}/,
.claude/{agents,skills}/, .claude/settings.json), with their file modes and symlinks. The tool
links it delivers are the root AGENTS.md, .claude/rules and .claude/templates. It uploads the
blobs the server is missing, posts the seed run, prints its report and compares each
repository's release with the repository on disk.

Packs form a tree: org is the root and every other pack of the clone is its child, unless
--pack-parent <child>=<parent> names another parent. A stored pack keeps its parent. A pack the
CLI knows as deprecated is reported, not imported.

A repository's own files go into its family pack, the pack it uses at its root: the last pack of
its .agents/.managed.json other than org, or a pack named after the repository when it has none.
--repo-pack <pack> chooses it for every repository, --repo-pack <repository>=<pack> for one.

--pack <name>=<folder> mounts a pack in a folder of a repository: it takes the files of its
namespace (.agents/rules/<name>/, .agents/skills/<name>-*, .claude/agents/<name>-*.md,
docs/private/conventions/projects/<name>/ and the like) and <folder>/AGENTS.md, stored as the
pack's AGENTS.md. A mounted pack outside the clone is a child of the repository's family pack.
Other agent files below the folder stay in git and are reported as not imported.

Without --agents the packs the server already holds are kept and only packs outside the clone
are seeded. A repository without .agents/.managed.json has been cut over to Kaya and is refused
unless --after-cutover is passed; it also needs --agents, its stored pack list is kept and
--repo-pack names its family pack.

--layout <file> builds the packs from a seed tree instead. Its layout file names the packs with
their parents, each repository's packs (at its root, or mounted in a folder), the ordered rules
that place the files of the agents clone (agents:<path in the clone>) and of each repository
(<repository>:<path>) in a pack, and the files left out:

  packs:
    - name: base
    - {name: web, parent: base}
    - {name: tool, parent: base}
  repositories:
    - name: app-one
      packs: [web, {name: tool, folder: tools/tool}]
  rules:
    - pack: base
      paths: ["agents:packs/core/"]
    - pack: web
      paths: ["app-one:.agents/**", "app-one:.claude/settings.json"]
  leftOut: ["app-one:.agents/hooks/old.sh"]

The first rule that matches places a file; "**" spans folders and a trailing "/" takes all below.
A file no rule and no leftOut entry matches fails the seed, all of them named. A skill's link
goes to the pack of its skill, and a mounted folder's AGENTS.md to the mounted pack.
content/<pack>/<path> beside the layout file replaces or adds that path in that pack. A
repository is read with or without .agents/.managed.json, and --pack, --pack-parent,
--repo-pack and --after-cutover do not apply. A checkout Kaya delivers to is refused: its
delivered files are links into its .kaya/ folder, Kaya's own output, so seed from a checkout
Kaya does not deliver to, or leave the repository out. A pack is sent only when the run reads every
source it takes files from, and a repository the run does not read keeps its stored pack list,
so a later run can add packs and repositories while Kaya leaves equal packs as they are. The
release comparison lists the content files and the files left out as changed on purpose.

Exit status: 0 when the run PASSED and every release equals its repository on disk; 2 when the
run FAILED or a release differs; 1 on any other error.`,
		Args: cobra.NoArgs,
		RunE: runSeed,
	}

	flags := seedCmd.Flags()
	flags.StringVar(&seedOpts.agents, "agents", "", "clone of your agent config repository, whose packs/ are imported")
	flags.StringArrayVar(&seedOpts.repos, "repo", nil, "checkout of a consumer repository (repeatable)")
	flags.StringArrayVar(&seedOpts.mounts, "pack", nil, "pack mounted in a folder of a repository, <name>=<folder> (repeatable)")
	flags.StringArrayVar(&seedOpts.parents, "pack-parent", nil, "parent of a seeded pack, <child>=<parent> (repeatable)")
	flags.StringArrayVar(&seedOpts.repoPacks, "repo-pack", nil, "family pack that takes a repository's own files, [<repository>=]<pack> (repeatable)")
	flags.StringVar(&seedOpts.sourceRef, "source-ref", "", "commit recorded for the run (default: HEAD of the agents clone, else of the first repository)")
	flags.BoolVar(&seedOpts.afterCutover, "after-cutover", false, "seed repositories that were already cut over to Kaya (needs --agents)")
	flags.BoolVar(&seedOpts.dryRun, "dry-run", false, "print what would be sent, without signing in or calling Kaya")
	flags.StringVar(&seedOpts.layout, "layout", "", "layout file of a seed tree whose packs, rules and content/ folder replace the clone's packs")
	_ = seedCmd.MarkFlagRequired("repo")
	return seedCmd
}

func runSeed(cmd *cobra.Command, _ []string) error {
	out := cmd.OutOrStdout()
	opts, err := seedOptions()
	if err != nil {
		return err
	}
	plan, err := seed.Build(opts)
	if err != nil {
		return err
	}
	if seedOpts.dryRun {
		if jsonout.Enabled() {
			return jsonresult.SetValue(plan.Request)
		}
		seed.PrintPlan(out, plan)
		return nil
	}

	api, err := client.ForOrganization()
	if err != nil {
		return err
	}
	run, uploaded, err := seed.Send(cmd.Context(), api, plan)
	if err != nil {
		return err
	}
	checks, err := seed.Check(cmd.Context(), api, plan, run)
	if err != nil {
		return err
	}
	if jsonout.Enabled() {
		if err := jsonresult.SetValue(run); err != nil {
			return err
		}
	} else {
		_, _ = fmt.Fprintf(out, "Uploaded %d blobs.\n", uploaded)
		seed.PrintRun(out, run, plan, checks)
	}
	if !seed.Passed(run, checks) {
		// The run is the report a script reads, so --json still prints it.
		jsonout.KeepOnFailure()
		return &exitcode.Error{Code: exitSeedFailed}
	}
	return nil
}

// seedOptions reads the seed flags into the options of seed.Build.
func seedOptions() (seed.Options, error) {
	opts := seed.Options{
		AgentsDir:    seedOpts.agents,
		RepoDirs:     seedOpts.repos,
		SourceRef:    seedOpts.sourceRef,
		AfterCutover: seedOpts.afterCutover,
		Layout:       seedOpts.layout,
		Parents:      map[string]string{},
		RepoPacks:    map[string]string{},
	}
	for _, value := range seedOpts.mounts {
		mount, err := seed.ParseMount(value)
		if err != nil {
			return seed.Options{}, err
		}
		opts.Mounts = append(opts.Mounts, mount)
	}
	for _, value := range seedOpts.parents {
		child, parent, err := seed.ParsePackParent(value)
		if err != nil {
			return seed.Options{}, err
		}
		if other, taken := opts.Parents[child]; taken && other != parent {
			return seed.Options{}, fmt.Errorf("--pack-parent names two parents of %s: %s and %s", child, other, parent)
		}
		opts.Parents[child] = parent
	}
	for _, value := range seedOpts.repoPacks {
		repository, pack, err := seed.ParseRepoPack(value)
		if err != nil {
			return seed.Options{}, err
		}
		if other, taken := opts.RepoPacks[repository]; taken && other != pack {
			return seed.Options{}, fmt.Errorf("--repo-pack names two packs for %q: %s and %s", repository, other, pack)
		}
		opts.RepoPacks[repository] = pack
	}
	return opts, nil
}
