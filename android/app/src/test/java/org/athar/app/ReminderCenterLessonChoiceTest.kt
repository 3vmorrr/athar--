package org.athar.app

import org.athar.app.learning.LessonReminderChoice
import org.junit.Assert.*
import org.junit.Test

class ReminderCenterLessonChoiceTest {
    @Test fun lessonChoiceContractRemainsFixedLeadAndOwnerCollectionSafe() {
        val a=LessonReminderChoice(7)
        val b=LessonReminderChoice(9)
        assertEquals(15,a.leadMinutes)
        assertEquals(listOf(7,9),listOf(a,b).sortedBy{it.lessonId}.map{it.lessonId})
        assertTrue(runCatching{LessonReminderChoice(0)}.isFailure)
        assertTrue(runCatching{LessonReminderChoice(7,30)}.isFailure)
    }
}
