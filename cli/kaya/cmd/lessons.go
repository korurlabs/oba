package cmd

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/delivery"
	"github.com/korurlabs/kaya-cli/internal/lessons"
)

// lessonsTokenTimeout bounds the machine token lookup, which can go through kagi. Each API call has
// the client's own bound.
const lessonsTokenTimeout = 30 * time.Second

type lessonsOptions struct {
	title    string
	bodyFile string
	repoWide bool
	session  string
	search   string
	json     bool
}

var lessonsOpts lessonsOptions

var lessonsCmd = &cobra.Command{
	Use:   "lessons",
	Short: "Read and record the lessons of this repository and organization in Kaya",
	Long: `Lessons are standing rules kept in Kaya, for one repository or the whole organization. A
checkout gets its repository's lessons plus the organization-wide ones.

Reading rule for agents: the title is the rule and the body holds the rest. Before a complex task
run ` + "`kaya lessons list --search <topic>`" + ` or read .kaya/lessons.md, and after a user correction run
` + "`kaya lessons add`" + `. Run ` + "`kaya lessons show <id>`" + ` for the full text of one lesson, and
` + "`kaya lessons remove <id>`" + ` when a lesson is wrong or obsolete.

The commands use the machine token. Editing a lesson happens in the Kaya app.`,
	Args: cobra.NoArgs,
	RunE: func(cmd *cobra.Command, _ []string) error { return cmd.Help() },
}

var lessonsAddCmd = &cobra.Command{
	Use:   "add --title <rule> --body-file <path> [--repo-wide] [--session <id>]",
	Short: "Record a lesson after a user correction",
	Long: `Records a lesson and prints its id and web URL. The title is the rule as a sentence (1 to 300
characters); the file says what went wrong, why, and the correct approach (1 to 4000 characters).

Without --repo-wide the lesson belongs to this checkout's repository; with it, to the organization.
The session is --session, else the session of the Claude Code process that runs the command, else
the only live session of this checkout; with none known the lesson has no session link.`,
	Args: cobra.NoArgs,
	RunE: runLessonsAdd,
}

var lessonsListCmd = &cobra.Command{
	Use:   "list [--search <topic>] [--json]",
	Short: "List the lessons of this checkout, or search their full text",
	Long: `Lists the lessons of this checkout's repository and the organization-wide ones, newest first, at
most 100, as - <id> <title> lines. --search keeps those whose title or text holds the topic and
prints the text under each title. --json prints the rows as a JSON array.`,
	Args: cobra.NoArgs,
	RunE: runLessonsList,
}

var lessonsShowCmd = &cobra.Command{
	Use:   "show <id>",
	Short: "Print a lesson with its full text",
	Args:  cobra.ExactArgs(1),
	RunE:  runLessonsShow,
}

var lessonsRemoveCmd = &cobra.Command{
	Use:   "remove <id>",
	Short: "Delete a lesson for good",
	Long:  "Deletes a lesson; there is no history to restore it from. Only its author, an ADMIN or an OWNER may.",
	Args:  cobra.ExactArgs(1),
	RunE:  runLessonsRemove,
}

var lessonsImportCmd = &cobra.Command{
	Use:   "import <file> [--repo-wide]",
	Short: "Add the lessons of a markdown file",
	Long: `Splits the file at lines that start "### " outside code fences: the heading is the title and the
lines up to the next heading are the text. What stands before the first heading is ignored.

Each lesson is added to this checkout's repository, or with --repo-wide to the organization. A
title that already exists in that scope is skipped, so the import can run again. The whole file is
refused, before anything is sent, when a lesson breaks a limit. It prints the created and skipped
counts and leaves the file in place.`,
	Args: cobra.ExactArgs(1),
	RunE: runLessonsImport,
}

