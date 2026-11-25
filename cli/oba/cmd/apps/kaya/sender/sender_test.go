package sender

import (
	"context"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/sender"
)

// The sender records the version of the oba that runs it in a park, so that another version sends
// a park after a machine name refusal once more. A version that is missing would keep such a park
// waiting for a new token, so an empty one is taken as a local build.
func TestTheSenderRunsUnderTheVersionOfTheRunningOba(t *testing.T) {
	previous := senderRun
	t.Cleanup(func() { senderRun = previous })
	for name, versions := range map[string]struct{ root, want string }{
		"release":       {"0.6.0 (abc1234, 2026-11-25T09:00:00Z)", "0.6.0 (abc1234, 2026-11-25T09:00:00Z)"},
		"local build":   {"dev", "dev"},
		"empty version": {"", localBuildVersion},
	} {
		t.Run(name, func(t *testing.T) {
			var got *sender.Config
			senderRun = func(_ context.Context, cfg sender.Config) error {
				got = &cfg
				return nil
			}
			group := &cobra.Command{Use: "kaya"}
			group.AddCommand(NewCmd())
			root := &cobra.Command{Use: "oba", Version: versions.root, SilenceUsage: true, SilenceErrors: true}
			root.AddCommand(group)
			root.SetArgs([]string{"kaya", "sender", "--once"})
			if err := root.Execute(); err != nil {
				t.Fatal(err)
			}
			if got == nil {
				t.Fatal("the command did not run the sender")
			}
			if got.Version != versions.want || !got.Once {
				t.Errorf("sender ran with version %q and once %t, want version %q and once", got.Version, got.Once, versions.want)
			}
		})
	}
}
