package lynks.common.inject

class ServiceProvider {

    class ServiceEntry<T : Any>(val clazz: Class<out T>, val value: T)

    private val services = mutableListOf<ServiceEntry<*>>()
    private var sealed = false

    fun seal() {
        sealed = true
    }

    fun <T : Any> register(t: T) {
        check(!sealed) { "ServiceProvider is sealed; cannot register ${t.javaClass.name} after startup" }
        services.add(ServiceEntry(t.javaClass, t))
    }

    inline fun <reified T: Any> get() : T {
        return get(T::class.java) ?: throw IllegalStateException("No service registered for ${T::class.java.name}")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: Class<T>): T? {
        return services.find {
            it.clazz == clazz || clazz.isAssignableFrom(it.clazz)
        }?.let {
            it.value as T?
        }
    }

}
