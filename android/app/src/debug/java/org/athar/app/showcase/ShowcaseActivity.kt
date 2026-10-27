package org.athar.app.showcase

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.athar.app.core.ui.AtharHeroArtwork
import org.athar.app.core.ui.AtharHeroPresentation
import org.athar.app.core.ui.AtharHeroStatusTone
import org.athar.app.core.ui.AtharIcons
import org.athar.app.core.ui.AtharImmersiveHero
import org.athar.app.core.ui.AtharLayeredContentSurface
import org.athar.app.core.ui.AtharMetric
import org.athar.app.core.ui.AtharQuickAction
import org.athar.app.core.ui.AtharSectionHeader
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.AtharStatusPill
import org.athar.app.core.ui.AtharTheme
import org.athar.app.core.ui.QuranFont

class ShowcaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AtharUiShowcase() }
    }
}

private enum class PreviewPage(
    val label: String,
) {
    Home("الرئيسية"),
    Quran("القرآن"),
    Adhkar("الأذكار"),
    Night("قيام الليل"),
    Watch("الدرس"),
    Audio("الاستماع"),
    Books("الكتب"),
    Account("الحساب"),
}

@Composable
private fun AtharUiShowcase() {
    var page by rememberSaveable { mutableStateOf(PreviewPage.Home) }
    var dark by rememberSaveable { mutableStateOf(false) }

    AtharTheme(if (dark) "dark" else "light") {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 36.dp, bottom = AtharSpacing.Sm),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AtharSpacing.Lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Athar UI Preview",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "بيانات تجريبية للمعاينة البصرية فقط",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (dark) "داكن" else "فاتح",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Switch(
                                checked = dark,
                                onCheckedChange = { dark = it },
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(
                                horizontal = AtharSpacing.Lg,
                                vertical = AtharSpacing.Sm,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        ) {
                            items(PreviewPage.entries) { item ->
                                FilterChip(
                                    selected = page == item,
                                    onClick = { page = item },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (page) {
                    PreviewPage.Home -> HomePreview()
                    PreviewPage.Quran -> QuranPreview()
                    PreviewPage.Adhkar -> AdhkarPreview()
                    PreviewPage.Night -> NightPreview()
                    PreviewPage.Watch -> WatchPreview()
                    PreviewPage.Audio -> AudioPreview()
                    PreviewPage.Books -> BooksPreview()
                    PreviewPage.Account -> AccountPreview()
                }
            }
        }
    }
}

@Composable
private fun HomePreview() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.Home,
                    eyebrow = "رحلتك مع أثر",
                    title = "السلام عليكم، عمر",
                    supportingText = "مساحة هادئة للقراءة والعلم والذكر وقيام الليل.",
                    primaryActionLabel = "اقرأ القرآن",
                    secondaryActionLabel = "قيام الليل",
                ),
                onPrimaryAction = {},
                onSecondaryAction = {},
            )
        }

        item {
            AtharLayeredContentSurface(overlap = 24.dp) {
                AtharSectionHeader("ابدأ من هنا")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharQuickAction(
                        icon = AtharIcons.MenuBook,
                        label = "القرآن",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        emphasized = true,
                    )
                    AtharQuickAction(
                        icon = AtharIcons.School,
                        label = "العلم",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharQuickAction(
                        icon = AtharIcons.Spa,
                        label = "الأذكار",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    AtharQuickAction(
                        icon = AtharIcons.NightsStay,
                        label = "قيام الليل",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }

                AtharSectionHeader(
                    title = "القادم",
                    actionLabel = "عرض الكل",
                    onAction = {},
                )
                PreviewContentCard(
                    title = "شرح كتاب التوحيد",
                    subtitle = "الشيخ محمد · اليوم ٨:٣٠ م",
                    status = "قريبًا",
                )

                AtharSectionHeader("نشاطك")
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharMetric("١٢", "جلسة قراءة", Modifier.weight(1f))
                    AtharMetric("٧", "دروس مكتملة", Modifier.weight(1f))
                    AtharMetric("٢٤", "ذكر مكتمل", Modifier.weight(1f))
                }

                AtharSectionHeader("أكمل رحلتك العلمية")
                PreviewContentCard(
                    title = "مدخل إلى أصول الفقه",
                    subtitle = "الدرس ٤ من ١٢ · ٣٨٪",
                )
                PreviewContentCard(
                    title = "رياض الصالحين",
                    subtitle = "آخر إضافة · باب الإخلاص",
                )
            }
        }
    }
}

