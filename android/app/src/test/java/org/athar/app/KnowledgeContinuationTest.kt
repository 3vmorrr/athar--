package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.learning.*
import org.junit.Assert.*
import org.junit.Test

class KnowledgeContinuationTest {
    private fun progress(
        id:Int,
        position:Int,
        complete:Boolean=false,
        updated:Long,
    )=RetainedLessonProgress(
        SavedLessonProgress(
            lessonId=id,
            positionSeconds=position,
            durationSeconds=100,
            complete=complete,
            eventId=if(complete)"00000000-0000-0000-0000-000000000001" else null,
            pending=false,
            version=1,
        ),
        updated,
    )

    @Test fun latestContinuationUsesLastUpdatedIncompleteStartedLesson() {
        val value=LessonContinuationPolicy.latest(
            listOf(
                progress(1,40,updated=100),
                progress(2,0,updated=300),
                progress(3,80,complete=true,updated=400),
                progress(4,20,updated=200),
            ),
        )
        assertEquals(4,value?.progress?.lessonId)
    }

    @Test fun realLessonDetailAddsOnlyReturnedSeriesAndScholarContext() {
        val retained=progress(7,30,updated=100)
        val value=KnowledgeContinuationContract.from(
            retained,
            JsonParser.parseString(
                """{
                  "id":7,
                  "title":"Real lesson",
                  "series_id":4,
                  "series_title":"Real series",
                  "scholar_name":"Real scholar"
                }""",
            ),
        )
        assertEquals("Real lesson",value.title)
        assertEquals(4,value.seriesId)
        assertEquals("Real series",value.seriesTitle)
        assertEquals("Real scholar",value.scholarName)
        assertEquals(30,value.positionSeconds)
    }

    @Test fun missingDetailKeepsRealLessonIdWithoutInventingCopy() {
        val value=KnowledgeContinuationContract.from(progress(9,10,updated=50),null)
        assertEquals(9,value.lessonId)
        assertNull(value.title)
        assertNull(value.seriesTitle)
        assertNull(value.scholarName)
    }

    @Test fun mismatchedDetailFailsClosed() {
        assertTrue(runCatching{
            KnowledgeContinuationContract.from(
                progress(9,10,updated=50),
                JsonParser.parseString("""{"id":10,"title":"Wrong lesson"}"""),
            )
        }.isFailure)
    }

    @Test fun knowledgeCatalogUsesPublishedPayloadFieldsOnly() {
        val page=KnowledgeCatalogContract.page(
            "lessons",
            JsonParser.parseString(
                """{
                  "items":[
                    {
                      "id":2,
                      "title":"Lesson",
                      "description":"Description",
                      "series_title":"Series",
                      "scholar_name":"Scholar"
                    }
                  ],
                  "pagination":{"has_more":false}
                }""",
            ),
        )
        assertEquals("Lesson",page.first.single().title)
        assertEquals("Series",page.first.single().subtitle)
        assertFalse(page.second)
    }
}
