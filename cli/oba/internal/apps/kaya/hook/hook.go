// Package hook is oba kaya hook: the Claude Code hooks of config delivery (session-start,
// session-end, pre-tool-use, post-tool-use, user-prompt-submit) and of the recorder (record).
// Kaya's managed settings drop-in registers all of them with the absolute path of oba; oba kaya
// install --managed renders it.
package hook

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/delivery"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/install"
	"github.com/korurlabs/oba-cli/internal/dojo/exitcode"
)

const (
	// maxHookInput bounds the event a hook reads; a Write of a large file carries its content.
	maxHookInput = 64 << 20
	// maxContext is the most a hook may add to the session's context.
	maxContext = 10000
	// feedbackTimeout bounds the session start's call for the plan feedback a session that ended
	// on the branch left: past it the session starts without that feedback, which stays pending.
	feedbackTimeout = 2 * time.Second
	// inboxLockTimeout bounds a hook's wait for the watcher's short hold of the inbox.
	inboxLockTimeout = time.Second
	// minFeedbackContext is the least room a cut feedback item is still given in.
	minFeedbackContext = 1000
	// proposeMessage answers an edit of a delivered path. A session agent cannot edit the path in
	// place, so it names the --from route that proposes the file without touching the checkout.
	proposeMessage = "Kaya delivers this file; change it through Kaya. Write the new version to the same " +
		"relative path below a scratch directory and run `oba kaya propose --reason \"<why>\" --from <scratch dir> <path>` " +
		"(`oba kaya propose --reason \"<why>\" --remove <path>` removes it). The change applies once it is approved and released."
	// userLauncher is the user-level launcher hook of korurlabs/agents that already runs the
	// checkout's dispatcher in every session. The value is the file name its machine installer
	// gives the launcher copy in the user settings, so it must match that name, not the org.
	userLauncher = "senseylabs-agents-guards"
)

// lessonsTimeout bounds the session start's read of the checkout's lessons, every page and every
// fresh start of it included: past it the session starts on the cached .kaya/lessons.md and its
// stored ETag. A variable so that a test can run out of it quickly.
var lessonsTimeout = 2 * time.Second

// guardedTools are refused while the checkout has no verified release: the tools that run
// commands, change files or start work from skills and agents.
var guardedTools = map[string]bool{
	"Bash": true, "Edit": true, "Write": true, "MultiEdit": true, "NotebookEdit": true,
	"Skill": true, "Agent": true, "Task": true,
}

var editTools = map[string]bool{"Edit": true, "Write": true, "MultiEdit": true, "NotebookEdit": true}

// Names are the hooks Run knows, as Claude Code settings name them after oba kaya hook.
var Names = []string{"session-start", "session-end", "pre-tool-use", "post-tool-use", "user-prompt-submit", "record"}

// Run runs the hook name on the event read from stdin and writes its answer for Claude Code to w.
// An error of the hook itself goes to stderr and Run returns nil, so that it never blocks Claude
// Code; only the checkout's dispatcher refusing a tool returns an exitcode.Error with code 2.
func Run(ctx context.Context, name string, stdin io.Reader, w, stderr io.Writer) error {
	raw, err := io.ReadAll(io.LimitReader(stdin, maxHookInput))
	if err != nil {
		return hookFailed(stderr, fmt.Errorf("read the event: %w", err))
	}
	if name == "record" {
		// The capture reads the event as it is, so a field of an unexpected type costs
		// that field, not the event.
		return recordHook(raw, stderr)
	}
	var in hookInput
	if err := json.Unmarshal(raw, &in); err != nil {
		return hookFailed(stderr, fmt.Errorf("the event is not JSON: %w", err))
	}
	switch name {
	case "session-start":
		return hookFailed(stderr, sessionStart(ctx, in, w))
	case "session-end":
		return hookFailed(stderr, sessionEnd(ctx, in))
	case "pre-tool-use":
		return preToolUse(ctx, in, raw, w, stderr)
	case "post-tool-use":
		return hookFailed(stderr, inboxFeedback(ctx, in, "PostToolUse", w))
	case "user-prompt-submit":
		return hookFailed(stderr, inboxFeedback(ctx, in, "UserPromptSubmit", w))
	}
	return hookFailed(stderr, fmt.Errorf("unknown hook %q", name))
}

