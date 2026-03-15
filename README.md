[![Kotlin](https://img.shields.io/badge/kotlin-2.3-blue.svg?logo=kotlin)](http://kotlinlang.org)
![Build](https://github.com/raharrison/lynks-server/workflows/Build/badge.svg)

## Lynks Server - Self-hosted link and note manager

**Kotlin 2.3 + Ktor 3.4**

Backend API service for the Lynks project. Accompanied by [lynks-ui](https://github.com/raharrison/lynks-ui) which provides the frontend webapp.

### Libraries

- [Ktor](https://github.com/ktorio/ktor) - Kotlin async web framework on the [Netty](https://github.com/netty/netty) engine
- [Exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
- [Postgres](https://www.postgresql.org/) - Primary production data store
- [H2](https://github.com/h2database/h2database) - Embeddable database for testing and standalone deployments
- [HikariCP](https://github.com/brettwooldridge/HikariCP) - High performance JDBC connection pooling
- [Flyway](https://flywaydb.org/) - Database migrations
- [Konf](https://github.com/uchuhimo/konf) - Type-safe configuration management
- [JUnit 5](https://junit.org/junit5/), [Mockk](https://github.com/mockk/mockk), [AssertJ](http://joel-costigliola.github.io/assertj/) and [Rest Assured](http://rest-assured.io/) for testing
- [Kover](https://github.com/Kotlin/kotlinx-kover) for code coverage

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
- YouTube metadata retrieval and **yt-dlp** integration
- Comments and additional file attachments per entry
- Full **audit history** - view or revert to any previous version of an entry
- Scheduled adhoc and recurring **reminders** with notifications via webapp, Pushover or email
- Scheduled **digest emails** for unread links
- Session-based auth with optional **TOTP two-factor authentication**
- Docker Compose deployment (`lynks-server`, `lynks-ui`, `lynks-scraper`, `nginx`, `postgres`)

### Build & Run

```bash
./gradlew build            # Full build + unit tests
./gradlew test             # Unit tests only
./gradlew testIntegration  # Integration tests
./gradlew run              # Run on port 8080
```
