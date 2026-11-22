package documenttemplate

import (
	"fmt"
	"io"
	"net/url"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// ListQuery is the filter of a template list; an empty value does not filter.
type ListQuery struct {
	Type          string
	Language      string
	LegalEntityID string
	Search        string
}

// query checks the filter values and turns them into the API's query, before any call.
func (q ListQuery) query() (url.Values, error) {
	values := url.Values{}
	if q.Type != "" {
		t, err := docfile.Type(q.Type)
		if err != nil {
			return nil, err
		}
		values.Set("type", t)
	}
	if q.Language != "" {
		lang, err := docfile.Language(q.Language)
		if err != nil {
			return nil, err
		}
		values.Set("language", lang)
	}
	id, err := legalEntityID(q.LegalEntityID)
	if err != nil {
		return nil, err
	}
	if id != "" {
		values.Set("legalEntityId", id)
	}
	if q.Search != "" {
		values.Set("search", q.Search)
	}
	return values, nil
}

// Check refuses an invalid filter value without a call.
func (q ListQuery) Check() error {
	_, err := q.query()
	return err
}

// List reads every template of the organization that matches the query. With a legal entity it
// returns that legal entity's templates and those that serve every legal entity.
func List(c *client.Client, q ListQuery) ([]client.DocumentTemplate, error) {
	values, err := q.query()
	if err != nil {
		return nil, err
	}
	return c.ListDocumentTemplates(values)
}

// PrintList writes the templates as one table: name, type, language, legal entity ("all" for a
// template every legal entity may use), whether it can be used (READY), last change and id.
func PrintList(out io.Writer, templates []client.DocumentTemplate) error {
	if len(templates) == 0 {
		_, _ = fmt.Fprintln(out, "No document templates.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tTYPE\tLANGUAGE\tLEGAL ENTITY\tREADY\tUPDATED\tID")
	for _, t := range templates {
		updated := "-"
		if t.UpdatedAt != "" {
			updated = dates.FormatInstant(t.UpdatedAt)
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\t%s\n", orDash(t.Name), orDash(t.Type), orDash(t.Language),
			legalEntityCell(t), yesNo(t.Ready), updated, orDash(t.ID))
	}
	return w.Flush()
}

// legalEntityCell is the short name of the template's legal entity, "all" when it has none.
func legalEntityCell(t client.DocumentTemplate) string {
	if t.LegalEntityID == nil {
		return "all"
	}
	if t.LegalEntityShortName != nil {
		return orDash(*t.LegalEntityShortName)
	}
	return orDash(*t.LegalEntityID)
}

func yesNo(b bool) string {
	if b {
		return "yes"
	}
	return "no"
}
