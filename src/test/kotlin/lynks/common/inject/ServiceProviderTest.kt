package lynks.common.inject

import lynks.resource.WebResourceRetriever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ServiceProviderTest {

    private val serviceProvider = ServiceProvider()

    @Test
    fun testServiceRetrieval() {
        val retriever = WebResourceRetriever()
        serviceProvider.register(retriever)

        val retrieved = serviceProvider.get<WebResourceRetriever>()
        assertThat(retrieved).isEqualTo(retriever)
    }

    @Test
    fun testSealPreventsLateRegistration() {
        serviceProvider.register(WebResourceRetriever())
        serviceProvider.seal()
        assertThrows<IllegalStateException> {
            serviceProvider.register(WebResourceRetriever())
        }
    }

    @Test
    fun testGetAfterSealStillWorks() {
        val retriever = WebResourceRetriever()
        serviceProvider.register(retriever)
        serviceProvider.seal()
        assertThat(serviceProvider.get<WebResourceRetriever>()).isEqualTo(retriever)
    }

}
