package client

// The multipart part names and media types of a files upload of a document or document template
// (PUT .../files).
const (
	PartHTML        = "html"
	PartPDF         = "pdf"
	ContentTypeHTML = "text/html; charset=utf-8"
	ContentTypePDF  = "application/pdf"
)

// DocumentFiles is the HTML of a document or document template and the PDF rendered from it,
// uploaded together so that the two never disagree.
type DocumentFiles struct {
	// BaseName names the uploaded files: BaseName.html and BaseName.pdf.
	BaseName string
	HTML     []byte
	PDF      []byte
}

func (f DocumentFiles) parts() []Part {
	return []Part{
		{Name: PartHTML, FileName: f.BaseName + ".html", ContentType: ContentTypeHTML, Data: f.HTML},
		{Name: PartPDF, FileName: f.BaseName + ".pdf", ContentType: ContentTypePDF, Data: f.PDF},
	}
}
