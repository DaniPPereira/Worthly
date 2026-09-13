# ADR-006 --- Mobile Session and Device Security

Refresh credentials live only in Keychain/Keystore-backed secure
storage. Access tokens are memory-first. Biometrics re-unlock locally
protected session material and never replace backend auth. Verified
HTTPS App/Universal Links are preferred. Push payloads are
non-sensitive. Device sessions are revocable from Worthly.
