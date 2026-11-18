package template

import (
	"context"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/otak/docfile"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// PushInput is one push: the .html file and the flags. Without ID a new template is created and Type
// and Language are required; with ID the template is updated, and an empty Type, Language or
// Name keeps the stored value.
type PushInput struct {
	File     string
	ID       string
	Type     string
	Language string
	Name     string
}

// PushResult is the template after the push and whether it was created.
type PushResult struct {
	Template client.DocumentTemplate
	Created  bool
	// Uploaded is true when the HTML and the PDF were uploaded.
	Uploaded bool
}

// Push creates or updates a template from an .html file. The file is read and rendered to PDF
// before the first call. A push takes two calls, the metadata then the files; when the files
// fail after a create, the error names the new template's id, so that the push can be repeated
// with --id.
func Push(ctx context.Context, c *client.Client, in PushInput) (PushResult, error) {
	target, err := resolveTarget(c, in)
	if err != nil {
		return PushResult{}, err
	}
	files, err := docfile.Load(ctx, in.File, target.docType)
	if err != nil {
		return PushResult{}, err
	}
	req := client.DocumentContent{Name: target.name, Type: target.docType, Language: target.language}

	var saved client.DocumentTemplate
	if in.ID == "" {
		saved, err = c.CreateTemplate(req)
	} else {
		saved, err = c.UpdateTemplate(in.ID, req)
	}
	if err != nil {
		return PushResult{}, err
	}
	result := PushResult{Template: saved, Created: in.ID == ""}
	withFiles, err := c.ReplaceTemplateFiles(saved.ID, files)
	if err != nil {
		if result.Created {
			return result, fmt.Errorf("created template %s, but its files were not uploaded, so it cannot be used yet; "+
				"run the push again with --id %s: %w", saved.ID, saved.ID, err)
		}
		return result, fmt.Errorf("updated template %s, but its files were not uploaded: %w", saved.ID, err)
	}
	result.Template = withFiles
	result.Uploaded = true
	return result, nil
}

type pushTarget struct {
	docType  string
	language string
	name     string
}

// resolveTarget settles type, language and name: from the flags, the stored template for what
// an update leaves out, and the file name for the name of a new template. An update may change
// the type, since a template's content no longer depends on it.
func resolveTarget(c *client.Client, in PushInput) (pushTarget, error) {
	var target pushTarget
	var err error
	if in.Type != "" {
		if target.docType, err = docfile.Type(in.Type); err != nil {
			return pushTarget{}, err
		}
	}
	if in.Language != "" {
		if target.language, err = docfile.Language(in.Language); err != nil {
			return pushTarget{}, err
		}
	}
	target.name = in.Name

	if in.ID == "" {
		if target.docType == "" || target.language == "" {
			return pushTarget{}, fmt.Errorf("a new template needs --type and --lang; pass --id to update an existing one")
		}
		if target.name == "" {
			target.name = docfile.BaseName(in.File)
		}
		return target, nil
	}

	stored, err := c.GetTemplate(in.ID)
	if err != nil {
		return pushTarget{}, err
	}
	if target.docType == "" {
		target.docType = stored.Type
	}
	if target.language == "" {
		target.language = stored.Language
	}
	if target.name == "" {
		target.name = stored.Name
	}
	return target, nil
}
