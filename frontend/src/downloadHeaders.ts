export function downloadReference(response: Response, header: string): string | undefined {
  const reference = response.headers.get(header);
  return reference && /^[0-9a-f-]{36}$/i.test(reference) ? reference : undefined;
}

export function downloadFilename(response: Response, fallback: string): string {
  const disposition = response.headers.get('Content-Disposition');
  return disposition?.match(/filename="?([A-Za-z0-9._-]+)"?/)?.[1] ?? fallback;
}
