package com.klisee.reloaded.gmk.update

actual fun formatShortDate(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        .format(java.util.Date(millis))