type hookInput struct {
	SessionID string         `json:"session_id"`
	Cwd       string         `json:"cwd"`
	Event     string         `json:"hook_event_name"`
	Source    string         `json:"source"`
	Reason    string         `json:"reason"`
	ToolName  string         `json:"tool_name"`
	ToolInput map[string]any `json:"tool_input"`
}

// hookFailed reports a hook's own error on stderr and lets Claude Code go on: a broken hook must
// not stop a session. The guard's refusals are not errors and never pass here.
func hookFailed(stderr io.Writer, err error) error {
	if err != nil {
		_, _ = fmt.Fprintln(stderr, "oba kaya hook:", err)
	}
	return nil
}

// hookDir is the session's working directory: the event's cwd, else the hook's own.
func hookDir(in hookInput) (string, error) {
	if in.Cwd != "" {
		return in.Cwd, nil
	}
	wd, err := os.Getwd()
	if err != nil {
		return "", fmt.Errorf("read the working directory: %w", err)
	}
	return wd, nil
}

func hookCheckout(in hookInput) (*delivery.Checkout, error) {
	dir, err := hookDir(in)
	if err != nil {
		return nil, err
	}
	return delivery.FindCheckout(dir)
}

// sessionStart reports the session's release to Claude Code and to the recorder.
func sessionStart(ctx context.Context, in hookInput, w io.Writer) error {
	checkout, err := hookCheckout(in)
	if err != nil || checkout.Kind != delivery.Delivered {
		return err
	}
	claudePID := delivery.ClaudePID()
	launched := os.Getenv(delivery.EnvLaunched) == "1" && os.Getenv(delivery.EnvCheckout) == checkout.Root
	var (
		lines        []string
		changes      []string
		number       int
		sha          string
		releaseState string
		deliveredBy  string
		reload       bool
		problems     []error
	)
	api := &hookAPI{checkout: checkout}
	if launched {
		// The launcher synced already; the hook reads only local state.
		deliveredBy = delivery.DeliveredByLauncher
		d, err := delivery.Open(ctx, checkout, false)
		if err != nil {
			return err
		}
		active, state, activeErr := d.ActiveRelease()
		releaseState = os.Getenv(delivery.EnvReleaseState)
		number, _ = strconv.Atoi(os.Getenv(delivery.EnvRelease))
		switch {
		case activeErr != nil:
			releaseState, number = delivery.StateUnverified, 0
			problems = append(problems, activeErr)
		case active == nil:
			releaseState, number = delivery.StateNone, 0
		case active.Manifest.Number != number:
			// Another session switched the checkout after this launch.
			number = active.Manifest.Number
		}
		out := delivery.Outcome{State: releaseState, Active: number, Staged: state.Staged}
		if active != nil {
			sha, out.ActiveCreatedAt, out.ManifestSHA256 = active.Signed.ManifestSHA256, active.Manifest.CreatedAt, active.Signed.ManifestSHA256
		}
		lines = append(lines, delivery.Summary(checkout, out, state))
		if reason := os.Getenv(delivery.EnvOffline); reason != "" && active != nil {
			lines = append(lines, offlineLine(number, active.Manifest.CreatedAt, reason))
		}
		if state.Staged > number && number > 0 {
			lines = append(lines, fmt.Sprintf("Config release %d waits for the next session.", state.Staged))
		}
		changes = d.ChangesSince(state.LastSessionRelease, active, state)
	} else {
		deliveredBy = delivery.DeliveredByHook
		d, err := delivery.Open(ctx, checkout, true)
		if err != nil {
			return err
		}
		d.ExcludePID = claudePID
		api.keep(d)
		lockCtx, cancel := delivery.LockContext(ctx)
		defer cancel()
		out, err := d.Sync(lockCtx, delivery.KeepAlwaysOn)
		// A delivered path git tracks, as on a branch from before Kaya delivered that path, refuses
		// only the release that would replace it: the active release stays placed, so the session
		// is reported and registered on it, and the refusal with its fix is a warning.
		var tracked *delivery.TrackedError
		if errors.As(err, &tracked) {
			out.Warnings = append(out.Warnings, err.Error())
		} else if err != nil {
			return err
		}
		state, err := checkout.LoadState()
		if err != nil {
			problems = append(problems, err)
		}
		releaseState, number, sha, reload = out.State, out.Active, out.ManifestSHA256, out.Switched
		lines = append(lines, delivery.Summary(checkout, out, state))
		lines = append(lines, delivery.Details(out)...)
		if out.Switched && len(out.Changed) > 0 {
			changes = out.Changed
		} else {
			active, _, activeErr := d.ActiveRelease()
			if activeErr == nil {
				changes = d.ChangesSince(state.LastSessionRelease, active, state)
			}
		}
	}
	if delivery.ValidSessionID(in.SessionID) {
		session := delivery.Session{PID: claudePID, SessionID: in.SessionID, Release: number, State: releaseState, DeliveredBy: deliveredBy}
		if err := checkout.RegisterSession(session); err != nil {
			problems = append(problems, err)
		}
		if err := delivery.WriteReport(in.SessionID, delivery.NewReport(number, sha, releaseState, deliveredBy)); err != nil {
			problems = append(problems, err)
		}
	}
	if number > 0 {
		d, err := delivery.Open(ctx, checkout, false)
		if err == nil {
			lockCtx, cancel := delivery.LockContext(ctx)
			err = d.RecordSessionStart(lockCtx, number)
			cancel()
		}
		if err != nil {
			problems = append(problems, err)
		}
	}
	warning, err := belowRootWarning(checkout, in)
	switch {
	case err != nil:
		problems = append(problems, err)
	case warning != "":
		lines = append([]string{warning}, lines...)
	}
	src, feedback, err := branchFeedback(ctx, checkout, in, api)
	if err != nil {
		problems = append(problems, err)
	}
	lessons, err := fetchLessons(ctx, checkout, api)
	if err != nil {
		problems = append(problems, err)
	}
	given, lessonsTold, err := writeSessionContext(w, lines, changes, feedback, lessons, in.SessionID, reload)
	if err != nil {
		problems = append(problems, err)
		return errors.Join(problems...)
	}
	if given > 0 {
		if err := ackBranchFeedback(ctx, checkout, src, in.SessionID, feedback[:given]); err != nil {
			problems = append(problems, err)
		}
	}
	if lessonsTold {
		lockCtx, cancel := delivery.LockContext(ctx)
		err := checkout.StoreLessons(lockCtx, *lessons)
		cancel()
		if err != nil {
			problems = append(problems, err)
		}
	}
	return errors.Join(problems...)
}

