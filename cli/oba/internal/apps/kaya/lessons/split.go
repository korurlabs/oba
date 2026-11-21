package lessons

import "strings"

// headingPrefix opens a lesson in a lessons file.
const headingPrefix = "### "

// Block is one lesson of a lessons file: the heading is its title, the lines up to the next
// heading its text.
type Block struct {
	Title string
	Body  string
}

// Split cuts a lessons file at the lines that start "### " outside code fences. What stands
// before the first heading is the file's own introduction and belongs to no lesson.
func Split(text string) []Block {
	var (
		blocks  []Block
		body    []string
		current *Block
		open    fence
	)
	flush := func() {
		if current != nil {
			current.Body = strings.TrimSpace(strings.Join(body, "\n"))
			blocks = append(blocks, *current)
		}
	}
	for line := range strings.Lines(strings.TrimPrefix(text, "\ufeff")) {
		line = strings.TrimRight(line, "\r\n")
		open = open.next(line)
		if !open.active() && strings.HasPrefix(line, headingPrefix) {
			flush()
			current = &Block{Title: strings.TrimSpace(strings.TrimPrefix(line, headingPrefix))}
			body = nil
			continue
		}
		body = append(body, line)
	}
	flush()
	return blocks
}

// fence is the code fence a line of markdown stands in; the zero value is none.
type fence struct {
	marker byte
	size   int
}

func (f fence) active() bool {
	return f.size > 0
}

// next is the fence after line, by the CommonMark rules: a closing run uses the opening
// character, is at least as long and carries nothing after it, so a shorter run stays content
// of a longer fence.
func (f fence) next(line string) fence {
	marker, size, rest := fenceRun(line)
	if size == 0 {
		return f
	}
	if f.active() {
		if marker == f.marker && size >= f.size && strings.TrimSpace(rest) == "" {
			return fence{}
		}
		return f
	}
	if marker == '`' && strings.ContainsRune(rest, '`') {
		return f
	}
	return fence{marker: marker, size: size}
}

// fenceRun finds a run of three or more backticks or tildes that opens line, at most three spaces
// in, and what follows it. The size is 0 when the line has none.
func fenceRun(line string) (marker byte, size int, rest string) {
	trimmed := strings.TrimLeft(line, " ")
	if trimmed == "" || len(line)-len(trimmed) > 3 {
		return 0, 0, ""
	}
	marker = trimmed[0]
	if marker != '`' && marker != '~' {
		return 0, 0, ""
	}
	rest = strings.TrimLeft(trimmed, string(marker))
	size = len(trimmed) - len(rest)
	if size < 3 {
		return 0, 0, ""
	}
	return marker, size, rest
}
