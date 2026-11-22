import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { auth } from "@oba/shared/libs/auth";

import { fetchHtmlPrefix } from "@/lib/link-preview/fetch-html-prefix";
import { parsePreviewTarget } from "@/lib/link-preview/target-policy";

/**
 * Fetches the <title> tag from a given URL.
 * Used by the markdown editor's /link slash command to resolve page titles.
 * The server runs inside the cluster, so only public web targets are fetched; for any
 * other link the editor falls back to the host name.
 */
export async function GET(request: NextRequest): Promise<NextResponse> {
  const session = await auth();
  if (!session) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const url = request.nextUrl.searchParams.get("url");

  if (!url) {
    return NextResponse.json({ error: "Missing url parameter" }, { status: 400 });
  }

  if (!URL.canParse(url)) {
    return NextResponse.json({ error: "Invalid URL" }, { status: 400 });
  }

  const target = parsePreviewTarget(url);
  if (!target) {
    return NextResponse.json({ title: null });
  }

  try {
    const html = await fetchHtmlPrefix(target);
    const titleMatch = html?.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
    const title = titleMatch?.[1]?.trim().replace(/\s+/g, " ").slice(0, 200) ?? null;
    return NextResponse.json({ title });
  } catch (error) {
    // The editor shows the host name instead of a title, so the user still gets a usable link.
    console.warn(
      "[link-metadata] preview fetch failed",
      error instanceof Error ? error.message : error
    );
    return NextResponse.json({ title: null });
  }
}

export const runtime = "nodejs";
