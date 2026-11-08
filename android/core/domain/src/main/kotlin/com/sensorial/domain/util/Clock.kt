package com.sensorial.domain.util

import com.sensorial.domain.model.InteractionId
import java.util.UUID

fun interface Clock {
    fun nowEpochMs(): Long
}

object SystemClock : Clock {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}

fun interface IdGenerator {
    fun newInteractionId(): InteractionId
}

object UuidIdGenerator : IdGenerator {
    override fun newInteractionId(): InteractionId = InteractionId(UUID.randomUUID().toString())
}
