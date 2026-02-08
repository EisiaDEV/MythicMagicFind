package com.eisiadev.enceladus.magicfind.util

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object ReflectionCache {
    private val fieldCache = ConcurrentHashMap<String, Field?>()
    private val methodCache = ConcurrentHashMap<String, Method?>()
    private val missingFields = ConcurrentHashMap.newKeySet<String>()  // ✅ 추가
    private val missingMethods = ConcurrentHashMap.newKeySet<String>()

    fun getField(clazz: Class<*>, fieldName: String): Field? {
        val key = "${clazz.name}::$fieldName"

        if (missingFields.contains(key)) {
            return null
        }

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
            missingFields.add(key)
            null
        }
    }

    fun getMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method? {
        val key = "${clazz.name}::$methodName::${paramTypes.joinToString(",") { it.name }}"

        if (missingMethods.contains(key)) {  // ✅ 추가
            return null
        }

        return methodCache.getOrPut(key) {
            try {
                clazz.getMethod(methodName, *paramTypes).apply {
                    isAccessible = true
                }
            } catch (e: NoSuchMethodException) {
                missingMethods.add(key)  // ✅ 추가
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