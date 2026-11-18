package roadmap

import (
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
)

// span returns the days the timeline spans, each at local midnight. A bound the roadmap carries
// is used as given; a missing one is taken from the milestones and widened to a whole month. ok
// is false when a bound is missing and there are no milestones to derive it from.
func span(r client.Roadmap) (start time.Time, end time.Time, ok bool) {
	var minDate, maxDate time.Time
	found := false
	for _, b := range r.Boards {
		for _, m := range b.Milestones {
			target, err := dates.ParseDate(m.TargetDate)
			if err != nil {
				continue
			}
			first := target
			if m.StartDate != nil {
				if s, sErr := dates.ParseDate(*m.StartDate); sErr == nil && s.Before(target) {
					first = s
				}
			}
			if !found || first.Before(minDate) {
				minDate = first
			}
			if !found || target.After(maxDate) {
				maxDate = target
			}
			found = true
		}
	}

	startSet, endSet := false, false
	if r.From != nil {
		if d, err := dates.ParseDate(*r.From); err == nil {
			start, startSet = d, true
		}
	}
	if r.To != nil {
		if d, err := dates.ParseDate(*r.To); err == nil {
			end, endSet = d, true
		}
	}

	if (!startSet || !endSet) && !found {
		return time.Time{}, time.Time{}, false
	}
	if !startSet {
		start = time.Date(minDate.Year(), minDate.Month(), 1, 0, 0, 0, 0, minDate.Location())
	}
	if !endSet {
		end = time.Date(maxDate.Year(), maxDate.Month()+1, 0, 0, 0, 0, 0, maxDate.Location())
	}
	if end.Before(start) {
		end = start
	}
	return start, end, true
}
