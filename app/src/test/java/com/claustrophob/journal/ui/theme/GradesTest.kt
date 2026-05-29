package com.claustrophob.journal.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class GradesTest {

    @Test
    fun `пятибалльная`() {
        assertEquals(GradeTone.Excellent, gradeTone(5.0))
        assertEquals(GradeTone.Excellent, gradeTone(4.5))
        assertEquals(GradeTone.Good, gradeTone(4.0))
        assertEquals(GradeTone.Fair, gradeTone(3.0))
        assertEquals(GradeTone.Poor, gradeTone(2.0))
        assertEquals(GradeTone.None, gradeTone(0.0))
    }

    @Test
    fun `двенадцатибалльная узнаётся по оценке выше пяти`() {
        assertEquals(GradeTone.Excellent, gradeTone(11.0))
        assertEquals(GradeTone.Good, gradeTone(8.0))
        // Пятёрка из двенадцати — это не отлично.
        assertEquals(GradeTone.Fair, gradeTone(5.0, scale = 12))
    }

    @Test
    fun `шкала по набору оценок`() {
        assertEquals(5, scaleOf(listOf(5, 4, 3)))
        assertEquals(12, scaleOf(listOf(5, 9, 3)))
        assertEquals(5, scaleOf(emptyList<Int>()))
    }
}
