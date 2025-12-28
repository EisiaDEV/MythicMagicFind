package com.eisiadev.enceladus.magicfind.util

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object ReflectionCache {
    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val methodCache = ConcurrentHashMap<String, Method>()

    fun getField(clazz: Class<*>, fieldName: String): Field? {
        val key = "${clazz.name}::$fieldName"
        return fieldCache.getOrPut(key) {
            var currentClass: Class<*>? = clazz
            while (currentClass != null && currentClass != Any::class.java) {
                try {
                    return@getOrPut currentClass.getDeclaredField(fieldName).apply {
                        isAccessible = true
                    }
                } catch (e: NoSuchFieldException) {
                    currentClass = currentClass.superclass
                }
            }
            throw NoSuchFieldException("Field $fieldName not found in ${clazz.name}")
        }
    }

    fun getMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method? {
        val key = "${clazz.name}::$methodName::${paramTypes.joinToString(",") { it.name }}"
        return methodCache.getOrPut(key) {
            try {
                clazz.getMethod(methodName, *paramTypes).apply {
                    isAccessible = true
                }
            } catch (e: NoSuchMethodException) {
                null
            }
        }
    }

    fun getFieldValue(instance: Any, fieldName: String): Any? {
        return try {
            val field = getField(instance.javaClass, fieldName)
            field?.get(instance)
        } catch (e: Exception) {
            null
        }
    }

    fun getFieldDouble(instance: Any, fieldName: String): Double? {
        return (getFieldValue(instance, fieldName) as? Number)?.toDouble()
    }
}
