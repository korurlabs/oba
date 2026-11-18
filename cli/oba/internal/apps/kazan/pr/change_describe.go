package pr

import (
	"fmt"
	"strconv"
	"strings"
)

// DescribeChanges lists what an edit changes, one line each: status, note, reviewers added and
// removed, related tickets added and removed. An empty list means no change.
func DescribeChanges(before, after Edit) []string {
	var lines []string
	if before.Status != after.Status {
		lines = append(lines, fmt.Sprintf("Status: %s -> %s", StatusLabel(before.Status), StatusLabel(after.Status)))
	}
	if noteText(before.Note) != noteText(after.Note) {
		lines = append(lines, fmt.Sprintf("Note: %s -> %s", noteText(before.Note), noteText(after.Note)))
	}

	added, removed, reordered := listChanges(before.ReviewerIDs, after.ReviewerIDs, before.Names, after.Names)
	if len(added) > 0 {
		lines = append(lines, "Reviewers added: "+strings.Join(added, ", "))
	}
	if len(removed) > 0 {
		lines = append(lines, "Reviewers removed: "+strings.Join(removed, ", ")+" (their approval is dropped)")
	}
	if reordered {
		lines = append(lines, "Reviewers reordered")
	}

	added, removed, reordered = listChanges(before.TicketIDs, after.TicketIDs, before.TicketLabels, after.TicketLabels)
	if len(added) > 0 {
		lines = append(lines, "Tickets added: "+strings.Join(added, ", "))
	}
	if len(removed) > 0 {
		lines = append(lines, "Tickets removed: "+strings.Join(removed, ", "))
	}
	if reordered {
		lines = append(lines, "Tickets reordered")
	}
	return lines
}

// noteText renders a note for the change list.
func noteText(note *string) string {
	if note == nil {
		return "none"
	}
	return strconv.Quote(*note)
}

// listChanges compares two id lists in display order: the names of the ids added and removed,
// and whether the same ids only changed order.
func listChanges(before, after []string, beforeNames, afterNames map[string]string) (added, removed []string, reordered bool) {
	for _, id := range after {
		if indexOfID(before, id) < 0 {
			added = append(added, afterNames[strings.ToLower(id)])
		}
	}
	for _, id := range before {
		if indexOfID(after, id) < 0 {
			removed = append(removed, beforeNames[strings.ToLower(id)])
		}
	}
	reordered = len(added) == 0 && len(removed) == 0 && !strings.EqualFold(strings.Join(before, ","), strings.Join(after, ","))
	return added, removed, reordered
}
