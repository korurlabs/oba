package terminal

import (
	"fmt"
	"sort"
	"strconv"
	"strings"
)

// PickAll is the answer that picks every number of a list that is not skipped.
const PickAll = "all"

// ParsePicks reads "1,3-5", "all" or an empty answer into sorted, unique numbers of a list of
// count items, as a pick prompt takes them. "all" takes every number that is not skipped;
// naming a skipped one is an error that gives skippedReason, such as "is already in Otak and
// cannot be picked".
func ParsePicks(text string, count int, skipped map[int]bool, skippedReason string) ([]int, error) {
	text = strings.TrimSpace(text)
	if text == "" {
		return nil, nil
	}
	if strings.EqualFold(text, PickAll) {
		var picks []int
		for number := 1; number <= count; number++ {
			if !skipped[number] {
				picks = append(picks, number)
			}
		}
		return picks, nil
	}
	chosen := make(map[int]bool)
	for _, part := range strings.Split(text, ",") {
		part = strings.TrimSpace(part)
		first, last, err := parsePickRange(part)
		if err != nil {
			return nil, err
		}
		if first < 1 || last > count || first > last {
			return nil, fmt.Errorf("%q is out of range; pick numbers from 1 to %d", part, count)
		}
		for number := first; number <= last; number++ {
			if skipped[number] {
				return nil, fmt.Errorf("%d %s", number, skippedReason)
			}
			chosen[number] = true
		}
	}
	picks := make([]int, 0, len(chosen))
	for number := range chosen {
		picks = append(picks, number)
	}
	sort.Ints(picks)
	return picks, nil
}

// parsePickRange reads "3" or "3-5".
func parsePickRange(part string) (int, int, error) {
	invalid := fmt.Errorf("%q is not a number or a range such as 3-5", part)
	low, high, isRange := strings.Cut(part, "-")
	first, err := strconv.Atoi(strings.TrimSpace(low))
	if err != nil {
		return 0, 0, invalid
	}
	if !isRange {
		return first, first, nil
	}
	last, err := strconv.Atoi(strings.TrimSpace(high))
	if err != nil {
		return 0, 0, invalid
	}
	return first, last, nil
}
