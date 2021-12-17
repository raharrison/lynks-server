package lynks.db

enum class DatabaseDialect(val driver: String) {
    H2("org.h2.Driver"),
    POSTGRES("org.postgresql.Driver")
}
