package org.athar.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.athar.app.quran.AyahText
import org.athar.app.quran.QuranData
import org.athar.app.quran.QuranIndex
import org.w3c.dom.Element

/** Real bundled references for pure domain tests; Android parsing stays instrumented. */
internal object QuranJvmFixture {
    val data: QuranData by lazy {
        val index = QuranIndex.fromJson(File("src/main/assets/quran/index.json").readText())
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/assets/quran/tanzil-uthmani.xml"))
        val ayahs = document.getElementsByTagName("aya")
        val verses = (0 until ayahs.length).map { offset ->
            val ayah = ayahs.item(offset) as Element
            val surah = (ayah.parentNode as Element).getAttribute("index").toInt()
            AyahText(
                index.position(index.global(surah, ayah.getAttribute("index").toInt())),
                ayah.getAttribute("text"),
                ayah.getAttribute("bismillah").takeIf { it.isNotEmpty() },
            )
        }
        QuranData(index, verses)
    }
}
