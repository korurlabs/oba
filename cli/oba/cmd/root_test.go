package cmd

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestUnknownSubcommandFailsInEveryGroup(t *testing.T) {
	cases := []struct {
		args []string
		want string
	}{
		{[]string{"bogus"}, `unknown command "bogus" for "oba"`},
		{[]string{"kazan", "prs", "list"}, `unknown command "prs" for "oba kazan"`},
		{[]string{"kazan", "prs", "list", "--json"}, `unknown command "prs" for "oba kazan"`},
		{[]string{"kazan", "bogus"}, `unknown command "bogus" for "oba kazan"`},
		{[]string{"kazan", "bogus", "--json"}, `unknown command "bogus" for "oba kazan"`},
		{[]string{"kazan", "pr", "bogus"}, `unknown command "bogus" for "oba kazan pr"`},
		{[]string{"otak", "bogus"}, `unknown command "bogus" for "oba otak"`},
		{[]string{"otak", "mail-template", "push"}, `unknown command "push" for "oba otak mail-template"`},
		{[]string{"otak", "mail-template", "pull"}, `unknown command "pull" for "oba otak mail-template"`},
		{[]string{"kalkan", "bogus"}, `unknown command "bogus" for "oba kalkan"`},
		{[]string{"kalkan", "page", "bogus"}, `unknown command "bogus" for "oba kalkan page"`},
		{[]string{"kalkan", "attachment", "bogus"}, `unknown command "bogus" for "oba kalkan attachment"`},
		{[]string{"kalkan", "attachment", "delete"}, `unknown command "delete" for "oba kalkan attachment"`},
		{[]string{"ulak", "bogus"}, `unknown command "bogus" for "oba ulak"`},
		{[]string{"admin", "member", "list"}, `unknown command "admin" for "oba"`},
	}
	for _, tc := range cases {
		t.Run(strings.Join(tc.args, " "), func(t *testing.T) {
			out, stderr, err := run(t, tc.args...)
			if err == nil {
				t.Fatalf("no error; out = %q", out)
			}
			if !strings.HasPrefix(err.Error(), tc.want) {
				t.Errorf("err = %q, want prefix %q", err, tc.want)
			}
			if code := client.ExitCode(err); code == 0 {
				t.Errorf("exit code = 0, want non-zero")
			}
			if out != "" || stderr != "" {
				t.Errorf("out = %q, stderr = %q, want both empty", out, stderr)
			}
		})
	}
}

func TestUnknownSubcommandSuggestsTheNearName(t *testing.T) {
	_, _, err := run(t, "kazan", "prs")
	if err == nil || !strings.Contains(err.Error(), "Did you mean this?\n\tpr\n") {
		t.Errorf("err = %v", err)
	}
}

func TestGroupWithoutSubcommandPrintsHelp(t *testing.T) {
	for _, args := range [][]string{{"kazan"}, {"kazan", "pr"}, {"otak"}, {"ulak"}, {"kalkan"}, {"kalkan", "page"}, {"kalkan", "attachment"}, {"kazan", "--json"}} {
		t.Run(strings.Join(args, " "), func(t *testing.T) {
			out, _, err := run(t, args...)
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(out, "Available Commands:") {
				t.Errorf("out = %q, want the group's help", out)
			}
		})
	}
}

func TestKalkanListsEveryDomain(t *testing.T) {
	out, _, err := run(t, "kalkan")
	if err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"page", "upload", "attachment"} {
		if !strings.Contains(out, "\n  "+name+" ") {
			t.Errorf("out = %q, want the command %s", out, name)
		}
	}
}
