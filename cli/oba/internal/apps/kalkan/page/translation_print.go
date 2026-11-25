package page

import (
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PrintPage writes a header line for the page, then every translation under a header of its own:
// locale, title, last change and who made it, then the Markdown body.
func PrintPage(out io.Writer, page client.KalkanPageDetail) {
	compliance := ""
	if page.Compliance {
		compliance = ", compliance document"
	}
	_, _ = fmt.Fprintf(out, "%s (version %d, your level %s%s)\n", page.Path, page.Version,
		guardrail.OneLine(page.Level), compliance)
	for _, translation := range page.Translations {
		_, _ = fmt.Fprintf(out, "\n== %s: %s (updated %s by %s)\n\n", translation.Locale,
			guardrail.OneLine(translation.Title), translation.UpdatedAt, guardrail.OneLine(translation.UpdatedByName))
		_, _ = fmt.Fprint(out, withTrailingNewline(translation.Body))
	}
}

// PrintBody writes only the Markdown body of the page in locale, as it is stored and ending in a
// newline, so that it can be redirected to a file. A page without that translation is an error.
func PrintBody(out io.Writer, page client.KalkanPageDetail, locale string) error {
	for _, translation := range page.Translations {
		if translation.Locale == locale {
			_, _ = fmt.Fprint(out, withTrailingNewline(translation.Body))
			return nil
		}
	}
	return fmt.Errorf("the page %s has no %s translation", page.Path, strings.ToLower(locale))
}

func withTrailingNewline(body string) string {
	if body == "" || strings.HasSuffix(body, "\n") {
		return body
	}
	return body + "\n"
}
