package cmd

import (
	"errors"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kalkan-cli/internal/client"
)

var (
	putLang       string
	putTitle      string
	putFile       string
	putCompliance bool
	putForce      bool
)

var putCmd = &cobra.Command{
	Use:   "put <path>",
	Short: "Create or update one page by its path",
	Long: "Writes the page at <path> in one language: creates it when the path names no page, " +
		"otherwise updates that language and keeps the others. Every parent must exist; a path of " +
		"one slug creates a root page, which needs the ADMIN role. A page edited in Kalkan since " +
		"the last push is not written (exit code 3) unless --force is given. --file - reads the " +
		"body from stdin.",
	Args: cobra.ExactArgs(1),
	RunE: runPut,
}

func init() {
	flags := putCmd.Flags()
	flags.StringVar(&putLang, "lang", "en", "language of the title and body: tr or en")
	flags.StringVar(&putTitle, "title", "", "page title in that language (required)")
	flags.StringVar(&putFile, "file", "", "Markdown file of the body, - for stdin (required)")
	flags.BoolVar(&putCompliance, "compliance", false, "set the compliance mark; --compliance=false clears it; left out, the mark is kept")
	flags.BoolVar(&putForce, "force", false, "overwrite a page edited in Kalkan since the last push")
	rootCmd.AddCommand(putCmd)
}

// putResult is the JSON line of a put, in the shape of one page of a push.
type putResult struct {
	ID      string `json:"id,omitempty"`
	Path    string `json:"path"`
	Outcome string `json:"outcome"`
	Version int64  `json:"version"`
}

func runPut(cmd *cobra.Command, args []string) error {
	path, err := normalizePath(args[0])
	if err != nil {
		return fail(cmd, err)
	}
	locale, err := parseLocale(putLang)
	if err != nil {
		return fail(cmd, err)
	}
	if strings.TrimSpace(putTitle) == "" {
		return fail(cmd, errors.New("--title is required"))
	}
	if putFile == "" {
		return fail(cmd, errors.New("--file is required; use - to read the body from stdin"))
	}
	body, err := readBody(cmd.InOrStdin(), putFile)
	if err != nil {
		return fail(cmd, err)
	}
	input := client.UpsertInput{
		Path:         path,
		Translations: []client.TranslationInput{{Locale: locale, Title: putTitle, Body: body}},
		Force:        putForce,
	}
	if cmd.Flags().Changed("compliance") {
		compliance := putCompliance
		input.Compliance = &compliance
	}

	api, _, err := signedInClient()
	if err != nil {
		return fail(cmd, err)
	}
	result, err := api.UpsertPage(cmd.Context(), input, false)
	if err != nil {
		return fail(cmd, err)
	}
	outcome := strings.ToLower(result.Outcome)
	if jsonOutput {
		if err := printJSON(cmd, putResult{ID: result.ID, Path: result.Path, Outcome: outcome, Version: result.Version}); err != nil {
			return fail(cmd, err)
		}
		return nil
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "%s %s (version %d)\n", outcome, result.Path, result.Version)
	return nil
}

// readBody reads the Markdown body from a file, or from stdin when name is -.
func readBody(stdin io.Reader, name string) (string, error) {
	if name == "-" {
		data, err := io.ReadAll(stdin)
		if err != nil {
			return "", fmt.Errorf("read the body from stdin: %w", err)
		}
		return string(data), nil
	}
	data, err := os.ReadFile(name) // #nosec G304 -- the user names the file to upload
	if err != nil {
		return "", fmt.Errorf("read the body: %w", err)
	}
	return string(data), nil
}
