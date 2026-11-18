package pr

import "fmt"

// CheckSearch refuses a pull request search over the API's limit.
func CheckSearch(search string) error {
	if utf16Length(search) > MaxSearchLength {
		return fmt.Errorf("--search must be at most %d characters, got %d", MaxSearchLength, utf16Length(search))
	}
	return nil
}