// hookAPI reaches Kaya for one session start and finds the machine token at most once, since each
// read may start kagi and the calls share the time a session waits to start.
type hookAPI struct {
	checkout  *delivery.Checkout
	deliverer *delivery.Deliverer
	err       error
}

// keep takes the deliverer of a sync when that one found the machine token.
func (a *hookAPI) keep(d *delivery.Deliverer) {
	if d.Source != nil {
		a.deliverer = d
	}
}

func (a *hookAPI) online(ctx context.Context) (*delivery.Deliverer, error) {
	if a.deliverer == nil && a.err == nil {
		a.deliverer, a.err = delivery.Open(ctx, a.checkout, true)
	}
	return a.deliverer, a.err
}

// requestRemote is the remote the API knows the checkout by.
func requestRemote(checkout *delivery.Checkout, state delivery.State) (string, error) {
	if state.RequestRemote != "" {
		return state.RequestRemote, nil
	}
	remote, _, err := checkout.Remotes()
	return remote, err
}

// currentBranch is the branch checked out in root, empty on a detached HEAD.
var currentBranch = func(ctx context.Context, root string) (string, error) {
	out, err := exec.CommandContext(ctx, "git", "-C", root, "rev-parse", "--abbrev-ref", "HEAD").Output() // #nosec G204 -- fixed git subcommand on the checkout
	if err != nil {
		return "", fmt.Errorf("read the branch of %s: %w", root, err)
	}
	branch := strings.TrimSpace(string(out))
	if branch == "HEAD" {
		return "", nil
	}
	return branch, nil
}

