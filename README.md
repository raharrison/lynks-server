[![Kotlin](https://img.shields.io/badge/kotlin-2.4-blue.svg?logo=kotlin)](http://kotlinlang.org)
![Build](https://github.com/raharrison/lynks-server/workflows/Build/badge.svg)

## Lynks Server - Self-hosted link and note manager

**Kotlin 2.4 + Ktor 3.6**

Backend API service for the Lynks project. Accompanied by [lynks-ui](https://github.com/raharrison/lynks-ui) which provides the frontend webapp.

### Libraries

- [Ktor](https://github.com/ktorio/ktor) - Kotlin async web framework on the [Netty](https://github.com/netty/netty) engine
- [Exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
- [Postgres](https://www.postgresql.org/) - Data store, with [Testcontainers](https://testcontainers.com/) for tests
- [HikariCP](https://github.com/brettwooldridge/HikariCP) - High performance JDBC connection pooling
- [Flyway](https://flywaydb.org/) - Database migrations
- [Konf](https://github.com/uchuhimo/konf) - Type-safe configuration management
- [JUnit 6](https://junit.org/), [Mockk](https://github.com/mockk/mockk), [AssertJ](http://joel-costigliola.github.io/assertj/) and [Rest Assured](http://rest-assured.io/) for testing

### Features

- Create and manage multiple entry types:
  - **Links** - bookmarks with automatic content extraction and screenshot capture
  - **Notes** - rich Markdown text
  - **Snippets** - small code or text segments
  - **Files** - file uploads
- Organise entries with **tags** and **collections** (with hierarchy support)
- Rich sorting, filtering and pagination
- **Full-text search** across notes and extracted webpage content
- **Automatic archiving** - screenshots, thumbnails and readable text extraction so content is preserved even if the original site goes offline
- Reddit and Hacker News discussion discovery for links
- YouTube metadata retrieval via the YouTube Data API
- Comments and additional file attachments per entry
- Full **audit history** - view or revert to any previous version of an entry
- Scheduled adhoc and recurring **reminders**, delivered in-app or handed off to [jolt](https://github.com/raharrison)
- Weekly **digest** page of unread links, regenerated on a schedule
- **Multiple users**, each with their own entries, tags, collections, reminders,
  notifications and digest
- Session-based auth with optional **TOTP two-factor authentication**, and optional **single sign-on** through a self-hosted OpenID Connect provider such as Authelia
- Runs as a plain systemd service; only Postgres and the scraper use containers

### Build & Run

```bash
docker compose -f compose.yaml up -d   # Postgres for local work
./gradlew build            # Full build + unit tests
./gradlew test             # Unit tests only
./gradlew testIntegration  # Integration tests
./gradlew run              # Run on port 8080
./gradlew shadowJar        # Fat jar -> build/libs/lynks-server-<version>-all.jar
```

### Deployment

Nothing here runs in a container except Postgres, and the scraper in its own repo.

Pushing a version tag (e.g. `2.1.0`, matching `version` in `build.gradle.kts`) runs
the `Release` workflow, which builds and copies `lynks-server-<version>-all.jar`
to `API_TARGET_PATH` on the host. Point `/home/lynks/lynks/lynks-server.jar` at it and
restart the service. `lynks-ui` does the same for `dist/`, into
`UI_TARGET_PATH<version>/`.

On the host, as the `lynks` user with `/home/lynks/lynks` as the working directory:

| Piece    | How it runs                                                                                |
|----------|--------------------------------------------------------------------------------------------|
| Postgres | `docker compose up -d`, published on `127.0.0.1:5432`, credentials in `config/.env`        |
| API      | `config/lynks-server.service` installed to `/etc/systemd/system`, serving `127.0.0.1:8080` |
| UI       | `npm run build` in `lynks-ui`, `dist/` copied to `/var/www/lynks`                          |
| Scraper  | `docker compose up -d` in `lynks-scraper`, published on `127.0.0.1:3000`                   |
| nginx    | `config/nginx.conf` as a site, serving the UI and proxying `/api`                          |

Config lives in `config/lynks.config.json` next to the jar, resolved relative to
the service's `WorkingDirectory`. `CONFIG_MODE` is read with `System.getProperty`,
so it has to be `-DCONFIG_MODE=prod` on the command line rather than an environment
variable; the unit file already does this.

**The scraper must see the media directory at the same absolute path the API
uses.** The API sends absolute target paths, so `compose.yaml` there mounts
`$LYNKS_MEDIA` at `$LYNKS_MEDIA` and runs the container as the `lynks` uid/gid.
Mount it elsewhere, or run it as another user, and the scraper writes files the
API cannot find or read.

### Users

Every entry, tag, collection, notification and digest belongs to one user, and
nothing is shared between users.

On first start the API creates `auth.defaultUserName` with `auth.defaultUserPassword`
(raw text or a bcrypt hash). With `auth.enabled` false there is no login and every
request acts as that user, so it is created whenever it is missing, with a random
password if none is configured.

Every other account is created by an admin: `scripts/manage_users.py` manages
accounts directly in Postgres, reading the credentials from `config/.env`:

```bash
cd scripts
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt

python manage_users.py list
python manage_users.py create alice             # prompts for a password
python manage_users.py create bob --inactive
python manage_users.py activate bob
python manage_users.py deactivate bob           # signed in sessions stop working at once
python manage_users.py set-password alice      # also signs alice out everywhere
python manage_users.py revoke-sessions alice
python manage_users.py unlink-sso alice
python manage_users.py delete bob               # asks you to type the username to confirm
```

Sessions live in Postgres, so they survive restarts. One expires after
`auth.session.idleDays` without use (default 30) and `auth.session.maxDays` after
sign in (default 90). Users can see and sign out their sessions under
Settings > Security, and changing a password signs out every other session.

### Single sign-on

With `auth.oidc` configured, the login page offers a single sign-on button next to
the password form. Lynks is a confidential OpenID Connect client using the
authorization code flow with PKCE. The server does the whole exchange, and the
browser only ever holds the lynks session cookie. Lynks still owns its users: SSO
signs in an existing lynks user and never creates one.

```json
"auth": {
    "enabled": true,
    "oidc": {
        "enabled": true,
        "issuer": "https://auth.example.com",
        "clientId": "lynks",
        "clientSecret": "...",
        "redirectUri": "https://lynks.example.com/api/auth/oidc/callback"
    }
}
```

Register the same `redirectUri` with the provider, with PKCE (`S256`) required and
the `openid` and `profile` scopes. Who may use lynks at all is the provider's access
policy, including whether it demands two factors. Lynks TOTP applies only to
password sign in.

The first time an identity signs in, it is linked to the lynks user whose username
exactly matches the provider's `preferred_username`, so keep the usernames the same
in both. After that only the provider's `sub` counts, so renaming either side
changes nothing. If the provider loses its storage it issues new subjects; clear
the old links with `manage_users.py unlink-sso` and each user relinks on their next
sign in, so back its storage up.

Locally, point `auth.oidc` in `config/lynks.config.json` at a dev provider, such as
the one in the separate `auth` project: issuer `https://127.0.0.1:9091`, client
`lynks-dev`, and redirect uri `http://localhost:3000/api/auth/oidc/callback` through
the Vite proxy. The JVM running the API has to trust that provider's self-signed
certificate.

Password sign in stays on as the fallback. `auth.passwordLoginEnabled: false`
makes lynks SSO only; turning it back on and restarting is the way back in if the
provider is gone. The provider is contacted on first use rather than at startup, so
lynks starts and password sign in works while it is down.

Jolt reminders go to each user's own inbound channel, set under Settings > Profile.
`external.joltHost` only says which Jolt server to use.

Deleting a user removes their database rows. Their resource files have no rows
left, so `OrphanResourceCleanupWorker` removes them once they are a day old.

### Backups

`scripts/backup_lynks.sh` dumps Postgres with `scripts/backup_db.sh`, then pushes
the dump plus `media/` (minus `media/temp`) to restic. It assumes the install at
`/home/$USER/lynks`, with the restic binary and `restic.properties` under `~/bak`.
The dump runs `docker exec` against the `lynks-postgres-1` container, so the user
running it has to be in the `docker` group. Nothing schedules it; run it from cron
or a systemd timer.
