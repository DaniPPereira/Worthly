export function oauthBasicAuthorization(clientId: string, clientSecret: string): string {
  const encoded = Buffer.from(`${encodeURIComponent(clientId)}:${encodeURIComponent(clientSecret)}`).toString("base64");
  return `Basic ${encoded}`;
}
