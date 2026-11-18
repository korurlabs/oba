import type { NextRequest } from 'next/server';
import { NextResponse } from 'next/server';
import { auth } from '@oba/shared/libs/auth';

/**
 * Validates that a URL is safe to fetch (not internal/private).
 * Blocks localhost, private IPs, link-local, cloud metadata endpoints,
 * and non-HTTP(S) protocols.
 */
function isSafeUrl(urlString: string): boolean {
  let parsed: URL;
  try {
    parsed = new URL(urlString);
  } catch {
    return false;
  }

  // Only allow http and https protocols
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return false;
  }

  const hostname = parsed.hostname.toLowerCase();

  // Block localhost variants
  if (
    hostname === 'localhost' ||
    hostname === '127.0.0.1' ||
    hostname === '::1' ||
    hostname === '0.0.0.0' ||
    hostname === '[::1]'
  ) {
    return false;
  }

  // Block numeric/hex/octal IP representations that bypass dotted-decimal checks
  if (/^\d+$/.test(hostname)) return false;           // decimal IP like 2130706433
  if (/^0x[0-9a-f]+$/i.test(hostname)) return false;  // hex IP like 0x7f000001
  if (/^0\d/.test(hostname)) return false;             // octal like 0177.0.0.1

  // Block private and reserved IP ranges
  const ipMatch = hostname.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipMatch) {
    const [, a, b] = ipMatch.map(Number);

    // 10.0.0.0/8
    if (a === 10) return false;

    // 172.16.0.0/12
    if (a === 172 && b !== undefined && b >= 16 && b <= 31) return false;

    // 192.168.0.0/16
    if (a === 192 && b === 168) return false;

    // 169.254.0.0/16 (link-local + cloud metadata)
    if (a === 169 && b === 254) return false;

    // 0.0.0.0/8
    if (a === 0) return false;
  }

  // Block IPv6 private/internal ranges
  const cleanHost = hostname.replace(/^\[|\]$/g, '');
  const ipv6Lower = cleanHost.toLowerCase();
  if (
    ipv6Lower.startsWith('::ffff:') ||   // IPv4-mapped IPv6
    ipv6Lower.startsWith('fc') ||         // Unique local (fc00::/7)
    ipv6Lower.startsWith('fd') ||         // Unique local (fd00::/8)
    ipv6Lower.startsWith('fe80') ||       // Link-local
    ipv6Lower === '::' ||                 // Unspecified
    ipv6Lower === '::1' ||                // Loopback (also caught above)
    ipv6Lower.startsWith('100::')         // Discard prefix
  ) {
    return false;
  }

  return true;
}

/**
 * Fetches a URL with manual redirect handling to prevent SSRF via redirect chains.
 * Each redirect target is validated against isSafeUrl before following.
 */
async function safeFetch(url: string, maxRedirects: number = 3): Promise<Response> {
  let currentUrl = url;
  for (let i = 0; i <= maxRedirects; i++) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
      const response = await fetch(currentUrl, {
        signal: controller.signal,
        redirect: 'manual',
        headers: {
          'User-Agent': 'Mozilla/5.0 (compatible; ObaLinkBot/1.0)',
          Accept: 'text/html',
        },
      });

      if ([301, 302, 303, 307, 308].includes(response.status)) {
        const location = response.headers.get('location');
        if (!location) return response;

        // Resolve relative URLs against the current request URL
        const resolvedUrl = new URL(location, currentUrl).toString();
        if (!isSafeUrl(resolvedUrl)) {
          throw new Error('Redirect to blocked URL');
        }
        currentUrl = resolvedUrl;
        continue;
      }

      return response;
    } finally {
      clearTimeout(timeout);
    }
  }
  throw new Error('Too many redirects');
}

/**
 * Fetches the <title> tag from a given URL.
 * Used by the markdown editor's /link slash command to resolve page titles.
 */
export async function GET(request: NextRequest): Promise<NextResponse> {
  // Auth check — require a valid session
  const session = await auth();
  if (!session) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const url = request.nextUrl.searchParams.get('url');

  if (!url) {
    return NextResponse.json({ error: 'Missing url parameter' }, { status: 400 });
  }

  try {
    new URL(url);
  } catch {
    return NextResponse.json({ error: 'Invalid URL' }, { status: 400 });
  }

  // SSRF protection — block internal/private URLs
  if (!isSafeUrl(url)) {
    return NextResponse.json({ title: null });
  }

  try {
    const response = await safeFetch(url);

    if (!response.ok) {
      return NextResponse.json({ title: null });
    }

    // Only read first 16KB to find the title — no need to download the full page
    const reader = response.body?.getReader();
    if (!reader) {
      return NextResponse.json({ title: null });
    }

    const decoder = new TextDecoder();
    let html = '';
    const maxBytes = 16384;

    while (html.length < maxBytes) {
      const { done, value } = await reader.read();
      if (done) break;
      html += decoder.decode(value, { stream: true });

      // Check if we already have the closing </title> tag
      if (html.includes('</title>')) break;
    }

    reader.cancel();

    const titleMatch = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
    const title = titleMatch?.[1]?.trim().replace(/\s+/g, ' ').slice(0, 200) ?? null;

    return NextResponse.json({ title });
  } catch {
    return NextResponse.json({ title: null });
  }
}

export const runtime = 'nodejs';
