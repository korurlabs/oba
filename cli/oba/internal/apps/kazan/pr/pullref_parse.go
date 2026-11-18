package pr

import (
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

// PullRef is a pull request named on the command line: its repository as typed (owner/name,
// name or GitHub repository id) and its number.
type PullRef struct {
	Repo   string
	Number int
}

// ParsePullRef reads the <pr> argument: a GitHub URL https://github.com/{owner}/{name}/pull/{N}
// (a trailing path, query or fragment is ignored), owner/name#N, name#N, or N or #N with --repo.
// When the argument names a repository, --repo may only name the same one, though one of the two
// may give it by its short name.
func ParsePullRef(arg, repoFlag string) (PullRef, error) {
	arg = strings.TrimSpace(arg)
	repoFlag = strings.TrimSpace(repoFlag)
	var repo, number string

	lower := strings.ToLower(arg)
	switch {
	case strings.HasPrefix(lower, "https://"), strings.HasPrefix(lower, "http://"),
		strings.HasPrefix(lower, "github.com/"), strings.HasPrefix(lower, "www.github.com/"):
		raw := arg
		if !strings.Contains(lower, "://") {
			raw = "https://" + arg
		}
		u, err := url.Parse(raw)
		if err != nil {
			return PullRef{}, fmt.Errorf("invalid pull request URL %q: %w", arg, err)
		}
		host := strings.ToLower(u.Hostname())
		segments := strings.Split(strings.Trim(u.Path, "/"), "/")
		if (host != "github.com" && host != "www.github.com") || len(segments) < 4 || segments[2] != "pull" {
			return PullRef{}, fmt.Errorf("invalid pull request URL %q: use https://github.com/{owner}/{name}/pull/{number}", arg)
		}
		repo = segments[0] + "/" + segments[1]
		number = segments[3]
	case strings.LastIndex(arg, "#") > 0:
		i := strings.LastIndex(arg, "#")
		repo = strings.TrimSpace(arg[:i])
		number = arg[i+1:]
	default:
		number = strings.TrimPrefix(arg, "#")
	}

	n, err := strconv.Atoi(strings.TrimSpace(number))
	if err != nil || n < 1 {
		return PullRef{}, fmt.Errorf("invalid pull request %q: use a GitHub URL, owner/name#N, name#N, or N with --repo", arg)
	}

	switch {
	case repo == "" && repoFlag == "":
		return PullRef{}, fmt.Errorf("pull request %q names no repository: pass --repo or use owner/name#N", arg)
	case repo == "":
		repo = repoFlag
	case repoFlag != "":
		same, ok := sameRepository(repo, repoFlag)
		if !ok {
			return PullRef{}, fmt.Errorf("pull request %q names repository %s, but --repo names %s", arg, repo, repoFlag)
		}
		repo = same
	}
	return PullRef{Repo: repo, Number: n}, nil
}

// sameRepository reports whether two repository names given by the user can name one
// repository, and returns the more specific of them. Names compare with case ignored, as
// GitHub's do. A short name and an owner/name are the same when the short name is the name
// part; the owner/name is kept because it resolves without the short name's ambiguity.
func sameRepository(a, b string) (string, bool) {
	if strings.EqualFold(a, b) {
		return a, true
	}
	aHasOwner := strings.Contains(a, "/")
	bHasOwner := strings.Contains(b, "/")
	switch {
	case aHasOwner && !bHasOwner:
		_, name, _ := strings.Cut(a, "/")
		return a, strings.EqualFold(name, b)
	case bHasOwner && !aHasOwner:
		_, name, _ := strings.Cut(b, "/")
		return b, strings.EqualFold(name, a)
	}
	return "", false
}