func init() {
	add := lessonsAddCmd.Flags()
	add.StringVar(&lessonsOpts.title, "title", "", "the rule as a sentence, 1 to 300 characters")
	add.StringVar(&lessonsOpts.bodyFile, "body-file", "", "file that says what went wrong, why, and the correct approach")
	add.BoolVar(&lessonsOpts.repoWide, "repo-wide", false, "apply the lesson to the whole organization")
	add.StringVar(&lessonsOpts.session, "session", "", "Claude Code session id that records the lesson")
	list := lessonsListCmd.Flags()
	list.StringVar(&lessonsOpts.search, "search", "", "topic to find in the title or text")
	list.BoolVar(&lessonsOpts.json, "json", false, "print the rows as JSON")
	lessonsImportCmd.Flags().BoolVar(&lessonsOpts.repoWide, "repo-wide", false, "add the lessons to the whole organization")
	lessonsCmd.AddCommand(lessonsAddCmd, lessonsListCmd, lessonsShowCmd, lessonsRemoveCmd, lessonsImportCmd)
	rootCmd.AddCommand(lessonsCmd)
}

func runLessonsAdd(cmd *cobra.Command, _ []string) error {
	title := strings.TrimSpace(lessonsOpts.title)
	if err := lessons.CheckTitle(title); err != nil {
		return err
	}
	if lessonsOpts.bodyFile == "" {
		return errors.New("pass --body-file <path>: what went wrong, why, and the correct approach")
	}
	raw, err := os.ReadFile(lessonsOpts.bodyFile) // #nosec G304 -- the file the user names
	if err != nil {
		return fmt.Errorf("read the lesson text: %w", err)
	}
	body := strings.TrimSpace(string(raw))
	if err := lessons.CheckBody(body); err != nil {
		return err
	}
	checkout, remote, err := lessonCheckout()
	if err != nil {
		return err
	}
	session, err := lessonSession(cmd.ErrOrStderr(), checkout)
	if err != nil {
		return err
	}
	api, err := lessonsAPI(cmd.Context())
	if err != nil {
		return err
	}
	created, err := api.AddLesson(cmd.Context(), client.LessonRequest{
		RemoteURL: remote, Title: title, Body: body, RepoWide: lessonsOpts.repoWide, ClaudeSessionID: session,
	})
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Lesson %s: %s\n", created.ID, created.URL)
	return nil
}

// lessonSession is the session that records a lesson. A failed read of the session records costs
// the link, not the lesson, and says so.
func lessonSession(stderr io.Writer, checkout *delivery.Checkout) (string, error) {
	if id := lessonsOpts.session; id != "" {
		if !delivery.ValidSessionID(id) {
			return "", fmt.Errorf("--session %q is not a Claude Code session id", id)
		}
		return id, nil
	}
	live, err := checkout.LiveSessions(0)
	if err != nil {
		_, _ = fmt.Fprintf(stderr, "kaya lessons: the lesson gets no session link: %v\n", err)
		return "", nil
	}
	return lessons.SessionOf(delivery.ClaudePID(), live), nil
}

func runLessonsList(cmd *cobra.Command, _ []string) error {
	searching := cmd.Flags().Changed("search")
	topic := strings.TrimSpace(lessonsOpts.search)
	if searching {
		if err := lessons.CheckSearch(topic); err != nil {
			return err
		}
	}
	_, remote, err := lessonCheckout()
	if err != nil {
		return err
	}
	api, err := lessonsAPI(cmd.Context())
	if err != nil {
		return err
	}
	var rows []client.Lesson
	if searching {
		rows, err = api.SearchLessons(cmd.Context(), remote, topic)
	} else {
		rows, err = api.Lessons(cmd.Context(), remote)
	}
	if err != nil {
		return err
	}
	w := cmd.OutOrStdout()
	if lessonsOpts.json {
		return printLessonsJSON(w, rows)
	}
	if len(rows) == 0 {
		_, _ = fmt.Fprintln(w, "No lessons found.")
		return nil
	}
	for _, row := range rows {
		_, _ = fmt.Fprintf(w, "- %s %s\n", row.ID, row.Title)
		if row.Body != "" {
			_, _ = fmt.Fprintf(w, "  %s\n\n", strings.ReplaceAll(row.Body, "\n", "\n  "))
		}
	}
	return nil
}

