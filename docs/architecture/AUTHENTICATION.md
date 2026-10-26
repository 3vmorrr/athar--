# Authentication and verification
Web administration uses an isolated cookie session with CSRF. Android uses opaque random access tokens (15 minutes) and rotating refresh tokens (30 days maximum), stored hashed server-side. Each token references the user `session_version`; password changes, suspension and account deletion invalidate credentials. Refresh rotation is transactional; reuse revokes its family.

Registration validates display name, unique lowercase 3–24 ASCII username, email, 10–200 character password and confirmation. An unverified user receives a verification-scoped credential only, permitting OTP/resend/logout and no content API. OTP uses a keyed server hash, 10-minute expiry and five transactional attempts. Issuance/resend are rate-limited independently of PHP cookies. SMTP failure returns `delivery_unavailable` and a pending account; never state recipient delivery merely because the account row exists.

Recovery responds generically to avoid email enumeration and applies shared rate limits. Validate codes with the same hashing/attempt service. A live OTP, password or raw bearer/refresh token must never appear in logs or admin UI. See [API contract](API_CONTRACT.md).


## Captured-owner extension — task02.7.1

Account-specific callbacks bind the intended actor before coroutine dispatch; private repository entry points capture default identity before IO/lock/index waits. Current verified/full SignedIn credentials are also required, including offline; matching an actor ID after reauthentication into Pending is insufficient. The native session request boundary preserves narrow verify/resend allowance and the dedicated owned logout flow. Pending data/ciphertext remain retained after stale/suspended actions, rather than being retargeted or acknowledged under a new account.53 JVM/build/lint/306 resources, Arabic10/English32 hardware cases and main API1 pass; see [owned mutation details](../features/OWNED_MUTATION_DISPATCH.md). No backend/schema contract changes or new UI motion.
