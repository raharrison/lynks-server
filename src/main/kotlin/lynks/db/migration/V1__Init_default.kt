package lynks.db.migration

import lynks.common.Entries
import lynks.common.EntryType
import lynks.common.EntryVersions
import lynks.common.Environment
import lynks.db.DatabaseDialect
import lynks.db.DatabaseFactory
import lynks.db.EntryVersionTrigger
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection

class V1__Init_default : BaseJavaMigration() {

    override fun migrate(context: Context) {
        DatabaseFactory().createAll()
        val connection = context.connection
        if (Environment.database.dialect == DatabaseDialect.H2) {
            createH2EntryAuditTriggers(connection)
            enableH2EntrySearch(connection)
        } else if(Environment.database.dialect == DatabaseDialect.POSTGRES) {
            createPostgresEntryAuditTriggers(connection)
            createPostgresEntrySearch(connection)
        }
    }

    private fun createH2EntryAuditTriggers(conn: Connection) {
        conn.createStatement().use {
            it.execute(
                "CREATE TRIGGER IF NOT EXISTS ENTRY_VERS_INS AFTER INSERT ON ${Entries.tableName} " +
                    "FOR EACH ROW CALL \"${EntryVersionTrigger::class.qualifiedName}\""
            )
            it.execute(
                "CREATE TRIGGER IF NOT EXISTS ENTRY_VERS_UPD AFTER UPDATE ON ${Entries.tableName} " +
                    "FOR EACH ROW CALL \"${EntryVersionTrigger::class.qualifiedName}\""
            )
        }
    }

    private fun enableH2EntrySearch(conn: Connection) {
        conn.createStatement().use {
            it.execute("CREATE ALIAS IF NOT EXISTS FT_INIT FOR \"org.h2.fulltext.FullText.init\";")
            it.execute("CALL FT_INIT();")
            it.execute("CALL FT_CREATE_INDEX('PUBLIC', '${Entries.tableName}', '${Entries.title.name},${Entries.plainContent.name}');")
        }
    }

    private fun createPostgresEntrySearch(conn: Connection) {
        conn.createStatement().use {
            it.execute("""
                ALTER TABLE ${Entries.tableName} ADD COLUMN TS_DOC tsvector
                    GENERATED ALWAYS AS (
                        case
                            when ${Entries.type.name} = ${EntryType.LINK.ordinal} then
                                                (setweight(to_tsvector('english', ${Entries.title.name}), 'A') ||
                                                setweight(to_tsvector('english', coalesce(${Entries.plainContent.name}, '')), 'A') ||
                                                setweight(to_tsvector('english', coalesce(${Entries.content.name}, '')), 'B'))
                            else (setweight(to_tsvector('english', ${Entries.title.name}), 'A') ||
                                  setweight(to_tsvector('english', coalesce(${Entries.plainContent.name}, '')), 'B'))
                        end
                        )
                 STORED;
            """.trimIndent())
            it.execute("CREATE INDEX ts_doc_idx ON ${Entries.tableName} USING GIN (TS_DOC);")
        }
    }

    private fun createPostgresEntryAuditTriggers(conn: Connection) {
        val colSet = Entries.columns.joinToString(", ") { it.name }
        val newValSet = Entries.columns.joinToString(", ") { "NEW.${it.name}" }
        conn.createStatement().use {
            it.execute("""
                CREATE OR REPLACE FUNCTION audit_entry_changes()
                  RETURNS TRIGGER
                  LANGUAGE PLPGSQL
                  AS
                $$
                BEGIN
                    IF (TG_OP = 'UPDATE') THEN
                	    IF OLD.version = NEW.version THEN
                            RETURN NULL;
                        END IF;
                    END IF;

                    INSERT INTO ${EntryVersions.tableName}($colSet) SELECT $newValSet;

                    RETURN NEW;
                END;
                $$
            """.trimIndent())
            it.execute("""
                CREATE TRIGGER entry_changes
                    AFTER INSERT OR UPDATE ON ${Entries.tableName}
                    FOR EACH ROW
                    EXECUTE PROCEDURE audit_entry_changes();
            """.trimIndent())
        }
    }

}
