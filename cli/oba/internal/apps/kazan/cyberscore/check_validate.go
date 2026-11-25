package cyberscore

import (
	"fmt"
	"strings"
	"unicode/utf8"
)

// checkRequiredText refuses a blank value or one longer than max characters.
func checkRequiredText(flag, value string, max int) error {
	if strings.TrimSpace(value) == "" {
		return fmt.Errorf("%s cannot be empty", flag)
	}
	return checkLength(flag, value, max)
}

// checkLength refuses a value longer than max characters.
func checkLength(flag, value string, max int) error {
	if n := utf8.RuneCountInString(value); n > max {
		return fmt.Errorf("%s must be at most %d characters, got %d", flag, max, n)
	}
	return nil
}

// checkRange refuses a number outside min to max.
func checkRange(flag string, value, min, max int) error {
	if value < min || value > max {
		return fmt.Errorf("%s must be between %d and %d, got %d", flag, min, max, value)
	}
	return nil
}

// optionalCategory parses a category value; an empty one is nil, which the API stores as no
// category.
func optionalCategory(value string) (*string, error) {
	if strings.TrimSpace(value) == "" {
		return nil, nil
	}
	category, err := ParseCategory(value)
	if err != nil {
		return nil, err
	}
	return &category, nil
}

// optionalText is a value the API stores as null when it is empty.
func optionalText(value string) *string {
	if value == "" {
		return nil
	}
	return &value
}