// branchFeedback asks Kaya, for at most feedbackTimeout, for the pending feedback on plans whose
// session ended on this checkout's branch, so the session that takes the work over gets it.
// Offline the session gets no feedback and it stays pending in Kaya.
func branchFeedback(ctx context.Context, checkout *delivery.Checkout, in hookInput, api *hookAPI) (delivery.FeedbackSource, []client.PlanFeedback, error) {
	if !delivery.ValidSessionID(in.SessionID) {
		return nil, nil, nil
	}
	ctx, cancel := context.WithTimeout(ctx, feedbackTimeout)
	defer cancel()
	branch, err := currentBranch(ctx, checkout.Root)
	if err != nil || branch == "" {
		return nil, nil, err
	}
	state, err := checkout.LoadState()
	if err != nil {
		return nil, nil, err
	}
	remote, err := requestRemote(checkout, state)
	if err != nil {
		return nil, nil, err
	}
	d, err := api.online(ctx)
	if err != nil {
		return nil, nil, err
	}
	src := delivery.FeedbackSourceOf(d)
	if src == nil {
		return nil, nil, d.SourceError
	}
	items, err := src.PendingFeedbackForBranch(ctx, remote, branch)
	if err != nil {
		return nil, nil, fmt.Errorf("fetch the plan feedback of branch %s: %w", branch, err)
	}
	return src, items, nil
}

// fetchLessons asks Kaya, for at most lessonsTimeout, whether the checkout's lessons changed. It
// answers nil when they did not. Any failure, an archived repository's refusal and a budget that
// runs out between two pages included, leaves the cached .kaya/lessons.md and the stored values as
// they are, so the next session asks again and nothing is lost.
func fetchLessons(ctx context.Context, checkout *delivery.Checkout, api *hookAPI) (*delivery.LessonsUpdate, error) {
	ctx, cancel := context.WithTimeout(ctx, lessonsTimeout)
	defer cancel()
	state, err := checkout.LoadState()
	if err != nil {
		return nil, err
	}
	remote, err := requestRemote(checkout, state)
	if err != nil {
		return nil, err
	}
	d, err := api.online(ctx)
	if err != nil {
		return nil, err
	}
	src, ok := d.Source.(delivery.LessonSource)
	if !ok {
		return nil, d.SourceError
	}
	update, err := checkout.FetchLessons(ctx, src, remote, state)
	if err != nil {
		return nil, fmt.Errorf("fetch the lessons: %w", err)
	}
	return update, nil
}

// ackBranchFeedback tells Kaya that the new session received the feedback; when Kaya is not
// reached, the ids wait in the session's acks file for oba kaya watch.
func ackBranchFeedback(ctx context.Context, checkout *delivery.Checkout, src delivery.FeedbackSource, sessionID string, given []client.PlanFeedback) error {
	ids := make([]string, 0, len(given))
	for _, item := range given {
		ids = append(ids, item.ID)
	}
	ackCtx, cancel := context.WithTimeout(ctx, feedbackTimeout)
	_, err := src.AckFeedback(ackCtx, sessionID, ids)
	cancel()
	if err == nil {
		return nil
	}
	lockCtx, cancel := context.WithTimeout(ctx, inboxLockTimeout)
	defer cancel()
	if queueErr := checkout.RecordFeedbackAcks(lockCtx, sessionID, ids); queueErr != nil {
		return errors.Join(fmt.Errorf("acknowledge the plan feedback: %w", err), queueErr)
	}
	return nil
}