@Composable
private fun QuranPreview() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.Quran,
                    eyebrow = "القرآن الكريم",
                    title = "متابعة القراءة",
                    supportingText = "سورة البقرة · الآية ١٤٢",
                    primaryActionLabel = "تابع القراءة",
                ),
                onPrimaryAction = {},
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AtharSpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            ) {
                AtharQuickAction(
                    AtharIcons.MenuBook,
                    "المصحف",
                    {},
                    Modifier.weight(1f),
                    emphasized = true,
                )
                AtharQuickAction(
                    AtharIcons.PlayArrow,
                    "الاستماع",
                    {},
                    Modifier.weight(1f),
                )
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AtharSpacing.Lg),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        "سُورَةُ الْفَاتِحَةِ",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                        modifier = Modifier.fillMaxWidth(),
                        fontFamily = QuranFont,
                        fontSize = 24.sp,
                        lineHeight = 48.sp,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Rtl,
                        ),
                    )
                    Text(
                        "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ﴿١﴾ الرَّحْمَٰنِ الرَّحِيمِ ﴿٢﴾ مَالِكِ يَوْمِ الدِّينِ ﴿٣﴾ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ ﴿٤﴾ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ ﴿٥﴾ صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ ﴿٦﴾",
                        modifier = Modifier.fillMaxWidth(),
                        fontFamily = QuranFont,
                        fontSize = 24.sp,
                        lineHeight = 48.sp,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Rtl,
                        ),
                    )
                    AtharStatusPill("اضغط مطولًا على الآية لإظهار أدواتها")
                }
            }
        }
    }
}

