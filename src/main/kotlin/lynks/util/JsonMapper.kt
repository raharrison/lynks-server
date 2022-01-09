package lynks.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder

object JsonMapper {

    val defaultMapper: ObjectMapper

    init {
        val lowerCaseEnumJacksonSerializerModule = SimpleModule().also {
            val lowerCaseEnumKeySerializer = object : StdSerializer<Enum<*>>(Enum::class.java) {
                override fun serialize(value: Enum<*>?, json: JsonGenerator, provider: SerializerProvider) {
                    json.writeFieldName(value?.name?.lowercase())
                }
            }
            val lowerCaseEnumValueSerializer = object : StdSerializer<Enum<*>>(Enum::class.java) {
                override fun serialize(value: Enum<*>?, json: JsonGenerator, provider: SerializerProvider) {
                    json.writeString(value?.name?.lowercase())
                }
            }
            it.addKeySerializer(Enum::class.java, lowerCaseEnumKeySerializer)
            it.addSerializer(Enum::class.java, lowerCaseEnumValueSerializer)
        }

        defaultMapper = jacksonMapperBuilder()
//            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .addModule(lowerCaseEnumJacksonSerializerModule)
            .build()
    }

}