// inboxFeedback adds the feedback oba kaya watch put in the session's inbox to its context and
// queues what it added for the watcher to acknowledge. It reads only local files: a session
// without an inbox gets no output, and the hook never waits on the network.
func inboxFeedback(ctx context.Context, in hookInput, event string, w io.Writer) error {
	if !delivery.ValidSessionID(in.SessionID) {
		return nil
	}
	checkout, err := hookCheckout(in)
	if err != nil || checkout.Kind != delivery.Delivered {
		return err
	}
	lockCtx, cancel := context.WithTimeout(ctx, inboxLockTimeout)
	defer cancel()
	return checkout.DeliverInbox(lockCtx, in.SessionID, func(items []client.PlanFeedback) (int, error) {
		text, given := feedbackContext(items, in.SessionID, maxContext)
		if given == 0 {
			return 0, nil
		}
		return given, writeHookContext(w, map[string]any{"hookEventName": event, "additionalContext": text})
	})
}

// feedbackDecisions name a reviewer's decision in the session's context.
var feedbackDecisions = map[string]string{
	client.DecisionApprove:        "Approved",
	client.DecisionRequestChanges: "Changes requested",
	client.DecisionComment:        "Comment",
}

// feedbackLine is one item of plan feedback as the session reads it. It names Kaya and the
// reviewer, so that Claude weighs it as review input and not as an instruction of the user.
func feedbackLine(item client.PlanFeedback, sessionID string) string {
	plan := item.Path
	switch {
	case plan != "":
	case item.SessionID == sessionID:
		plan = "the plan of this session"
	default:
		plan = "the plan of session " + item.SessionID
	}
	decision := feedbackDecisions[item.Decision]
	if decision == "" {
		decision = item.Decision
	}
	line := fmt.Sprintf("Kaya plan feedback from %s on %s version %d: %s.", item.ReviewerName, plan, item.VersionNumber, decision)
	if item.LineStart > 0 {
		line += fmt.Sprintf(" Lines %d-%d", item.LineStart, item.LineEnd)
		if item.Quote != "" {
			line += fmt.Sprintf(": %q", item.Quote)
		}
		line += "."
	}
	if item.Comment != "" {
		line += " " + item.Comment
	}
	return line
}

// feedbackContext is the text of the leading items that fit in limit, and how many those are;
// the rest are named as "and N more" with the plan page of the first of them, and stay pending.
// A single item longer than limit is cut, unless limit is below minFeedbackContext, where the
// cut would leave too little to act on and the item waits instead.
func feedbackContext(items []client.PlanFeedback, sessionID string, limit int) (string, int) {
	if len(items) == 0 || limit <= 0 {
		return "", 0
	}
	lines := make([]string, len(items))
	for i, item := range items {
		lines[i] = feedbackLine(item, sessionID)
	}
	build := func(n int) string {
		var parts []string
		for _, item := range items[:n] {
			if item.Decision == client.DecisionRequestChanges {
				parts = append(parts, "Revise the plan before you implement.")
				break
			}
		}
		parts = append(parts, lines[:n]...)
		if n < len(items) {
			parts = append(parts, fmt.Sprintf("and %d more on the Kaya plan page %s", len(items)-n, items[n].PlanURL))
		}
		return strings.Join(parts, "\n")
	}
	for n := len(items); n > 1; n-- {
		if text := build(n); len(text) <= limit {
			return text, n
		}
	}
	if limit < minFeedbackContext {
		return "", 0
	}
	return cut(build(1), limit), 1
}

// cut shortens text to at most limit bytes without splitting a character.
func cut(text string, limit int) string {
	if len(text) <= limit {
		return text
	}
	end := limit
	for end > 0 && !utf8.RuneStart(text[end]) {
		end--
	}
	return text[:end]
}

