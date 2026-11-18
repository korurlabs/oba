package template

import (
	"fmt"
	"io"
	"net/url"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/apps/otak/docfile"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ListQuery is the filter of a template list; an empty value does not filter.
type ListQuery struct {
	Type     string
	Language string
}

// List reads every template of the organization that matches the query.
func List(c *client.Client, query ListQuery) ([]client.DocumentTemplate, error) {
	q := url.Values{}
	if query.Type != "" {
		t, err := docfile.Type(query.Type)
		if err != nil {
			return nil, err
		}
		q.Set("type", t)
	}
	if query.Language != "" {
		lang, err := docfile.Language(query.Language)
		if err != nil {
			return nil, err
		}
		q.Set("language", lang)
	}
	return c.ListTemplates(q)
}

// PrintList writes the templates as one table: name, type, language, whether it can be used
// (READY), last change and id.
func PrintList(out io.Writer, templates []client.DocumentTemplate) error {
	if len(templates) == 0 {
		_, _ = fmt.Fprintln(out, "No templates.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tTYPE\tLANGUAGE\tREADY\tUPDATED\tID")
	for _, t := range templates {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\n", orDash(t.Name), orDash(t.Type), orDash(t.Language),
			yesNo(t.Ready), orDash(t.UpdatedAt), orDash(t.ID))
	}
	return w.Flush()
}

func yesNo(b bool) string {
	if b {
		return "yes"
	}
	return "no"
}
