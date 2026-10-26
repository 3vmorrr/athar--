package org.athar.app

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.Locale
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap

class LessonWatchTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun watchCommentsThreadsCompletionAndSeriesStayNative() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}).resources
        fun label(id:Int)=resources.getString(id)
        runBlocking { graph.session.restore();graph.settings.completeOnboarding();graph.settings.theme("light");graph.session.login("native.reader@example.test","NativeFixture123!") }
        val before=runBlocking{graph.session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "2")).toString()}
        try { ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(25_000){compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Interface test lesson 2"))
            compose.onNodeWithText("Interface test lesson 2").performClick()
            compose.waitUntil(20_000){compose.onAllNodesWithText(label(R.string.watch_video_unavailable)).fetchSemanticsNodes().isNotEmpty()}
            assertEquals(before,runBlocking{graph.session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "2")).toString()})
            capture(app,"watch")
            val total=runBlocking{graph.session.request("GET","lessons/2/discussion").asJsonObject["pagination"].asJsonObject["total"].asInt}
            compose.onNodeWithTag("watch-details").performScrollToNode(hasTestTag("watch-comments"))
            compose.waitUntil(10_000){compose.onAllNodesWithText(resources.getString(R.string.watch_comments_count,total)).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(resources.getString(R.string.watch_comments_count,total)).performScrollTo().performClick()
            compose.onNodeWithTag("discussion-list").performScrollToNode(hasText("Visible interface comment"))
            compose.onNode(hasText("Visible interface comment") and hasAnyAncestor(hasTestTag("discussion-list"))).assertIsDisplayed()
            compose.onNodeWithTag("discussion-list").performScrollToNode(hasText(resources.getString(R.string.watch_view_replies,1)))
            compose.onNodeWithText(resources.getString(R.string.watch_view_replies,1)).performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithText(resources.getString(R.string.watch_hide_replies,1)).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("discussion-list").performScrollToNode(hasText("Visible interface reply"))
            compose.onNode(hasText("Visible interface reply") and hasAnyAncestor(hasTestTag("discussion-list"))).assertIsDisplayed()
            val inline = compose.onAllNodesWithContentDescription(label(R.string.watch_expand_comments)).fetchSemanticsNodes().isNotEmpty()
            capture(app,if(inline)"comments-inline" else "comments-compact-full")
            if(inline)compose.onNodeWithContentDescription(label(R.string.watch_expand_comments)).performClick()
            compose.onNodeWithTag("discussion-list").performScrollToNode(hasText("Visible interface comment"))
            compose.onNode(hasText("Visible interface comment") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            capture(app,"comments-full")
            if(language=="ar"&&InstrumentationRegistry.getArguments().getString("submitComment")!="false") {
                val body="Native interface comment fixture "+java.util.UUID.randomUUID()
                compose.onNode(hasText(label(R.string.watch_add_comment)) and hasAnyAncestor(isDialog())).performClick()
                compose.onNode(hasSetTextAction()).performTextInput(body)
                compose.onNode(hasContentDescription(label(R.string.close)) and hasAnyAncestor(hasTestTag("comment-composer"))).performClick()
                compose.onNode(hasText(label(R.string.watch_add_comment)) and hasAnyAncestor(isDialog())).performClick()
                compose.onNode(hasSetTextAction()).assertTextContains(body)
                compose.onNodeWithText(label(R.string.send_comment)).performScrollTo().performClick()
                compose.waitUntil(15_000){runBlocking{graph.session.request("GET","lessons/2/discussion").asJsonObject["items"].asJsonArray.any{row->row.asJsonObject["body"].asString==body&&row.asJsonObject["status"].asString=="pending"}}}
                compose.waitUntil(10_000){compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()}
            }
            compose.onNodeWithContentDescription(label(R.string.close)).performClick()
            compose.waitForIdle()
            if(compose.onAllNodesWithTag("discussion-list").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription(label(R.string.close)).performClick()
            compose.onNodeWithTag("watch-details").performScrollToNode(hasTestTag("watch-actions"))
            if(compose.onAllNodesWithText(label(R.string.watch_mark_complete)).fetchSemanticsNodes().isNotEmpty())compose.onNodeWithText(label(R.string.watch_mark_complete)).performScrollTo().performClick()
            compose.waitUntil(15_000){runBlocking{graph.session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "2")).takeUnless{it.isJsonNull}?.asJsonObject?.get("is_complete")?.asInt==1}}
            if(compose.onAllNodesWithText("Interface test lesson 3").fetchSemanticsNodes().isEmpty())compose.onNodeWithTag("watch-details").performScrollToNode(hasText("Interface test lesson 3"))
            compose.onNodeWithText("Interface test lesson 3").performScrollTo().performClick()
            compose.waitUntil(15_000){runCatching{compose.onNodeWithTag("watch-title").assertTextEquals("Interface test lesson 3")}.isSuccess}
            compose.onNode(hasText("Interface test series") and hasClickAction()).performScrollTo().performClick()
            compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.watch_continue)).fetchSemanticsNodes().isNotEmpty()}
            capture(app,"series")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Interface test scholar"))
            compose.onNode(hasText("Interface test scholar") and hasClickAction()).performClick()
            compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.scholar_standalone)).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(label(R.string.scholar_standalone)).performClick()
            compose.waitUntil(15_000){compose.onAllNodesWithText("Official YouTube IFrame sample").fetchSemanticsNodes().isNotEmpty()}
            capture(app,"scholar")
        }} finally {runBlocking{graph.session.logout()}}
    }
    private fun capture(app:AtharApplication,name:String){compose.waitForIdle();android.os.SystemClock.sleep(350);val locale=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar";val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());File(app.filesDir,"lesson-$locale-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
}