@Composable
private fun AdhkarPreview() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.Adhkar,
                    eyebrow = "الأذكار",
                    title = "أذكار الصباح",
                    supportingText = "٤ من ٢٤",
                ),
            )
        }

        item {
            Column(
                Modifier.padding(horizontal = AtharSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
                    ) {
                        Text(
                            "أصبحنا وأصبح الملك لله، والحمد لله...",
                            fontFamily = QuranFont,
                            fontSize = 26.sp,
                            lineHeight = 46.sp,
                        )
                        AtharStatusPill("رواه مسلم")
                        Text(
                            "٢",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        LinearProgressIndicator(
                            progress = { 2f / 3f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("٢ من ٣", style = MaterialTheme.typography.labelLarge)
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 76.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Text(
                                "سبّح",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NightPreview() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.NightPrayer,
                    eyebrow = "قيام الليل",
                    title = "ليلٌ هادئ",
                    supportingText = "سجّل جلستك وتابع هدفك بهدوء، من غير مبالغة أو ضغط.",
                    statusText = "جاهز",
                    primaryActionLabel = "ابدأ جلسة",
                ),
                onPrimaryAction = {},
            )
        }

        item {
            Column(
                Modifier.padding(horizontal = AtharSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharMetric("٤٢", "آية الليلة", Modifier.weight(1f))
                    AtharMetric("٣", "جلسات هذا الأسبوع", Modifier.weight(1f))
                }

                AtharSectionHeader("هدف الليلة")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("٤٢ / ١٠٠ آية", style = MaterialTheme.typography.titleMedium)
                            AtharStatusPill("٤٢٪")
                        }
                        LinearProgressIndicator(
                            progress = { .42f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    AtharStatusPill("السجل")
                    AtharStatusPill("التقويم")
                    AtharStatusPill("الأدوات")
                }
            }
        }
    }
}

@Composable
private fun WatchPreview() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF111714)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.White.copy(alpha = .12f),
                ) {
                    Icon(
                        AtharIcons.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(18.dp)
                            .size(34.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = AtharSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        Text(
                            "شرح كتاب التوحيد — الدرس الخامس",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        ) {
                            AtharStatusPill("الشيخ محمد")
                            AtharStatusPill("٥ من ١٢")
                        }
                        LinearProgressIndicator(
                            progress = { .38f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                AtharSectionHeader("التعليقات")
                PreviewContentCard(
                    title = "أحمد",
                    subtitle = "جزاكم الله خيرًا، الشرح مرتب وواضح جدًا.",
                )

                AtharSectionHeader("باقي السلسلة")
                PreviewContentCard(
                    title = "الدرس السادس",
                    subtitle = "باب فضل التوحيد",
                )
            }
        }
    }
}

@Composable
private fun AudioPreview() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.Audio,
                    eyebrow = "الاستماع",
                    title = "سورة الكهف",
                    supportingText = "القارئ مشاري العفاسي",
                    statusText = "يعمل الآن",
                ),
            )
        }

        item {
            Column(
                Modifier.padding(horizontal = AtharSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("سورة الكهف", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "مشاري العفاسي",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Icon(
                                    AtharIcons.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .size(28.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { .27f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("١٢:٣٤")
                            Text("٤٥:١٠")
                        }
                    }
                }

                AtharSectionHeader("متابعة الآيات")
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "الْحَمْدُ لِلَّهِ الَّذِي أَنْزَلَ عَلَىٰ عَبْدِهِ الْكِتَابَ...",
                        modifier = Modifier.padding(AtharSpacing.Xl),
                        fontFamily = QuranFont,
                        fontSize = 24.sp,
                        lineHeight = 44.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BooksPreview() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.Books,
                    eyebrow = "المكتبة",
                    title = "رياض الصالحين",
                    supportingText = "الإمام النووي",
                    statusText = "متاح للتحميل",
                    primaryActionLabel = "تحميل الكتاب",
                ),
                onPrimaryAction = {},
            )
        }

        item {
            Column(
                Modifier.padding(horizontal = AtharSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                AtharSectionHeader("بيانات الكتاب")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharMetric("١٤٤٧ هـ", "الطبعة", Modifier.weight(1f))
                    AtharMetric("عربي", "اللغة", Modifier.weight(1f))
                }

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        AtharSectionHeader("الحقوق والمصدر")
                        Text("صاحب الحق: نسخة متاحة للنشر")
                        Text(
                            "أساس الإتاحة موضح قبل التنزيل، والمصدر الأصلي يمكن فتحه بشكل منفصل.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AtharStatusPill("تم التحقق من سلامة النسخة")
                    }
                }

                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text("فتح القارئ")
                }
            }
        }
    }
}

@Composable
private fun AccountPreview() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.Generic,
                    eyebrow = "الحساب",
                    title = "عمر إبراهيم",
                    supportingText = "omar@example.com",
                ),
            )
        }

        item {
            Column(
                Modifier.padding(horizontal = AtharSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                AtharSectionHeader("نشاطي")
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharMetric("٢٤", "ذكر مكتمل", Modifier.weight(1f))
                    AtharMetric("٧", "دروس مكتملة", Modifier.weight(1f))
                    AtharMetric("١٨٤", "دقيقة مشاهدة", Modifier.weight(1f))
                }

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        AtharSectionHeader("خطة الختمة")
                        Text(
                            "المتبقي ٣٨٠ صفحة",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "العبء المقترح اليومي: ١٢ صفحة",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { .37f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AtharStatusPill("متقدم ٨ صفحات عن الخطة")
                    }
                }

                AtharSectionHeader("الخصوصية والأمان")
                PreviewContentCard(
                    title = "تصدير بياناتي",
                    subtitle = "ملف خاص تختار أنت مكان حفظه.",
                )
                PreviewContentCard(
                    title = "الجلسات والأجهزة",
                    subtitle = "إدارة الجلسات الأخرى وحماية الحساب.",
                )
            }
        }
    }
}

@Composable
private fun PreviewContentCard(
    title: String,
    subtitle: String,
    status: String? = null,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(AtharSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                status?.let { AtharStatusPill(it, emphasized = true) }
            }
        }
    }
}
