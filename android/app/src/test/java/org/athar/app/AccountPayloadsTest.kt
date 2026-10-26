package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.core.data.AccountPayloads
import org.junit.Assert.*
import org.junit.Test

class AccountPayloadsTest {
    private fun valid(tool:String,json:String)=AccountPayloads.valid(tool,JsonParser.parseString(json))
    @Test fun absenceIsExplicitRatherThanMalformed(){
        assertTrue(valid("khatmah","null"));assertTrue(valid("favorites","[]"));assertTrue(valid("bookmarks","[]"))
        for(tool in listOf("khatmah","favorites","bookmarks"))assertFalse(valid(tool,"{}"))
        assertFalse(valid("favorites","null"));assertFalse(valid("bookmarks","true"))
    }
    @Test fun khatmahCannotFallBackToZeroFromMissingOrInvalidFields(){
        val body="""{"start_date":"2026-10-01","target_date":"2026-11-01","total_pages":604,"completed_pages":12,"status":"active"}"""
        assertTrue(valid("khatmah",body))
        assertFalse(valid("khatmah",body.replace("12","605")))
        assertFalse(valid("khatmah",body.replace("2026-11-01","2026-02-30")))
        assertFalse(valid("khatmah",body.replace("\"completed_pages\":12,","")))
    }
    @Test fun listsValidateEveryActionTarget(){
        assertTrue(valid("favorites","""[{"target_type":"lesson","target_id":"2","title":"Fixture"}]"""))
        assertFalse(valid("favorites","""[{"target_type":"user","target_id":2,"title":"Fixture"}]"""))
        assertFalse(valid("favorites","""[{"target_type":"lesson","title":"Fixture"}]"""))
        assertTrue(valid("bookmarks","""[{"id":1,"surah":114,"ayah":6,"label":"Fixture"}]"""))
        assertFalse(valid("bookmarks","""[{"id":1,"surah":115,"ayah":6,"label":"Fixture"}]"""))
    }
}
