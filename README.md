[![Kotlin](https://img.shields.io/badge/kotlin-1.6.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
![Build](https://github.com/raharrison/lynks-server/workflows/Build/badge.svg)

## Lynks Server - Self-hosted link and note manager

**Updated for Kotlin 1.6.10 + Ktor 1.6.8 + Exposed 0.37.3**

Server side for the Lynks project offering backend web services for entry management. Accompanied by [lynks-ui](https://github.com/raharrison/lynks-ui) project
which provides a front-end webapp.

### Libraries used:

 - [Ktor](https://github.com/ktorio/ktor) - Kotlin async web framework
 - [Exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
 - [Netty](https://github.com/netty/netty) - Async web server
 - [Postgres](https://www.postgresql.org/) - Modern and scalable RDBMS as core data store 
 - [H2](https://github.com/h2database/h2database) - Embeddable database for testing or standalone deployments
 - [HikariCP](https://github.com/brettwooldridge/HikariCP) - High performance JDBC connection pooling
 - [Jackson](https://github.com/FasterXML/jackson) - JSON serialization/deserialization
 - [JUnit 5](https://junit.org/junit5/), [Mockk](https://github.com/mockk/mockk), [AssertJ](http://joel-costigliola.github.io/assertj/) and [Rest Assured](http://rest-assured.io/) for testing
 
### Features

- Create and manage a number of different entry types:
  - links - bookmarks with dynamic extraction capabilities
  - notes - markdown text
  - snippet - small code or text segments
- Manage your entries within tags and collections with hierarchy support
- Rich sorting, filtering and paging capabilities
- Full-text search within notes and extracted webpage content (readable view)
- Automatic extraction of link content (screenshots), thumbnail generation and text content extraction - keep the content forever even if the site becomes unavailable
- Find and link to discussions about links on Reddit and Hacker News
- Automated Youtube metadata retrieval and youtube-dl integration
- Comment on your entries and add upload additional file attachments
- Full history and audit on all entries - travel back in time to view or revert back to previous versions
- Scheduled adhoc and recurring reminders with notifications through the webapp, Pushover or email
- Scheduled digest emails to remind you of unread links in your collection
- Easy single command Docker Compose based deployment
