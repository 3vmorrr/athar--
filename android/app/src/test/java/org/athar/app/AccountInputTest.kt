package org.athar.app

import org.athar.app.auth.AccountInput
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class AccountInputTest {
    @Test fun profileMatchesServerUnicodeAndReservedNameBoundaries(){
        assertTrue(AccountInput.validDisplayName("😀😀"))
        assertTrue(AccountInput.validDisplayName("😀".repeat(80)))
        assertFalse(AccountInput.validDisplayName("😀".repeat(81)))
        assertFalse(AccountInput.validDisplayName(" أ "))
        assertTrue(AccountInput.validUsername("reader_123"))
        for(name in listOf("admin","administrator","ROOT","support","athar","api","www","owner","ab","اسم","a-b","a".repeat(25)))assertFalse(name,AccountInput.validUsername(name))
    }
    @Test fun usernameNormalizationIsIndependentOfDeviceLocale(){
        val previous=Locale.getDefault()
        try{Locale.setDefault(Locale.forLanguageTag("tr"));assertEquals("id_123456",AccountInput.normalizeUsernameInput("ID_١٢٣۴۵۶"))}finally{Locale.setDefault(previous)}
    }
    @Test fun passwordCountsUnicodeCharactersWithoutTrimmingOrAcceptingNul(){
        assertTrue(AccountInput.validPassword("😀".repeat(10)))
        assertTrue(AccountInput.validPassword("x".repeat(200)))
        assertFalse(AccountInput.validPassword("x".repeat(201)))
        assertFalse(AccountInput.validPassword("password123\u0000"))
        assertFalse(AccountInput.validPassword("short"))
    }
}
