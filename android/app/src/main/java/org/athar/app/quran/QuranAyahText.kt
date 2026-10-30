package org.athar.app.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import org.athar.app.core.ui.QuranFont

/** The bundled text/bismillah and established Quran typography are shared by bounded readers. */
@Composable internal fun QuranAyahText(ayah: AyahText, fontSize: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (ayah.bismillah != null) Text(ayah.bismillah, Modifier.fillMaxWidth(), fontFamily = QuranFont, fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.9).sp, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
        Text(ayah.text, Modifier.fillMaxWidth(), fontFamily = QuranFont, fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.95).sp, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
    }
}