func writeHookContext(w io.Writer, output map[string]any) error {
	data, err := json.Marshal(map[string]any{"hookSpecificOutput": output})
	if err != nil {
		return fmt.Errorf("encode the hook output: %w", err)
	}
	_, err = fmt.Fprintln(w, string(data))
	return err
}

// belowRootWarning tells the agent that the session started in a folder below the checkout root.
// Claude Code then reads .claude/settings.json only from that folder and skips the delivered rules,
// whose links lead out of it, so the hooks, the guardrail deny rules and the rules
// of the release do not apply to the session.
func belowRootWarning(checkout *delivery.Checkout, in hookInput) (string, error) {
	dir, err := hookDir(in)
	if err != nil {
		return "", err
	}
	abs, err := filepath.Abs(dir)
	if err != nil {
		return "", fmt.Errorf("resolve %s: %w", dir, err)
	}
	if samePath(abs, checkout.Root) {
		return "", nil
	}
	rel, err := filepath.Rel(checkout.Root, abs)
	if err != nil || rel == "." || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		// FindCheckout found the root above dir, so dir always lies below it.
		return "", err
	}
	return fmt.Sprintf("Warning: this session started in %s, below the checkout root %s. Claude Code loads neither "+
		"the delivered rules nor .claude/settings.json (Kaya's hooks and its guardrail deny rules) in such a session, "+
		"so they do not apply here. Ask the user to restart Claude Code in %s.", filepath.ToSlash(rel), checkout.Root, checkout.Root), nil
}

// offlineLine tells the agent that the launcher did not reach Kaya and the session runs on the
// cached release, with the launcher's reason.
func offlineLine(number int, createdAt, reason string) string {
	return fmt.Sprintf("Kaya is unreachable; this session runs on config release %d of %s. (%s)", number, createdAt, reason)
}

// lessonsContext names the changed lessons within room, then counts the lessons it did not name,
// so a title that does not fit is counted with the unchanged ones. Without a changed lesson, or
// without room for the first line and the count, it adds nothing.
func lessonsContext(changed []string, total, room int) string {
	if len(changed) == 0 {
		return ""
	}
	more := func(named int) string {
		if total <= named {
			return ""
		}
		return fmt.Sprintf("\nand %d more, see oba kaya lessons list", total-named)
	}
	text := "\nKaya lessons new or edited since your last session here:"
	if len(text)+len(more(0)) > room {
		return ""
	}
	named := 0
	for _, title := range changed {
		line := "\n- " + title
		if len(text)+len(line)+len(more(named+1)) > room {
			break
		}
		text += line
		named++
	}
	return text + more(named)
}

// writeSessionContext prints the SessionStart answer: the release lines, the plan feedback after
// them, the changed paths and the changed lessons, within the limit Claude Code puts on added
// context. It answers how many feedback items it added, and whether lessons may be stored: a
// list whose changed titles found no room waits, so that the next session still names them. The
// changed paths give way to the feedback, and the lessons give way to everything before them.
func writeSessionContext(w io.Writer, lines, changes []string, feedback []client.PlanFeedback, lessons *delivery.LessonsUpdate, sessionID string, reload bool) (int, bool, error) {
	text := cut("Kaya config. "+strings.Join(lines, " "), maxContext)
	feedbackText, given := feedbackContext(feedback, sessionID, maxContext-len(text)-1)
	if given > 0 {
		text += "\n" + feedbackText
	}
	if len(changes) > 0 {
		text += "\nChanged since the last session:"
		for i, path := range changes {
			line := "\n- " + path
			rest := fmt.Sprintf("\n- and %d more", len(changes)-i)
			if len(text)+len(line)+len(rest) > maxContext {
				text += rest
				break
			}
			text += line
		}
	}
	lessonsTold := false
	if lessons != nil {
		lessonsText := lessonsContext(lessons.Changed, len(lessons.Lessons), maxContext-len(text))
		text += lessonsText
		lessonsTold = lessonsText != "" || len(lessons.Changed) == 0
	}
	output := map[string]any{"hookEventName": "SessionStart", "additionalContext": cut(text, maxContext)}
	if reload {
		output["reloadSkills"] = true
	}
	return given, lessonsTold, writeHookContext(w, output)
}

