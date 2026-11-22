import type { NextRequest } from 'next/server';
import { NextResponse } from 'next/server';
import path from 'path';

import { readUpload } from './read-upload';

// MIME type mapping
const MIME_TYPES: Record<string, string> = {
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.pdf': 'application/pdf',
  '.doc': 'application/msword',
  '.docx':
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  '.xls': 'application/vnd.ms-excel',
  '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  '.txt': 'text/plain',
  '.csv': 'text/csv',
  '.zip': 'application/zip',
};

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  try {
    const { path: pathSegments } = await params;
    const result = await readUpload(
      path.join(process.cwd(), 'public', 'uploads'),
      pathSegments
    );

    if (result.kind === 'invalid') {
      return NextResponse.json({ error: 'Invalid path' }, { status: 400 });
    }
    if (result.kind === 'not-found') {
      return NextResponse.json({ error: 'File not found' }, { status: 404 });
    }

    const { data: fileBuffer, fileName: filename } = result;
    const ext = path.extname(filename).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    // For downloadable files (non-images), suggest download
    const isImage = contentType.startsWith('image/');

    const headers: HeadersInit = {
      'Content-Type': contentType,
      'Cache-Control': 'public, max-age=31536000, immutable',
    };

    if (!isImage) {
      headers['Content-Disposition'] = `attachment; filename="${filename}"`;
    }

    return new NextResponse(fileBuffer, { headers });
  } catch (error) {
    console.error('File serve error:', error);
    return NextResponse.json(
      { error: 'Failed to serve file' },
      { status: 500 }
    );
  }
}

export const runtime = 'nodejs';
