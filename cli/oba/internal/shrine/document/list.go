package document

import (
	"fmt"
	"io"
	"net/url"
	"strconv"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// ListQuery is the filter of a document list; an empty value does not filter.
type ListQuery struct {
	RelationID    string
	LegalEntityID string
	Type          string
	Language      string
	Search        string
}

// query checks the filter values and turns them into the API's query, before any call.
func (q ListQuery) query() (url.Values, error) {
	values := url.Values{}
	relationID, err := optionalFlagID("--relation", q.RelationID, "oba otak relation list")
	if err != nil {
		return nil, err
	}
	legalEntityID, err := optionalFlagID("--legal-entity", q.LegalEntityID, "oba legal-entity list")
	if err != nil {
		return nil, err
	}
	if relationID != "" {
		values.Set("relationId", relationID)
	}
	if legalEntityID != "" {
		values.Set("legalEntityId", legalEntityID)
	}
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

// List reads every document of the organization that matches the query, last changed first.
func List(c *client.Client, q ListQuery) ([]client.Document, error) {
	values, err := q.query()
	if err != nil {
		return nil, err
	}
	return c.ListDocuments(values)
}

// PrintList writes the documents as one table: title, type, language, relation, legal entity,
// whether it has its files (READY), how many mails it was attached to (SENT), last change and
// id. Every cell is cut to one terminal-safe line.
func PrintList(out io.Writer, documents []client.Document) error {
	if len(documents) == 0 {
		_, _ = fmt.Fprintln(out, "No documents.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "TITLE\tTYPE\tLANGUAGE\tRELATION\tLEGAL ENTITY\tREADY\tSENT\tUPDATED\tID")
	for _, d := range documents {
		updated := "-"
		if d.UpdatedAt != "" {
			updated = dates.FormatInstant(d.UpdatedAt)
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", orDash(d.Title), orDash(d.Type), orDash(d.Language),
			orDash(deref(d.RelationName)), orDash(deref(d.LegalEntityShortName)), yesNo(d.Ready),
			strconv.Itoa(d.SnapshotCount), updated, orDash(d.ID))
	}
	return w.Flush()
}

func yesNo(b bool) string {
	if b {
		return "yes"
	}
	return "no"
}
