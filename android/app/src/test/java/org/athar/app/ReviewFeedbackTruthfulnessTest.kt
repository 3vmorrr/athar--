package org.athar.app

import org.athar.app.review.*
import org.junit.Assert.*
import org.junit.Test

class ReviewFeedbackTruthfulnessTest {
    @Test fun finalUncertainResultIsNotAProblemClaim() {
        val result = ReviewPassageVerdict(
            ayahs = listOf(
                ReviewAyahVerdict(
                    1,
                    1,
                    listOf(
                        ReviewWordVerdict(
                            1,
                            1,
                            0,
                            "بسم",
                            ReviewWordState.UNCERTAIN,
                            0.2f,
                            1f,
                            0.1f,
                        ).validated(),
                    ),
                ),
            ),
            heardCharacters = 3,
            final = true,
        )
        assertFalse(result.hasProblem)
        assertTrue(result.hasUncertainty)
    }

    @Test fun onlyWrongOrSkippedRaisesProblemFlag() {
        for (state in listOf(
            ReviewWordState.WRONG,
            ReviewWordState.SKIPPED,
        )) {
            val result = ReviewPassageVerdict(
                ayahs = listOf(
                    ReviewAyahVerdict(
                        1,
                        1,
                        listOf(
                            ReviewWordVerdict(
                                1,
                                1,
                                0,
                                "بسم",
                                state,
                                0.8f,
                                1f,
                                0.9f,
                            ).validated(),
                        ),
                    ),
                ),
                heardCharacters = 3,
                final = true,
            )
            assertTrue(result.hasProblem)
        }
    }
}
