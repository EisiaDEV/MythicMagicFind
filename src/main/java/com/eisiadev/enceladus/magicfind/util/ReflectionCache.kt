package com.eisiadev.enceladus.magicfind.util

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object ReflectionCache {
    private val fieldCache = ConcurrentHashMap<String, Field?>()
    private val methodCache = ConcurrentHashMap<String, Method?>()
    private val missingFields = ConcurrentHashMap.newKeySet<String>()
    private val missingMethods = ConcurrentHashMap.newKeySet<String>()

    fun getField(clazz: Class<*>, fieldName: String): Field? {
        val key = "${clazz.name}::$fieldName"

        if (missingFields.contains(key)) {
            return null
        }

        fieldCache[key]?.let { return it }

        var currentClass: Class<*>? = clazz
        while (currentClass != null && currentClass != Any::class.java) {
            try {
                val field = currentClass.getDeclaredField(fieldName)
                field.isAccessible = true
                fieldCache[key] = field
                return field
            } catch (e: NoSuchFieldException) {
            } catch (e: Throwable) {
                missingFields.add(key)
                return null
            }
            currentClass = currentClass.superclass
        }

        missingFields.add(key)
        return null
    }

    fun getMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method? {
        val key = "${clazz.name}::$methodName::${paramTypes.joinToString(",") { it.name }}"

        if (missingMethods.contains(key)) {
            return null
        }

        methodCache[key]?.let { return it }

        return try {
            val method = clazz.getMethod(methodName, *paramTypes)
            method.isAccessible = true
            methodCache[key] = method
            method
        } catch (e: NoSuchMethodException) {
            missingMethods.add(key)
            null
        } catch (e: Throwable) {
            missingMethods.add(key)
            null
        }
    }

    fun getFieldValue(instance: Any, fieldName: String): Any? {
        return try {
            val field = getField(instance.javaClass, fieldName) ?: return null
            field.get(instance)
        } catch (e: Throwable) {
            null
        }
    }

    fun getFieldDouble(instance: Any, fieldName: String): Double? {
        return try {
            (getFieldValue(instance, fieldName) as? Number)?.toDouble()
        } catch (e: Throwable) {
            null
        }
    }
}