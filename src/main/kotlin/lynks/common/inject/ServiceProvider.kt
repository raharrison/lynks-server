package lynks.common.inject

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class ServiceProvider {

    class ServiceEntry<T: Any>(val kClass: KClass<out T>, val value: T)

    val services = mutableListOf<ServiceEntry<*>>()

    inline fun <reified T: Any> register(t: T) {
        val kClass = t::class
        services.add(ServiceEntry(kClass, t))
    }

    inline fun <reified T: Any> get() : T {
        return get(T::class)!!
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> get(kClass: KClass<T>): T? {
        return services.find {
            it.kClass == kClass || it.kClass.isSubclassOf(kClass)
        }?.let {
            it.value as T?
        }
    }

}
