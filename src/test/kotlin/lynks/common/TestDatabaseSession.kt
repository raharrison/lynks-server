package lynks.common

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener

// Environment reads its config once, on first use by any test, so the container has to be set before any test runs
class TestDatabaseSession : LauncherSessionListener {
    override fun launcherSessionOpened(session: LauncherSession) = TestPostgresContainer.configure()
}
