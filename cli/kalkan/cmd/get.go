package cmd

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kalkan-cli/internal/client"
)

var getLang string

var getCmd = &cobra.Command{
	Use:   "get <path>",
	Short: "Print a page",
	Long: "Prints the page at <path>. With --lang, prints only the Markdown body in that language, " +
		"ready to redirect to a file; without it, prints every translation under a header. With " +
		"--json, prints the page with its translations (only the one of --lang when given).",
	Args: cobra.ExactArgs(1),
	RunE: runGet,
}

func init() {
	getCmd.Flags().StringVar(&getLang, "lang", "", "language of the body to print: tr or en")
	rootCmd.AddCommand(getCmd)
}

func runGet(cmd *cobra.Command, args []string) error {
	path, err := normalizePath(args[0])
	if err != nil {
		return fail(cmd, err)
	}
	locale := ""
	if getLang != "" {
		if locale, err = parseLocale(getLang); err != nil {
			return fail(cmd, err)
		}
	}
	api, _, err := signedInClient()
	if err != nil {
		return fail(cmd, err)
	}
	item, err := findPage(cmd.Context(), api, path)
	if err != nil {
		return fail(cmd, err)
	}
	page, err := api.GetPage(cmd.Context(), item.ID)
	if err != nil {
		return fail(cmd, err)
	}
	if locale != "" {
		translation, ok := findTranslation(page.Translations, locale)
		if !ok {
			return fail(cmd, fmt.Errorf("the page %s has no %s translation", path, strings.ToLower(locale)))
		}
		page.Translations = []client.PageTranslation{translation}
	}
	if jsonOutput {
		if err := printJSON(cmd, page); err != nil {
			return fail(cmd, err)
		}
		return nil
	}
	out := cmd.OutOrStdout()
	if locale != "" {
		_, _ = fmt.Fprint(out, withTrailingNewline(page.Translations[0].Body))
		return nil
	}
	compliance := ""
	if page.Compliance {
		compliance = ", compliance document"
	}
	_, _ = fmt.Fprintf(out, "%s (version %d, your level %s%s)\n", page.Path, page.Version, page.Level, compliance)
	for _, translation := range page.Translations {
		_, _ = fmt.Fprintf(out, "\n== %s: %s (updated %s by %s)\n\n", translation.Locale, translation.Title,
			translation.UpdatedAt, translation.UpdatedByName)
		_, _ = fmt.Fprint(out, withTrailingNewline(translation.Body))
	}
	return nil
}

// parseLocale turns tr or en, in any case, into the API's locale name.
func parseLocale(lang string) (string, error) {
	switch strings.ToUpper(strings.TrimSpace(lang)) {
	case client.LocaleTR:
		return client.LocaleTR, nil
	case client.LocaleEN:
		return client.LocaleEN, nil
	default:
		return "", fmt.Errorf("unknown language %q; use tr or en", lang)
	}
}

func findTranslation(translations []client.PageTranslation, locale string) (client.PageTranslation, bool) {
	for _, translation := range translations {
		if translation.Locale == locale {
			return translation, true
		}
	}
	return client.PageTranslation{}, false
}

func withTrailingNewline(body string) string {
	if body == "" || strings.HasSuffix(body, "\n") {
		return body
	}
	return body + "\n"
}
