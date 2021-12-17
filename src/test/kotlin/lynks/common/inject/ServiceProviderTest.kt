package lynks.common.inject

import lynks.resource.WebResourceRetriever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ServiceProviderTest {

    private val serviceProvider = ServiceProvider()

    @Test
    fun testServiceRetrieval() {
        val retriever = WebResourceRetriever()
        serviceProvider.register(retriever)

        val retrieved = serviceProvider.get<WebResourceRetriever>()
        assertThat(retrieved).isEqualTo(retriever)
    }

}
