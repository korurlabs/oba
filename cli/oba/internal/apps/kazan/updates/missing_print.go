package updates

import (
	"fmt"
	"io"
	"text/tabwriter"
	"time"

	// The embedded zone database loads Europe/Istanbul on a machine without one, such as a
	// minimal container or Windows.
	_ "time/tzdata"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

const (
	// ReportZoneName is the zone of the posting rule: the day runs from 00:00 Istanbul time and
	// an update is due by 10:30, in every organization.
	ReportZoneName = "Europe/Istanbul"

	reportClockLayout = "15:04"
)

// reportZone loads the zone the server applies the posting rule in, by its IANA name and never
// as a fixed offset, so that a change of the zone's rules reaches the CLI with the embedded zone
// database.
func reportZone() (*time.Location, error) {
	loc, err := time.LoadLocation(ReportZoneName)
	if err != nil {
		return nil, fmt.Errorf("failed to load the report zone %s: %w", ReportZoneName, err)
	}
	return loc, nil
}

// reportClock renders an API instant as HH:MM Istanbul time, or as given when it does not parse.
func reportClock(instant string, zone *time.Location) string {
	t, err := time.Parse(time.RFC3339Nano, instant)
	if err != nil {
		return cell(instant)
	}
	return t.In(zone).Format(reportClockLayout)
}

// missingStatus is "late HH:MM" for a member who posted after the deadline, else "missing" once
// the deadline has passed and "pending" before.
func missingStatus(m client.DailyUpdateMissingMember, deadlinePassed bool, zone *time.Location) string {
	switch {
	case m.PostedAt != nil:
		return "late " + reportClock(*m.PostedAt, zone)
	case deadlinePassed:
		return "missing"
	default:
		return "pending"
	}
}

// PrintMissing prints who has not posted on the report's day: a header with the day, the
// deadline and the count, then NAME, EMAIL and STATUS per member.
func PrintMissing(out io.Writer, report client.DailyUpdateMissing) error {
	if !report.Workday {
		_, _ = fmt.Fprintln(out, "No update is expected on weekends.")
		return nil
	}
	zone, err := reportZone()
	if err != nil {
		return err
	}
	day := cell(report.Date)
	if d, err := time.Parse(time.DateOnly, report.Date); err == nil {
		day = d.Weekday().String() + " " + report.Date
	}
	verb := "have not posted"
	if !report.DeadlinePassed {
		verb += " yet"
	}
	_, _ = fmt.Fprintf(out, "%s, deadline %s %s: %d of %d %s\n", day, reportClock(report.Deadline, zone),
		ReportZoneName, len(report.Members), report.ExpectedCount, verb)

	if len(report.Members) == 0 {
		_, _ = fmt.Fprintln(out, "Everyone has posted.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tEMAIL\tSTATUS")
	for _, m := range report.Members {
		name := guardrail.PersonName(m.FirstName, m.LastName, "-")
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\n", name, cell(m.Email), missingStatus(m, report.DeadlinePassed, zone))
	}
	if err := w.Flush(); err != nil {
		return fmt.Errorf("failed to print the members: %w", err)
	}
	return nil
}
