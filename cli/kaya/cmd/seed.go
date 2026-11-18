package cmd

import (
	"encoding/json"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/seed"
)

// Exit codes of kaya seed beyond 0 (passed) and 1 (an error before a report).
const (
	exitSeedFailed = 2
)

var seedOpts struct {
	agents       string
	repos        []string
	mounts       []string
	parents      []string
	repoPacks    []string
	sourceRef    string
	afterCutover bool
	dryRun       bool
	json         bool
}

var seedCmd = &cobra.Command{
	Use: "seed --repo <checkout> [--repo <checkout>...] [--agents <clone of korurlabs/agents>] " +
		"[--pack <name>=<folder>...] [--pack-parent <child>=<parent>...] [--repo-pack [<repository>=]<pack>...]",
	Short: "Import today's agent config from the repositories into Kaya (OWNER)",
	Long: `Reads every pack of a korurlabs/agents clone and, per repository, its packs from
.agents/.managed.json and its own files at the paths Kaya delivers (AGENTS.md, .agents/AGENTS.md,
.agents/{rules,skills,roles,workflows,templates,hooks}/, .claude/{agents,skills}/,
.claude/settings.json), with their file modes and symlinks. The tool links it delivers are
the root AGENTS.md, .claude/rules and .claude/templates. It uploads the blobs the server is
missing, posts the seed run, prints its report and compares each repository's release with the
repository on disk.

Packs form a tree: org is the root and every other pack of the clone is its child, unless
--pack-parent <child>=<parent> names another parent. A stored pack keeps its parent. packs/mdf is
deprecated and is reported, not imported.

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

Exit status: 0 when the run PASSED and every release equals its repository on disk; 2 when the
run FAILED or a release differs; 1 on any other error.`,
	Args: cobra.NoArgs,
	RunE: runSeed,
}

func init() {
	flags := seedCmd.Flags()
	flags.StringVar(&seedOpts.agents, "agents", "", "clone of korurlabs/agents whose packs/ are imported")
	flags.StringArrayVar(&seedOpts.repos, "repo", nil, "checkout of a consumer repository (repeatable)")
	flags.StringArrayVar(&seedOpts.mounts, "pack", nil, "pack mounted in a folder of a repository, <name>=<folder> (repeatable)")
	flags.StringArrayVar(&seedOpts.parents, "pack-parent", nil, "parent of a seeded pack, <child>=<parent> (repeatable)")
	flags.StringArrayVar(&seedOpts.repoPacks, "repo-pack", nil, "family pack that takes a repository's own files, [<repository>=]<pack> (repeatable)")
	flags.StringVar(&seedOpts.sourceRef, "source-ref", "", "commit recorded for the run (default: HEAD of the agents clone, else of the first repository)")
	flags.BoolVar(&seedOpts.afterCutover, "after-cutover", false, "seed repositories that were already cut over to Kaya (needs --agents)")
	flags.BoolVar(&seedOpts.dryRun, "dry-run", false, "print what would be sent, without signing in or calling Kaya")
	flags.BoolVar(&seedOpts.json, "json", false, "print the seed run as JSON")
	_ = seedCmd.MarkFlagRequired("repo")
	rootCmd.AddCommand(seedCmd)
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
		if seedOpts.json {
			return printJSON(cmd, plan.Request)
		}
		seed.PrintPlan(out, plan)
		return nil
	}

	api, cfg, err := signedInClient()
	if err != nil {
		return err
	}
	if cfg.OrgID == "" {
		return fmt.Errorf("no organization chosen; pass --org <id>")
	}
	run, uploaded, err := seed.Send(cmd.Context(), api, plan)
	if err != nil {
		return err
	}
	checks, err := seed.Check(cmd.Context(), api, plan, run)
	if err != nil {
		return err
	}
	if seedOpts.json {
		if err := printJSON(cmd, run); err != nil {
			return err
		}
	} else {
		_, _ = fmt.Fprintf(out, "Uploaded %d blobs.\n", uploaded)
		seed.PrintRun(out, run, plan.Warnings, checks)
	}
	if !seed.Passed(run, checks) {
		return &ExitError{Code: exitSeedFailed}
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

func printJSON(cmd *cobra.Command, value any) error {
	encoder := json.NewEncoder(cmd.OutOrStdout())
	encoder.SetIndent("", "  ")
	return encoder.Encode(value)
}
