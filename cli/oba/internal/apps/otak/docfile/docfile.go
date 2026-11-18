// Package docfile holds the file forms that the Otak template, mail-template and outreach
// commands share: the .md mail file, the .html template file, the --lang and --type
// values and the files a pull writes.
package docfile

import (
	"fmt"
	"path/filepath"
	"strings"
)

// The file extensions of a mail (.md) and of a document template (.html, with its rendered .pdf).
const (
	ExtMail = ".md"
	ExtHTML = ".html"
	ExtPDF  = ".pdf"
)

// languages maps every --lang value to the API's DocumentLanguage. us is English: the library
// names its US company versions us.
var languages = map[string]string{
	"nl": "NL",
	"tr": "TR",
	"en": "EN",
	"us": "EN",
}

// Language maps a --lang value (nl, tr, en or us, case ignored) to the API's language.
func Language(value string) (string, error) {
	lang, ok := languages[strings.ToLower(strings.TrimSpace(value))]
	if !ok {
		return "", fmt.Errorf("unknown language %q; use nl, tr, en or us (English)", value)
	}
	return lang, nil
}

// mailType is the document type that mails had before they became mail templates of their own.
// It is refused locally with a pointer to the mail-template command.
const mailType = "MAIL"

// Type turns a --type value into the API's DocumentType: upper case, a hyphen read as an
// underscore (service-sheet is SERVICE_SHEET). MAIL is refused, since mails are mail templates
// now; the API checks every other value itself.
func Type(value string) (string, error) {
	t := strings.ToUpper(strings.ReplaceAll(strings.TrimSpace(value), "-", "_"))
	if t == "" {
		return "", fmt.Errorf("--type is empty; use PROPOSAL, NDA, TERMS, REPORT, SERVICE_SHEET, PRESENTATION or OTHER")
	}
	if t == mailType {
		return "", fmt.Errorf("MAIL is no document type; a mail is a mail template: use 'oba otak mail-template'")
	}
	return t, nil
}

// CheckDocumentFile refuses a template file that is not .html, the only form a document template
// of any type takes. A .md file gets a pointer to the mail-template command.
func CheckDocumentFile(path, docType string) error {
	ext := strings.ToLower(filepath.Ext(path))
	if ext == ExtHTML {
		return nil
	}
	if ext == ExtMail {
		return fmt.Errorf("a %s is an %s file, rendered to PDF on upload, not %s; a %s mail is a mail template: "+
			"use 'oba otak mail-template push'", docType, ExtHTML, filepath.Base(path), ExtMail)
	}
	return fmt.Errorf("a %s is an %s file, rendered to PDF on upload, not %s", docType, ExtHTML, filepath.Base(path))
}

// CheckMailFile refuses a mail file that is not .md.
func CheckMailFile(path string) error {
	if strings.ToLower(filepath.Ext(path)) != ExtMail {
		return fmt.Errorf("a mail is a %s file (subject on the first line, then the body), not %s", ExtMail, filepath.Base(path))
	}
	return nil
}

// BaseName is a file name without its extension, as the default name of a pushed file.
func BaseName(path string) string {
	base := filepath.Base(path)
	return strings.TrimSuffix(base, filepath.Ext(base))
}
