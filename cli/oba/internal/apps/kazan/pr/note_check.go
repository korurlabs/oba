package pr

import "fmt"

// CheckNote refuses a note over the API's limit; nil is no note.
func CheckNote(note *string) error {
	if note != nil && utf16Length(*note) > MaxNoteLength {
		return fmt.Errorf("--note must be at most %d characters, got %d", MaxNoteLength, utf16Length(*note))
	}
	return nil
}
