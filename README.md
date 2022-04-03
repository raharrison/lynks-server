![Build](https://github.com/raharrison/lynks-server/workflows/Build/badge.svg)

## Lynks Server - Self-hosted link and note manager

**Updated for Kotlin 1.6.10 + Ktor 1.6.7 + Exposed 0.36.2**

Server side for the Lynks project offering backend web services for entry management. Accompanied by lynks-ui project
for front-end.

### Libraries used:

 - [Ktor](https://github.com/ktorio/ktor) - Kotlin async web framework
 - [Exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
 - [Netty](https://github.com/netty/netty) - Async web server
 - [H2](https://github.com/h2database/h2database) - Embeddable database
 - [HikariCP](https://github.com/brettwooldridge/HikariCP) - High performance JDBC connection pooling
 - [Jackson](https://github.com/FasterXML/jackson) - JSON serialization/deserialization
 - [JUnit 5](https://junit.org/junit5/), [Mockk](https://github.com/mockk/mockk), [AssertJ](http://joel-costigliola.github.io/assertj/) and [Rest Assured](http://rest-assured.io/) for testing
 
### Features

- Notes with markdown and URL (link) management
- Manage items with nested tags and collections, sorting, filtering and paging
- Automatic retrieval of link content (screenshot), thumbnail generation and text content extraction
- Find discussions about links on Reddit and Hacker News
- Full text search within note and webpage content
- Automated Youtube metadata retrieval and youtube-dl integration
- Comment on entries and add file attachments
- Scheduled and recurring reminder notifications through email or web sockets
- Scheduled digest email of unread links



## TODO:

### Server

- create register, login, logout endpoints
- file entry type
- create default user as migration
- emails for reminders

### UI

- show badge of number of unread notifications
