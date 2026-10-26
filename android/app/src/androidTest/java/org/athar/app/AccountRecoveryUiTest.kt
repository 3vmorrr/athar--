package org.athar.app

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.athar.app.core.ui.AccountDataFailure
import org.athar.app.core.ui.AtharTheme
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.assertEquals
import java.util.Locale

class AccountRecoveryUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun invalidAccountDataHasRetryInsteadOfEmptyOrSave(){
        val app=ApplicationProvider.getApplicationContext<android.content.Context>()
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val configuration=Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
        val context=app.createConfigurationContext(configuration);var retried=0
        compose.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalContext provides context,LocalResources provides context.resources,LocalConfiguration provides configuration,LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr,LocalDensity provides Density(density.density,1.5f)){
                AtharTheme("dark"){AccountDataFailure{retried++}}
            }
        }
        compose.onNodeWithText(context.getString(R.string.account_data_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.empty_state)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.save)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.retry)).performClick();assertEquals(1,retried)
    }
}
