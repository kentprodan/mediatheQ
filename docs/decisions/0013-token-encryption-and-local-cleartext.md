# 0013. Token encryption with the Android Keystore; cleartext HTTP only on local networks

- Status: Accepted
- Date: 2026-10-19

## Context

The access token is the key to the owner's whole library, so it must not sit on disk in plain text (architecture skill, section 10). Jetpack Security (`EncryptedSharedPreferences`) is deprecated. Google Tink works but is a large dependency for one string.

Home servers usually run plain HTTP on the LAN or over Tailscale. Android's network security config can allow cleartext per domain name, but not per IP range. It cannot say "any 192.168.x.x address" or "any Tailscale address", and the user types the address at runtime.

## Decision

- **Token:** AES-256-GCM with a key generated in and never leaving the Android Keystore (`AndroidKeystoreTokenCipher` in `:core:datastore`). No user authentication on the key, so playback can start without unlocking the phone. The DataStore holds Base64 of IV + ciphertext, with a fresh IV per encryption.
- **Unreadable token** (key lost after a restore or a security change, or corrupt data): `MqError.Storage`, which the app treats as signed out. It never crashes.
- **Backup is off** (`android:allowBackup="false"`). A restored DeviceId would collide with the original install (R9 in the jellyfin skill), and the Keystore key is not restored anyway.
- **Cleartext:** the manifest allows cleartext, and `CleartextPolicyInterceptor` in `:core:network` enforces the rule on every connection. It checks the IP the socket actually connected to, so LAN host names and Tailscale MagicDNS names work. Allowed: loopback, 10/8, 172.16/12, 192.168/16, link-local, IPv6 unique local (includes Tailscale `fd7a:115c:a1e0::/48`) and 100.64/10 (Tailscale). Everything else must use HTTPS.

## Consequences

- No new dependency. The real cipher is proven by a device test (`AndroidKeystoreTokenCipherTest`); JVM tests use `FakeTokenCipher`.
- 100.64/10 is also carrier-grade NAT, so cleartext to a CGNAT address on a mobile network would be allowed. This is accepted because the user typed that address.
- Media3 (playback, later issue) uses its own data source. It must use this OkHttp client, or repeat the same check.
- After a phone migration the user logs in again.
