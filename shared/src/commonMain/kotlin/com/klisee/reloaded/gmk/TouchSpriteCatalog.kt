package com.klisee.reloaded.gmk

object TouchSpriteCatalog {
    data class Target(val id: String, val title: String)

    val independent = setOf("m", "e", "p", "l", "t", "space", "a")

    val targets: List<Target> = TouchLayouts.controls.map {
        Target(it.id, if (it.joystick) "Joystick · base" else it.title)
    } + Target("joystick-knob", "Joystick · palanca")

    fun storageKey(id: String, profile: TouchProfile): String =
        if (id in independent && profile in setOf(TouchProfile.WORLD, TouchProfile.BATTLE)) "${profile.id}/$id" else id

    fun baseId(id: String): String = id.substringAfter('/')

    val keys: List<String>
        get() = targets.map { it.id } + listOf("world", "battle").flatMap { profile ->
            independent.map { "$profile/$it" }
        }

    fun target(id: String): Target =
        targets.firstOrNull { it.id == baseId(id) } ?: error("Control desconocido.")
}
