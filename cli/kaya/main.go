package main

import (
	"os"
	"path/filepath"

	"github.com/korurlabs/kaya-cli/cmd"
	"github.com/korurlabs/kaya-cli/internal/install"
)

var version = "dev"

func main() {
	cmd.SetVersion(version)
	// Called as kaya-launch, the binary is the wrapper VS Code starts Claude Code through.
	if filepath.Base(os.Args[0]) == install.LaunchName {
		cmd.ExecuteLaunch(os.Args[1:])
		return
	}
	cmd.Execute()
}
