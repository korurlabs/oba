package updates

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ApplyLineFlags sets the percentages and notes the flags give. A flag that names a ticket not
// among the lines is an error, before any write.
func ApplyLineFlags(lines []Line, f LineFlags) error {
	for _, p := range f.percents {
		i := findLine(lines, p.ref)
		if i < 0 {
			return fmt.Errorf("--percent %s: the ticket is not on the update (tickets: %s)", p.ref, lineKeys(lines))
		}
		lines[i].Percentage = p.percentage
		lines[i].PercentSet = true
	}
	for _, n := range f.notes {
		i := findLine(lines, n.ref)
		if i < 0 {
			return fmt.Errorf("--note %s: the ticket is not on the update (tickets: %s)", n.ref, lineKeys(lines))
		}
		lines[i].Note = OptionalText(n.note)
		lines[i].NoteSet = true
	}
	return nil
}

// CheckLineFlagRefs fails before any request when --percent or --note names a ticket that
// --tickets does not list. It decides only when every --tickets entry is a key: the key of an id
// entry is known once the ticket is read, and ApplyLineFlags checks again then.
func CheckLineFlagRefs(ticketRefs []string, f LineFlags) error {
	for _, ref := range ticketRefs {
		if userinput.IsID(ref) {
			return nil
		}
	}
	for _, flagRef := range f.refs() {
		found := false
		for _, ref := range ticketRefs {
			if strings.EqualFold(strings.TrimSpace(flagRef), ref) {
				found = true
				break
			}
		}
		if !found {
			return fmt.Errorf("%s is not among --tickets %s", flagRef, strings.Join(ticketRefs, ","))
		}
	}
	return nil
}

// findLine returns the index of the line ref names, or -1.
func findLine(lines []Line, ref string) int {
	for i, l := range lines {
		if l.matches(ref) {
			return i
		}
	}
	return -1
}

// lineKeys lists the ticket keys of the lines, for error messages.
func lineKeys(lines []Line) string {
	keys := make([]string, len(lines))
	for i, l := range lines {
		keys[i] = l.Key
	}
	return strings.Join(keys, ", ")
}
