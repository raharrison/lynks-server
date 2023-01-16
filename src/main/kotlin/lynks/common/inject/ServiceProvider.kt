package lynks.common.inject

class ServiceProvider {

    class ServiceEntry<T : Any>(val clazz: Class<out T>, val value: T)

    val services = mutableListOf<ServiceEntry<*>>()

    inline fun <reified T : Any> register(t: T) {
        services.add(ServiceEntry(t::class.java, t))
    }

    inline fun <reified T: Any> get() : T {
        return get(T::class.java)!!
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
