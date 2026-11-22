package mailtemplate

import (
	"fmt"
	"io"
	"net/url"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// ListQuery is the filter of a mail template list; an empty value does not filter.
type ListQuery struct {
	Language string
	Search   string
}

// List reads every mail template of the organization that matches the query.
func List(c *client.Client, query ListQuery) ([]client.MailTemplate, error) {
	q := url.Values{}
	if query.Language != "" {
		lang, err := docfile.Language(query.Language)
		if err != nil {
			return nil, err
		}
		q.Set("language", lang)
	}
	if search := strings.TrimSpace(query.Search); search != "" {
		q.Set("search", search)
	}
	return c.ListMailTemplates(q)
}

// PrintList writes the mail templates as one table: name, language, subject, last change and id.
func PrintList(out io.Writer, templates []client.MailTemplate) error {
	if len(templates) == 0 {
		_, _ = fmt.Fprintln(out, "No mail templates.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tLANGUAGE\tSUBJECT\tUPDATED\tID")
	for _, t := range templates {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\n", orDash(t.Name), orDash(t.Language), orDash(t.Subject),
			orDash(t.UpdatedAt), orDash(t.ID))
	}
	return w.Flush()
}
