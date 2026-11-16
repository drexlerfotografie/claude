package de.mediclever.tagebuch.core

import de.mediclever.tagebuch.core.model.GoalCheck
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {
    @Test fun goalCheckIdIsStable() = assertEquals("g|2026-01-02", GoalCheck.idFor("g", "2026-01-02"))
}
