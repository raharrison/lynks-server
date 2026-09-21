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
- Session-based auth with optional **TOTP two-factor authentication**
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

```bash
./gradlew shadowJar
scp build/libs/lynks-server-*-all.jar vps:/opt/lynks/lynks-server.jar
```

On the host, as the `lynks` user with `/opt/lynks` as the working directory:

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
