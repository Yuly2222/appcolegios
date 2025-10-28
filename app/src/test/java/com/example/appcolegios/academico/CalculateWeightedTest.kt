package com.example.appcolegios.academico

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CalculateWeightedTest {

    @Test
    fun weightedGrades_mapStructure_returnsWeightedAverage() {
        val data = mapOf<String, Any>(
            "grades" to mapOf(
                "a1" to mapOf("grade" to 4.0, "weight" to 0.4),
                "a2" to mapOf("grade" to 5.0, "weight" to 0.6)
            )
        )

        val result = calculateWeightedFromDoc(data)
        // expected = 4.0*0.4 + 5.0*0.6 = 4.6
        assertNotNull(result)
        assertEquals(4.6, result!!, 0.0001)
    }

    @Test
    fun finalGradeField_returnsFinalGrade() {
        val data = mapOf<String, Any>("finalGrade" to 3.7)
        val result = calculateWeightedFromDoc(data)
        assertNotNull(result)
        assertEquals(3.7, result!!, 0.0001)
    }

    @Test
    fun numericFields_averageReturned() {
        val data = mapOf<String, Any>("nota" to 4.0, "grade" to 5.0)
        val result = calculateWeightedFromDoc(data)
        assertNotNull(result)
        assertEquals(4.5, result!!, 0.0001)
    }
}
