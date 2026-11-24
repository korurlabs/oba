// Package docfile holds the file forms that the document commands and the Otak mail-template
// and outreach commands share: the .md mail file, the .html document file with its rendered
// PDF, the language and type values, and the files a download writes.
package docfile

import (
	"fmt"
	"path/filepath"
	"strings"
)

// The file extensions of a mail (.md) and of a document or document template (.html, with its
// rendered .pdf).
const (
	ExtMail = ".md"
	ExtHTML = ".html"
	ExtPDF  = ".pdf"
)

// languages maps every language flag value to the API's DocumentLanguage. us is English: the library
// names its US company versions us.
var languages = map[string]string{
	"nl": "NL",
	"tr": "TR",
	"en": "EN",
	"us": "EN",
}

// Language maps a language flag value (nl, tr, en or us, case ignored) to the API's language.
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
		return "", fmt.Errorf("--type is empty; use PROPOSAL, NDA, TERMS, CONTRACT, REPORT, SERVICE_SHEET, PRESENTATION or OTHER")
	}
	if t == mailType {
		return "", fmt.Errorf("MAIL is no document type; a mail is a mail template: use 'oba otak mail-template'")
	}
	return t, nil
}

// CheckDocumentFile refuses a file that is not .html, the only form a document or document
// template of any type takes. what names the target ("an NDA", "a document"). A .md file gets a
// pointer to the mail-template command.
func CheckDocumentFile(path, what string) error {
	ext := strings.ToLower(filepath.Ext(path))
	if ext == ExtHTML {
		return nil
	}
	if ext == ExtMail {
		return fmt.Errorf("%s is an %s file, rendered to PDF on upload, not %s; a %s mail is a mail template: "+
			"use 'oba otak mail-template upload'", what, ExtHTML, filepath.Base(path), ExtMail)
	}
	return fmt.Errorf("%s is an %s file, rendered to PDF on upload, not %s", what, ExtHTML, filepath.Base(path))
}

// CheckMailFile refuses a mail file that is not .md.
func CheckMailFile(path string) error {
	if strings.ToLower(filepath.Ext(path)) != ExtMail {
		return fmt.Errorf("a mail is a %s file (subject on the first line, then the body), not %s", ExtMail, filepath.Base(path))
	}
	return nil
}

// BaseName is a file name without its extension, as the default name of an uploaded file.
func BaseName(path string) string {
	base := filepath.Base(path)
	return strings.TrimSuffix(base, filepath.Ext(base))
}