// sessionEnd forgets the session and activates a staged release once no other session of the
// checkout runs, so a start without the launcher still gets it next time.
func sessionEnd(ctx context.Context, in hookInput) error {
	checkout, err := hookCheckout(in)
	if err != nil || checkout.Kind != delivery.Delivered {
		return err
	}
	var problems []error
	if delivery.ValidSessionID(in.SessionID) {
		if err := checkout.RemoveSession(in.SessionID); err != nil {
			problems = append(problems, err)
		}
		lockCtx, cancel := context.WithTimeout(ctx, inboxLockTimeout)
		err := checkout.RemoveInbox(lockCtx, in.SessionID)
		cancel()
		if err != nil {
			problems = append(problems, err)
		}
	}
	// After /clear or /resume the same Claude Code goes on, and its next session start syncs.
	if in.Reason != "clear" && in.Reason != "resume" {
		d, err := delivery.Open(ctx, checkout, false)
		if err == nil {
			d.ExcludePID = delivery.ClaudePID()
			lockCtx, cancel := delivery.LockContext(ctx)
			_, err = d.ActivateStaged(lockCtx)
			cancel()
		}
		if err != nil {
			problems = append(problems, err)
		}
	}
	return errors.Join(problems...)
}

// preToolUse is the guard: it refuses guarded tools without a verified release and edits of
// delivered paths, then runs the checkout's dispatcher where the project settings do not.
func preToolUse(ctx context.Context, in hookInput, raw []byte, w, stderr io.Writer) error {
	checkout, err := hookCheckout(in)
	if err != nil {
		// The checkout's own hooks still run when only its recognition failed.
		_ = hookFailed(stderr, err)
		if checkout == nil {
			return nil
		}
	}
	if checkout.Kind == delivery.Delivered {
		if reason := guardReason(ctx, checkout, in, stderr); reason != "" {
			return hookFailed(stderr, deny(w, reason))
		}
	}
	return runDispatcher(checkout, in, raw, w, stderr)
}

func guardReason(ctx context.Context, checkout *delivery.Checkout, in hookInput, stderr io.Writer) string {
	var manifest *delivery.Manifest
	d, err := delivery.Open(ctx, checkout, false)
	if err == nil {
		var active *delivery.Release
		active, _, err = d.ActiveRelease()
		if active != nil {
			manifest = &active.Manifest
		}
	}
	if manifest == nil && guardedTools[in.ToolName] {
		state, stateErr := checkout.LoadState()
		if stateErr != nil {
			_, _ = fmt.Fprintln(stderr, "oba kaya hook:", stateErr)
		}
		reason := fmt.Sprintf("No Kaya config on this machine for %s. Run `oba kaya sync` online once.", checkout.Name(state))
		if err != nil {
			reason += " (" + err.Error() + ")"
		}
		return reason
	}
	if !editTools[in.ToolName] {
		return ""
	}
	path := toolPath(in.ToolInput)
	if path == "" {
		return ""
	}
	if !filepath.IsAbs(path) {
		path = filepath.Join(checkout.Root, path)
	}
	if protectedPath(checkout, manifest, path) {
		return proposeMessage
	}
	return ""
}

func toolPath(input map[string]any) string {
	for _, key := range []string{"file_path", "notebook_path"} {
		if value, ok := input[key].(string); ok && value != "" {
			return value
		}
	}
	return ""
}

