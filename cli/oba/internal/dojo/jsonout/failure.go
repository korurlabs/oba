package jsonout

// KeepOnFailure makes the result stand even when the run then fails. A command that did part of
// its work before it failed, such as a bulk delete where one item failed, sets it so that a
// script still learns what was done. Every other failed run writes no JSON. It does nothing while
// --json is off.
func KeepOnFailure() {
	mu.Lock()
	defer mu.Unlock()
	keep = dest != nil
}

// KeptOnFailure reports whether the result of this run is written although the run failed.
func KeptOnFailure() bool {
	mu.Lock()
	defer mu.Unlock()
	return keep
}
