import { useEffect, useState } from "react";

interface FileObjectUrl {
  file: File;
  url: string;
}

/**
 * An object URL for a local file, so a file that is not uploaded yet can be
 * shown, e.g. as an image. Null until the URL exists and when `file` is null.
 *
 * The URL is revoked when the file changes and when the caller unmounts: an
 * object URL keeps the whole file in memory until it is revoked, and a message
 * may carry ten large images. Callers pass only files they render as images.
 */
export function useFileObjectUrl(file: File | null): string | null {
  const [entry, setEntry] = useState<FileObjectUrl | null>(null);

  useEffect(() => {
    if (!file) return undefined;
    const url = URL.createObjectURL(file);
    setEntry({ file, url });
    return () => URL.revokeObjectURL(url);
  }, [file]);

  // A URL made for the previous file is already revoked, so it is never returned.
  return entry && entry.file === file ? entry.url : null;
}
