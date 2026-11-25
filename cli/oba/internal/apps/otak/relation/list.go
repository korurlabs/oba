package relation

import (
	"fmt"
	"io"
	"net/url"
	"strconv"
	"strings"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const (
	fieldSearch      = "search"
	fieldHasContacts = "hasContacts"
	fieldEventID     = "eventId"
	fieldCampaignID  = "campaignId"

	// typeLead and typeCustomer are the relation types that have a pipeline stage; a supplier
	// has none, so a stage filter never applies to it.
	typeLead     = "LEAD"
	typeCustomer = "CUSTOMER"
	typeSupplier = "SUPPLIER"

	emptyCell = "-"
)

// ListQuery narrows a relation list. An empty Type lists every type the API knows; Stages
// narrow leads and customers; Search is a part of the name, case ignored; HasContacts, when
// set, keeps only the relations with (true) or without (false) a contact; EventID and CampaignID
// keep only the relations linked to that event or campaign.
type ListQuery struct {
	Type        string
	Stages      []string
	Search      string
	HasContacts *bool
	EventID     string
	CampaignID  string
}

// ListItem is the part of one GET /otak/relations item (RelationListResponse) that the relation
// list shows.
type ListItem struct {
	ID      string     `json:"id"`
	Name    string     `json:"name"`
	Type    string     `json:"type"`
	Website *string    `json:"website"`
	Stage   *string    `json:"stage"`
	Owner   *ListOwner `json:"owner"`

	// MainPersonContactName and MainPersonContactEmail are the name and e-mail of the relation's
	// main person contact, nil when it has no person contact or that contact has no e-mail.
	MainPersonContactName  *string `json:"mainPersonContactName"`
	MainPersonContactEmail *string `json:"mainPersonContactEmail"`

	// MainBusinessContactEmail is the e-mail of the relation's main business contact (such as
	// info@), nil when it has no business contact or that contact has no e-mail.
	MainBusinessContactEmail *string `json:"mainBusinessContactEmail"`
}

// ListOwner is the owner of a listed relation, by name.
type ListOwner struct {
	Name string `json:"name"`
}

// List reads every relation that matches q, grouped by type in the order of the API's type
// enum. The API needs a type on every call, so it makes one paged series of calls per type.
// Leads and customers are read with every stage named when q names none, because the API
// leaves lost and parked leads out when no stage is given. A failed call returns no rows, so
// that a caller never shows a partial list as if it were complete.
func List(c *client.Client, q ListQuery) ([]ListItem, error) {
	fields, err := loadFields(c.BaseURL, CreateFields)
	if err != nil {
		return nil, err
	}
	types := enumOf(fields, fieldType)
	allStages := enumOf(fields, fieldStage)
	if len(types) == 0 || len(allStages) == 0 {
		return nil, apidocs.FieldListError(c.BaseURL, fmt.Errorf("schema %q lists no types or no stages", createSchema))
	}

	series, err := listTypes(q.Type, types, len(q.Stages) > 0)
	if err != nil {
		return nil, err
	}
	stages, err := matchEnum(fieldStage, q.Stages, allStages)
	if err != nil {
		return nil, err
	}
	if len(stages) == 0 {
		stages = allStages
	}
	search := strings.TrimSpace(q.Search)
	eventID := strings.TrimSpace(q.EventID)
	campaignID := strings.TrimSpace(q.CampaignID)

	var all []ListItem
	for _, relationType := range series {
		query := url.Values{fieldType: {relationType}}
		if hasStage(relationType) {
			query[fieldStage] = stages
		}
		if search != "" {
			query.Set(fieldSearch, search)
		}
		if eventID != "" {
			query.Set(fieldEventID, eventID)
		}
		if campaignID != "" {
			query.Set(fieldCampaignID, campaignID)
		}
		if q.HasContacts != nil {
			query.Set(fieldHasContacts, strconv.FormatBool(*q.HasContacts))
		}
		items, err := client.ListPages[ListItem](c, relationsPath, query)
		if err != nil {
			return nil, fmt.Errorf("failed to read the %s relations: %w", strings.ToLower(relationType), err)
		}
		all = append(all, items...)
	}
	return all, nil
}

// listTypes returns the types to read: the given one, or every type of the enum in its order.
// With a stage filter a type without stages is skipped, or refused when it was asked for.
func listTypes(given string, types []string, withStages bool) ([]string, error) {
	if strings.TrimSpace(given) == "" {
		series := make([]string, 0, len(types))
		for _, relationType := range types {
			if withStages && !hasStage(relationType) {
				continue
			}
			series = append(series, relationType)
		}
		return series, nil
	}
	matched, err := matchEnum(fieldType, []string{given}, types)
	if err != nil {
		return nil, err
	}
	relationType := matched[0]
	if withStages && !hasStage(relationType) {
		if relationType == typeSupplier {
			return nil, fmt.Errorf("--stage does not apply to suppliers; they have no stage")
		}
		return nil, fmt.Errorf("--stage does not apply to %s relations; they have no stage", strings.ToLower(relationType))
	}
	return []string{relationType}, nil
}

func hasStage(relationType string) bool {
	return relationType == typeLead || relationType == typeCustomer
}

// matchEnum maps each value to the enum value it names, ignoring case as the API binds enums,
// so that the API receives the spelling it documents. A value the enum lacks is refused before
// any call, with the allowed values.
func matchEnum(name string, values, allowed []string) ([]string, error) {
	matched := make([]string, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(value)
		found := ""
		for _, candidate := range allowed {
			if strings.EqualFold(value, candidate) {
				found = candidate
				break
			}
		}
		if found == "" {
			return nil, fmt.Errorf("unknown %s %q; allowed: %s", name, value, strings.Join(allowed, ", "))
		}
		matched = append(matched, found)
	}
	return matched, nil
}

// enumOf returns the allowed values of the named field, or nil when the field is missing.
func enumOf(fields []apidocs.Field, name string) []string {
	for _, field := range fields {
		if field.Name == name {
			return field.Enum
		}
	}
	return nil
}

// PrintList prints the relations as one table: the main person contact's name and e-mail under
// CONTACT and CONTACT MAIL, the main business contact's e-mail under COMPANY MAIL. Every cell is
// sanitized onto one line, since names, websites and addresses are typed by users or come from
// the web.
func PrintList(out io.Writer, items []ListItem) error {
	if len(items) == 0 {
		_, _ = fmt.Fprintln(out, "No relations.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tTYPE\tSTAGE\tWEBSITE\tOWNER\tCONTACT\tCONTACT MAIL\tCOMPANY MAIL\tID")
	for _, item := range items {
		owner := ""
		if item.Owner != nil {
			owner = item.Owner.Name
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", cell(item.Name), cell(item.Type), optionalCell(item.Stage),
			optionalCell(item.Website), cell(owner), optionalCell(item.MainPersonContactName),
			optionalCell(item.MainPersonContactEmail), optionalCell(item.MainBusinessContactEmail), cell(item.ID))
	}
	return w.Flush()
}

func optionalCell(value *string) string {
	if value == nil {
		return emptyCell
	}
	return cell(*value)
}

// cell is value on one line with every terminal control removed, or "-" when nothing is left.
func cell(value string) string {
	clean := guardrail.OneLine(value)
	if clean == "" {
		return emptyCell
	}
	return clean
}
