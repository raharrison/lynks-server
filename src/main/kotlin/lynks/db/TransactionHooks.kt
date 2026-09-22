package lynks.db

import lynks.util.loggerFor
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

private val log = loggerFor<TransactionHook>()

private class TransactionHook(
    private val onCommit: Boolean,
    private val action: () -> Unit
) : StatementInterceptor {

    // A nested SQLException rolls back the outer transaction as well as the outer handler
    private var done = false

    override fun afterCommit(transaction: Transaction) {
        if (onCommit) run()
    }

    override fun afterRollback(transaction: Transaction) {
        if (!onCommit) run()
    }

    private fun run() {
        if (done) return
        done = true
        runCatching(action).onFailure { log.error("Transaction hook failed", it) }
    }
}

fun JdbcTransaction.afterCommit(action: () -> Unit) {
    registerInterceptor(TransactionHook(onCommit = true, action))
}

fun JdbcTransaction.onRollback(action: () -> Unit) {
    registerInterceptor(TransactionHook(onCommit = false, action))
}
