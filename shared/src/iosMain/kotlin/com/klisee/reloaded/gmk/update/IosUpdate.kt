package com.klisee.reloaded.gmk.update

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

/**
 * iOS no publica paquetes fuera del App Store, y el sistema no permite que una app se actualice
 * sola: la actualización la entrega la tienda. Devolver `null` deja la pantalla de actualizaciones
 * en modo informativo, sin ofrecer un botón que no podría funcionar.
 */
actual fun platformUpdateTarget(): UpdatePlatform? = null
