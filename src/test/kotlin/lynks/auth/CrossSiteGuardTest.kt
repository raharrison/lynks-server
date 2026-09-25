package lynks.auth

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import lynks.installPlugins
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CrossSiteGuardTest {

    private fun guarded(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installPlugins()
            install(CrossSiteGuard)
        }
        routing {
            get("/thing") { call.respond(HttpStatusCode.OK) }
            post("/thing") { call.respond(HttpStatusCode.OK) }
            put("/thing") { call.respond(HttpStatusCode.OK) }
            delete("/thing") { call.respond(HttpStatusCode.OK) }
        }
        block()
    }

    @Test
    fun testSameOriginWritesPass() = guarded {
        assertThat(client.post("/thing") { header("Sec-Fetch-Site", "same-origin") }.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun testNonBrowserWritesPass() = guarded {
        assertThat(client.post("/thing").status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun testCrossSiteAndSameSiteWritesAreRefused() = guarded {
        listOf("same-site", "cross-site", "none").forEach { site ->
            assertThat(client.post("/thing") { header("Sec-Fetch-Site", site) }.status).isEqualTo(HttpStatusCode.Forbidden)
            assertThat(client.put("/thing") { header("Sec-Fetch-Site", site) }.status).isEqualTo(HttpStatusCode.Forbidden)
            assertThat(client.delete("/thing") { header("Sec-Fetch-Site", site) }.status).isEqualTo(HttpStatusCode.Forbidden)
        }
    }

    @Test
    fun testCrossSiteReadsPass() = guarded {
        assertThat(client.get("/thing") { header("Sec-Fetch-Site", "cross-site") }.status).isEqualTo(HttpStatusCode.OK)
    }
}
