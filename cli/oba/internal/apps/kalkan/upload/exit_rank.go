package upload

// Exit statuses of an upload. 1, 2 and 3 mean what they mean in every oba command (a failure,
// an API 404, an API 403); 4 is a page edited in Kalkan since the last upload.
const (
	exitOK        = 0
	exitFailure   = 1
	exitNotFound  = 2
	exitForbidden = 3
	exitConflict  = 4
)

// exitRank orders the exit statuses of a run that ends several ways at once: a failure outranks
// a denial (403), which outranks a hidden page (404), which outranks a conflict. A conflict ranks
// lowest because only it is cleared by a deliberate --force; the others need a fix first.
var exitRank = map[int]int{
	exitOK:        0,
	exitConflict:  1,
	exitNotFound:  2,
	exitForbidden: 3,
	exitFailure:   4,
}

// ExitStatus is the exit status of the run: 0 when every page was created, updated or left
// unchanged, else the highest ranked status of its pages and run errors.
func ExitStatus(report Report) int {
	status := exitOK
	for _, page := range report.Pages {
		status = worseExit(status, page.exit)
	}
	for _, err := range report.RunErrors {
		_, exit := classify(err)
		status = worseExit(status, exit)
	}
	return status
}

// worseExit returns the exit status of higher rank; an unknown status counts as a failure.
func worseExit(current, next int) int {
	currentRank, ok := exitRank[current]
	if !ok {
		currentRank = exitRank[exitFailure]
	}
	nextRank, ok := exitRank[next]
	if !ok {
		nextRank = exitRank[exitFailure]
	}
	if nextRank > currentRank {
		return next
	}
	return current
}
