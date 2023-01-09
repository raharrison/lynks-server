[![Kotlin](https://img.shields.io/badge/kotlin-1.8.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
![Build](https://github.com/raharrison/lynks-server/workflows/Build/badge.svg)

## Lynks Server - Self-hosted link and note manager

**Updated for Kotlin 1.8.0 + Ktor 2.2.2**

Server side for the Lynks project offering backend web services for entry management. Accompanied by [lynks-ui](https://github.com/raharrison/lynks-ui) project
which provides a front-end webapp.

### Libraries used:

 - [Ktor](https://github.com/ktorio/ktor) - Kotlin async web framework, using the [Netty](https://github.com/netty/netty) engine
 - [Exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
 - [Postgres](https://www.postgresql.org/) - Modern and scalable RDBMS as core data store
 - [H2](https://github.com/h2database/h2database) - Embeddable database for testing or standalone deployments
 - [HikariCP](https://github.com/brettwooldridge/HikariCP) - High performance JDBC connection pooling
 - [Flyway](https://flywaydb.org/) - Database migrations
 - [Konf](https://github.com/uchuhimo/konf) - Type-safe configuration management
 - [JUnit 5](https://junit.org/junit5/), [Mockk](https://github.com/mockk/mockk), [AssertJ](http://joel-costigliola.github.io/assertj/) and [Rest Assured](http://rest-assured.io/) for testing
 - [Kover](https://github.com/Kotlin/kotlinx-kover) for code coverage, publishing to [Codecov](https://about.codecov.io/) through GitHub Actions

### Features

- Create and manage a number of different entry types:
  - links - **bookmarks** with dynamic extraction capabilities
  - notes - **Markdown** text
  - snippet - small code or text segments
  - file - file uploads
- Manage your entries within **tags** and **collections** with hierarchy support
- Rich sorting, filtering and pagination capabilities
- **Full-text search** within notes and extracted webpage content (readable view)
- **Automatic extraction** of link content (screenshots), thumbnail generation and text content extraction - **keep the content forever** even if the site becomes unavailable
- Find and link to discussions about links on Reddit and Hacker News
- Automated YouTube metadata retrieval and **youtube-dl** integration
- Comment on your entries and upload additional file attachments
- Full history and audit on all entries - travel back in time to view or revert back to previous versions
- Scheduled adhoc and recurring **reminders** with notifications through the webapp, Pushover or email
- Scheduled digest emails to remind you of unread links in your collection
- Easy single command **Docker Compose** based deployment (`Ktor`, `Nginx` and `Express` components)

## Roadmap:

### Server

- markdown @abcde --> keep same link in markdown (@abcde) and then transform in html for correct title
- automated checks for dead links

### UI

- update to Angular 15 when libs are updated
- file sets with multiple uploads
- entry colours
- two-factor auth pages
- hint for users to create new collections with parents by path
