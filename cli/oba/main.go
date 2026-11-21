package main

import (
	"os"
	"path/filepath"

	"github.com/korurlabs/oba-cli/cmd"
	"github.com/korurlabs/oba-cli/cmd/apps/kaya"
)

// A release build sets these through ldflags (.goreleaser.yaml); a local build
// keeps the defaults.
var (
	version = "dev"
	commit  = ""
	date    = ""
)

func main() {
	cmd.SetVersion(buildVersion())
	// Called as oba-kaya-launch, the binary is the wrapper VS Code starts Claude Code through.
	if filepath.Base(os.Args[0]) == kaya.LaunchName {
		kaya.ExecuteLaunch(os.Args[1:])
		return
	}
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