// printLessonsJSON prints the rows as an array, also when there are none, so that a script never
// reads null.
func printLessonsJSON(w io.Writer, rows []client.Lesson) error {
	if rows == nil {
		rows = []client.Lesson{}
	}
	encoder := json.NewEncoder(w)
	encoder.SetEscapeHTML(false)
	encoder.SetIndent("", "  ")
	return encoder.Encode(rows)
}

func runLessonsShow(cmd *cobra.Command, args []string) error {
	api, err := lessonsAPI(cmd.Context())
	if err != nil {
		return err
	}
	lesson, err := api.LessonByID(cmd.Context(), args[0])
	if err != nil {
		return err
	}
	w := cmd.OutOrStdout()
	repository := "Organization-wide"
	if lesson.Repository != nil {
		repository = lesson.Repository.Name
	}
	_, _ = fmt.Fprintf(w, "%s\n\nRepository: %s\nAuthor: %s\nUpdated: %s\n", lesson.Title, repository, lesson.User.Name, lesson.UpdatedAt)
	if lesson.ClaudeSessionID != "" {
		_, _ = fmt.Fprintf(w, "Session: %s\n", lesson.ClaudeSessionID)
	}
	_, _ = fmt.Fprintf(w, "\n%s\n", lesson.Body)
	return nil
}

func runLessonsRemove(cmd *cobra.Command, args []string) error {
	api, err := lessonsAPI(cmd.Context())
	if err != nil {
		return err
	}
	if err := api.RemoveLesson(cmd.Context(), args[0]); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Lesson %s removed.\n", args[0])
	return nil
}

func runLessonsImport(cmd *cobra.Command, args []string) error {
	raw, err := os.ReadFile(args[0]) // #nosec G304 -- the file the user names
	if err != nil {
		return fmt.Errorf("read the lessons file: %w", err)
	}
	blocks := lessons.Split(string(raw))
	if len(blocks) == 0 {
		return fmt.Errorf("%s has no line that starts \"### \", so it holds no lesson", args[0])
	}
	_, remote, err := lessonCheckout()
	if err != nil {
		return err
	}
	api, err := lessonsAPI(cmd.Context())
	if err != nil {
		return err
	}
	result, err := lessons.Import(cmd.Context(), api, remote, lessonsOpts.repoWide, blocks)
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Created %d, skipped %d.\n", result.Created, result.Skipped)
	return err
}

// lessonCheckout is the checkout of the working directory and the remote it names to Kaya. Any
// git checkout will do: lessons are not delivered config, so a repository need not be cut over
// to record or read them.
func lessonCheckout() (*delivery.Checkout, string, error) {
	checkout, err := currentCheckout()
	if err != nil {
		return nil, "", err
	}
	if checkout.Kind == delivery.NotRepository {
		return nil, "", errNotDelivered(checkout)
	}
	remote, _, err := checkout.Remotes()
	if err != nil {
		return nil, "", err
	}
	return checkout, remote, nil
}

// lessonsAPI is a client with the machine token, read once: an import makes many calls, and each
// read can go through kagi.
func lessonsAPI(ctx context.Context) (*client.Client, error) {
	apiURL := deliveryAPIURL()
	if err := delivery.CheckTransport(apiURL); err != nil {
		return nil, err
	}
	tokenCtx, cancel := context.WithTimeout(ctx, lessonsTokenTimeout)
	defer cancel()
	token, err := machineToken(tokenCtx)
	if err != nil {
		return nil, err
	}
	return client.New(apiURL, "", func(context.Context) (string, error) { return token, nil }), nil
}
