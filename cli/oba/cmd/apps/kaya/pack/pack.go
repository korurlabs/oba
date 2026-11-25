// Package pack builds oba kaya pack, which changes the packs of the organization as the signed-in
// user.
package pack

import (
	"errors"
	"fmt"
	"io"
	"net/http"
	"sort"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

type requiredOptions struct {
	off bool
}

var requiredOpts requiredOptions

// NewCmd builds the oba kaya pack command.
func NewCmd() *cobra.Command {
	requiredOpts = requiredOptions{}
	var packCmd = &cobra.Command{
		Use:   "pack",
		Short: "Change the packs of the organization in Kaya (OWNER)",
		Long: `A pack is a named set of agent config that repositories compose. These commands act as you, in
the organization --org names, else the chosen one, with the sign-in of oba login (or OBA_TOKEN).`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error { return cmd.Help() },
	}

	var requiredCmd = &cobra.Command{
		Use:   "required <name> [--off]",
		Short: "Make every repository of the organization compose a pack, or stop it (OWNER)",
		Long: `Marks the pack <name> required: every repository of the organization composes it, ahead of
the repository's own packs. --off marks it not required, so only the repositories that list the
pack compose it.

Kaya checks every active repository whose composition changes and releases each one, all in one
transaction. When the change breaks a rule, such as a pack below a required one overriding one of
its items, nothing changes and the error lists every violation. Marking a pack the state it
already has creates no release.

Only an OWNER of the organization may do this. It prints the pack, its new state and the number of
releases created; --json prints Kaya's answer: packId, required and releaseCount.`,
		Args: cobra.ExactArgs(1),
		RunE: runRequired,
	}

	requiredCmd.Flags().BoolVar(&requiredOpts.off, "off", false, "mark the pack not required, so only the repositories that list it compose it")
	packCmd.AddCommand(requiredCmd)
	return packCmd
}

func runRequired(cmd *cobra.Command, args []string) error {
	name := strings.TrimSpace(args[0])
	api, err := client.ForOrganization()
	if err != nil {
		return err
	}
	packs, err := api.Packs(cmd.Context())
	if err != nil {
		return err
	}
	pack, err := packNamed(packs, name)
	if err != nil {
		return err
	}
	result, err := api.SetPackRequired(cmd.Context(), pack.ID, !requiredOpts.off)
	if err != nil {
		return explainRefusal(err)
	}
	if jsonout.Enabled() {
		return jsonresult.SetValue(result)
	}
	printRequired(cmd.OutOrStdout(), pack.Name, result)
	return nil
}

// packNamed finds the pack of that name. Names are unique in an organization, so the error of an
// unknown name lists every pack the user could have meant.
func packNamed(packs []client.Pack, name string) (client.Pack, error) {
	names := make([]string, 0, len(packs))
	for _, pack := range packs {
		if pack.Name == name {
			return pack, nil
		}
		names = append(names, guardrail.OneLine(pack.Name))
	}
	if len(names) == 0 {
		return client.Pack{}, fmt.Errorf("the organization has no pack named %q: it has no packs yet", name)
	}
	sort.Strings(names)
	return client.Pack{}, fmt.Errorf("the organization has no pack named %q; its packs are: %s", name, strings.Join(names, ", "))
}

func printRequired(w io.Writer, name string, result client.PackRequired) {
	state := "not required"
	if result.Required {
		state = "required"
	}
	releases := "no release"
	switch {
	case result.ReleaseCount == 1:
		releases = "1 release"
	case result.ReleaseCount > 1:
		releases = fmt.Sprintf("%d releases", result.ReleaseCount)
	}
	_, _ = fmt.Fprintf(w, "Pack %s (%s): %s, %s created.\n", name, result.PackID, state, releases)
}

// refusal is Kaya's refusal of the change with every violation it names. The code and message
// alone say only that a composition breaks a rule; the violations name the paths and rules to fix.
type refusal struct {
	err        error
	violations []client.Violation
}

func (r *refusal) Error() string {
	var text strings.Builder
	text.WriteString(r.err.Error())
	for _, violation := range r.violations {
		line := violation.Rule + ": " + violation.Message
		if violation.Path != "" {
			line = violation.Path + ": " + line
		}
		text.WriteString("\n  " + guardrail.OneLine(line))
	}
	return text.String()
}

func (r *refusal) Unwrap() error {
	return r.err
}

// explainRefusal says why Kaya refused the change: a 403 of this call means the user is not an
// OWNER, since listing the packs before it needs only a membership, and a refusal with violations
// lists each one. Any other error is returned as it is.
func explainRefusal(err error) error {
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		return err
	}
	if apiErr.Status == http.StatusForbidden {
		return fmt.Errorf("only an OWNER of the organization may mark a pack required or not required: %w", err)
	}
	if violations := client.RefusalViolations(err); len(violations) > 0 {
		return &refusal{err: err, violations: violations}
	}
	return err
}
