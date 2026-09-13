Example secret files for local Compose. Replace before any real deployment.

These values are placeholders, not production credentials.
Never commit a real .env, PKCS#8 signing key, AES data key, or production password file.

The AES-256 data key used to encrypt Enable Banking session ids is not stored
in this folder. Point WORTHLY_DATA_KEY_FILE at a local 32-byte file, or set
WORTHLY_CRYPTO_GENERATE_EPHEMERAL_DATA_KEY=true for disposable local Compose.
