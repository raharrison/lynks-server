package db

import common.IdBasedCreatedEntity
import common.IdBasedNewEntity
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import util.RandomUtils
import util.combine

abstract class StringIdTable(name: String, length: Int = 12): Table(name) {
    val id = varchar("id", length)
    override val primaryKey = PrimaryKey(id)
}

abstract class GenericRepository<T : IdBasedCreatedEntity, in U : IdBasedNewEntity, E: StringIdTable>(private val table: E) {

    fun get(id: String): T? = transaction {
        baseQuery().combine { table.id eq id }
                .mapNotNull { toModel(it) }
                .singleOrNull()
    }

    fun add(entity: U): T = transaction {
        val newId = RandomUtils.generateUid()
        table.insert(toInsert(newId, entity))
        get(newId)!!
    }

    fun update(entity: U): T? {
        val id = entity.id
        return if (id == null) {
            add(entity)
        } else {
            transaction {
                val where = baseQuery().combine(identify(entity)).where!!
                val updated = table.update({ where }, body = {
                    toUpdate(entity)(it)
                })
                if(updated > 0) get(id)
                else null
            }
        }
    }

    fun update(entity: T): T? = transaction {
        val where = baseQuery().combine { table.id eq entity.id }.where!!
        table.update({ where }, body = {
            toUpdate(entity)(it)
        })
        get(entity.id)
    }

    fun delete(id: String): Boolean = transaction {
        table.deleteWhere { table.id eq id } > 0
    }

    protected open fun baseQuery(base: ColumnSet = table, where: E = table): Query = base.selectAll()

    protected open fun identify(entity: U): SqlExpressionBuilder.() -> Op<Boolean> = { table.id eq entity.id!! }

    protected abstract fun toInsert(eId: String, entity: U): E.(InsertStatement<*>) -> Unit

    protected abstract fun toUpdate(entity: U): E.(UpdateBuilder<*>) -> Unit

    protected abstract fun toUpdate(entity: T): E.(UpdateBuilder<*>) -> Unit

    protected abstract fun toModel(row: ResultRow): T

}