// protectedPath reports whether an edit of path would change Kaya's output: a delivered path,
// the release record, or anything that lands below .kaya/ once links are followed.
func protectedPath(checkout *delivery.Checkout, manifest *delivery.Manifest, path string) bool {
	kayaDir := filepath.Join(checkout.Root, ".kaya")
	for _, candidate := range []string{filepath.Clean(path), resolved(path)} {
		rel, err := filepath.Rel(checkout.Root, candidate)
		if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
			continue
		}
		slash := filepath.ToSlash(rel)
		if candidate == kayaDir || strings.HasPrefix(candidate, kayaDir+string(filepath.Separator)) {
			return true
		}
		if strings.EqualFold(slash, delivery.ReleaseRecordPath) || (manifest != nil && manifest.Delivers(slash)) {
			return true
		}
	}
	return false
}

// resolved follows the links of path, or of its directory when path does not exist yet.
func resolved(path string) string {
	if real, err := filepath.EvalSymlinks(path); err == nil {
		return real
	}
	if dir, err := filepath.EvalSymlinks(filepath.Dir(path)); err == nil {
		return filepath.Join(dir, filepath.Base(path))
	}
	return filepath.Clean(path)
}

func samePath(a, b string) bool {
	return filepath.Clean(a) == filepath.Clean(b) || resolved(a) == resolved(b)
}

func deny(w io.Writer, reason string) error {
	data, err := json.Marshal(map[string]any{"hookSpecificOutput": map[string]any{
		"hookEventName":            "PreToolUse",
		"permissionDecision":       "deny",
		"permissionDecisionReason": reason,
	}})
	if err != nil {
		return fmt.Errorf("encode the refusal: %w", err)
	}
	_, err = fmt.Fprintln(w, string(data))
	return err
}

// runDispatcher runs .agents/hooks/dispatch.sh of the checkout with the event, as the
// korurlabs/agents user-level launcher does, unless the session's project settings or the user
// settings run it already. A refusal of a guard (exit 2) comes through.
func runDispatcher(checkout *delivery.Checkout, in hookInput, raw []byte, w, stderr io.Writer) error {
	if checkout.Kind == delivery.NotRepository {
		return nil
	}
	script := filepath.Join(checkout.Root, ".agents", "hooks", "dispatch.sh")
	info, err := os.Stat(script)
	if err != nil || !info.Mode().IsRegular() || info.Mode().Perm()&0o111 == 0 {
		return nil
	}
	projectDir := os.Getenv("CLAUDE_PROJECT_DIR")
	if projectDir == "" {
		projectDir = in.Cwd
	}
	for _, settings := range []string{
		filepath.Join(projectDir, ".claude", "settings.json"),
		filepath.Join(projectDir, ".claude", "settings.local.json"),
	} {
		if mentions(settings, "dispatch.sh") {
			return nil
		}
	}
	if home, err := os.UserHomeDir(); err == nil {
		userSettings := install.ClaudeSettingsPath(install.Options{Home: home, ClaudeConfigDir: os.Getenv("CLAUDE_CONFIG_DIR")})
		if mentions(userSettings, userLauncher) {
			return nil
		}
	}
	run := exec.Command(script) // #nosec G204 -- the checkout's own hook dispatcher, as its settings would run it
	run.Dir = checkout.Root
	run.Stdin = bytes.NewReader(raw)
	run.Stdout = w
	run.Stderr = stderr
	run.Env = os.Environ()
	if os.Getenv("CLAUDE_PROJECT_DIR") == "" {
		run.Env = append(run.Env, "CLAUDE_PROJECT_DIR="+checkout.Root)
	}
	err = run.Run()
	var exitErr *exec.ExitError
	switch {
	case err == nil:
		return nil
	case errors.As(err, &exitErr) && exitErr.ExitCode() == 2:
		return &exitcode.Error{Code: 2}
	default:
		return hookFailed(stderr, fmt.Errorf("the hook dispatcher of %s failed: %w", checkout.Root, err))
	}
}

func mentions(path, text string) bool {
	data, err := os.ReadFile(path) // #nosec G304 G703 -- a settings file of the session or of the user
	return err == nil && bytes.Contains(data, []byte(text))
}
