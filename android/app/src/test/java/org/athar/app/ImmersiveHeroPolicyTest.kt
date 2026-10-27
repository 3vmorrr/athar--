package org.athar.app

import org.athar.app.core.ui.*
import org.junit.Assert.*
import org.junit.Test

class ImmersiveHeroPolicyTest {
    @Test fun artworkRegistryUsesOriginalOfflineSafeCanvasAssets() {
        val quran=AtharHeroArtworkRegistry.descriptor(AtharHeroArtwork.Quran)
        assertEquals("hero.quran",quran.id)
        assertEquals("native_canvas",quran.sourceKind)
        assertFalse(quran.networkRequired)
        assertFalse(quran.containsText)
        assertFalse(quran.containsPeople)
        assertFalse(quran.autoMirror)

        assertEquals(
            "hero.knowledge",
            AtharHeroArtworkRegistry.descriptor(AtharHeroArtwork.Home).id,
        )
        assertEquals(
            "state.empty",
            AtharHeroArtworkRegistry.descriptor(
                AtharHeroArtwork.Quran,
                AtharHeroVisualState.Empty,
            ).id,
        )
        assertEquals(
            "state.offline",
            AtharHeroArtworkRegistry.descriptor(
                AtharHeroArtwork.NightPrayer,
                AtharHeroVisualState.Offline,
            ).id,
        )
        assertEquals(
            "state.error",
            AtharHeroArtworkRegistry.descriptor(
                AtharHeroArtwork.Books,
                AtharHeroVisualState.Error,
            ).id,
        )
    }

    @Test fun ordinaryPhoneKeepsExpandedArtAndInlineActions() {
        val value=AtharHeroLayoutPolicy.resolve(
            fontScale=1f,
            widthDp=390f,
            collapseFraction=0f,
        )
        assertFalse(value.compact)
        assertTrue(value.showArtwork)
        assertFalse(value.stackActions)
        assertEquals(172,value.minimumHeightDp)
        assertEquals(2,value.titleMaxLines)
        assertEquals(3,value.supportingMaxLines)
    }

    @Test fun largeTextRemovesDecorationBeforeContentAndStacksActions() {
        val value=AtharHeroLayoutPolicy.resolve(
            fontScale=2f,
            widthDp=390f,
            collapseFraction=0f,
        )
        assertTrue(value.compact)
        assertFalse(value.showArtwork)
        assertTrue(value.stackActions)
        assertEquals(3,value.titleMaxLines)
        assertEquals(4,value.supportingMaxLines)
    }

    @Test fun narrowWidthStacksActionsWithoutForcingVeryLargeTextPolicy() {
        val value=AtharHeroLayoutPolicy.resolve(
            fontScale=1f,
            widthDp=320f,
            collapseFraction=0f,
        )
        assertFalse(value.compact)
        assertTrue(value.showArtwork)
        assertTrue(value.stackActions)
    }

    @Test fun collapsedHeroUsesCompactHeaderBudgetAndEventuallyHidesArt() {
        val compact=AtharHeroLayoutPolicy.resolve(1f,390f,0.8f)
        assertTrue(compact.compact)
        assertEquals(136,compact.minimumHeightDp)
        val almostGone=AtharHeroLayoutPolicy.resolve(1f,390f,0.95f)
        assertFalse(almostGone.showArtwork)
    }

    @Test fun invalidLayoutInputsFailClosed() {
        assertTrue(runCatching{
            AtharHeroLayoutPolicy.resolve(0f,390f,0f)
        }.isFailure)
        assertTrue(runCatching{
            AtharHeroLayoutPolicy.resolve(1f,0f,0f)
        }.isFailure)
    }
}
