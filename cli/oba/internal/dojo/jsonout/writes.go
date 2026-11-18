package jsonout

// CollectWrites makes the result the data of every call that changed something (any method but
// GET), in call order, as one array. Interactive commands such as finding leads create several
// records in one run, and the last of them alone would hide the others.
func CollectWrites() {
	mu.Lock()
	defer mu.Unlock()
	writesOnly = true
}
