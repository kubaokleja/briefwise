// Links in news and summaries point to third-party articles. The URL is untrusted data: a `javascript:` or `data:`
// URL in an href would run code on click. Only http(s) URLs become links; anything else is shown as plain text.

/** The URL if it is an absolute http(s) URL, otherwise undefined. */
export function safeHref(url: string | null | undefined): string | undefined {
  if (!url) {
    return undefined;
  }
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : undefined;
  } catch {
    return undefined;
  }
}
