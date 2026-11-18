package main

import "github.com/korurlabs/oba-cli/cmd"

// A release build sets these through ldflags (.goreleaser.yaml); a local build
// keeps the defaults.
var (
	version = "dev"
	commit  = ""
	date    = ""
)

func main() {
	cmd.SetVersion(buildVersion())
	cmd.Execute()
}

// buildVersion adds the commit and build time of a release to its version, so
// that `oba --version` in a bug report names the exact build.
func buildVersion() string {
	switch {
	case commit == "":
		return version
	case date == "":
		return version + " (" + commit + ")"
	default:
		return version + " (" + commit + ", " + date + ")"
	}
}
