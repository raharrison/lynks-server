package lynks.db.migration

import lynks.common.Entries
import lynks.common.EntryType
import lynks.common.EntryVersions
import lynks.db.DatabaseFactory
import lynks.resource.Resources
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection

class V1__Init_default : BaseJavaMigration() {

    override fun migrate(context: Context) {
        DatabaseFactory().createAll()
        val connection = context.connection
        createPostgresEntryAuditTriggers(connection)
        createPostgresEntrySearch(connection)
        deferResourceEntryKey(connection)
    }

    // Pasted images are attached while the markdown is processed, before the entry row is inserted
    private fun deferResourceEntryKey(conn: Connection) {
        conn.createStatement().use {
            it.execute("ALTER TABLE ${Resources.tableName} ALTER CONSTRAINT ${Resources.ENTRY_FK} DEFERRABLE INITIALLY DEFERRED")
        }
    }

    private fun createPostgresEntrySearch(conn: Connection) {
        conn.createStatement().use {
            it.execute("""
                ALTER TABLE ${Entries.tableName} ADD COLUMN TS_DOC tsvector
                    GENERATED ALWAYS AS (
                        case
                            when ${Entries.type.name} = '${EntryType.LINK.name}' then
                                                (setweight(to_tsvector('english', coalesce(${Entries.title.name}, '')), 'A') ||
                                                setweight(to_tsvector('english', coalesce(regexp_replace(${Entries.plainContent.name}, '[/:._-]', ' ', 'g'), '')), 'A') ||
                                                setweight(to_tsvector('english', coalesce(${Entries.content.name}, '')), 'B'))
                            else (setweight(to_tsvector('english', coalesce(${Entries.title.name}, '')), 'A') ||
                                  setweight(to_tsvector('english', coalesce(${Entries.plainContent.name}, '')), 'B'))
                        end
                        )
                 STORED;
            """.trimIndent())
            it.execute("CREATE INDEX ts_doc_idx ON ${Entries.tableName} USING GIN (TS_DOC);")
            it.execute("CREATE INDEX entries_title_lower_idx ON ${Entries.tableName} (lower(${Entries.title.name}));")
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
