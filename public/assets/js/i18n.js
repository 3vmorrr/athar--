(() => {
    const english = {
        'المصحف وأدوات القراءة': 'Quran reader and tools', 'اختر ما تريد فعله أولًا، وسنفتح لك مساحة مخصصة للقراءة أو الاستماع أو البحث.': 'Choose what you want to do first. We’ll open a dedicated space for reading, listening, or search.',
        'مراجعة التلاوة': 'Recitation review', 'استماع محلي': 'On-device listening', 'راجع موضعًا تختاره مع تنبيهات صوتية وبصرية للكلمات المحتمل الخطأ فيها.': 'Review a verse with visual and optional audio alerts for possible word errors.', 'ابدأ مراجعة التلاوة': 'Start recitation review',
        'مراجعة مجانية على جهازك': 'Free on-device review', 'راجع تلاوتك': 'Review your recitation', 'يُنَزَّل نموذج وبيانات قرآنية (نحو 75 ميجابايت) عند أول استخدام، ويحاول المتصفح حفظها محليًا. تعمل المراجعة داخل المتصفح ولا يُرفع صوتك إلى خادم. يحتاج التشغيل إلى اتصال أول مرة وميكروفون، وقد يستهلك ذاكرة الهاتف؛ اترك الصفحة مفتوحة أثناء القراءة.': 'A Quran model and its data (about 75 MB) download on first use; the browser tries to cache them locally. Review runs in your browser, and audio is not uploaded. An internet connection is needed for the first download, along with microphone access. It may use substantial phone memory; keep this page open while reciting.',
        'ابدأ الاستماع': 'Start listening', 'إنهاء المراجعة': 'Finish review', 'تنبيه صوتي عند رصد خطأ محتمل': 'Play a tone for a possible error', 'اختر السورة والآية أولًا.': 'Choose a surah and verse first.',
        'محرك المراجعة من': 'Review engine by', '، والنموذج وبياناته مرخصة بترخيص': ', with its model and data under', 'NPL-1.2 غير التجاري': 'NPL-1.2 non-commercial license', '. تظل الميزة مجانية ما دام هذا النموذج مستخدمًا؛ يلزم استبداله أو الحصول على ترخيص قبل أي استخدام تجاري. المراجعة استرشادية ولا تحكم على التجويد.': '. This feature must remain free and non-commercial while this model is used; replace it or obtain a license before commercial use. Alerts are advisory and do not grade tajweed.',
        'اختر السورة ورقم الآية ثم اضغط اختيار الآية.': 'Choose a surah and verse, then press Select verse.', 'اختيار الآية': 'Select verse', 'أعد الآية': 'Repeat the verse', 'تابع': 'Continue', 'تجاهل التنبيه': 'Dismiss alert',
        'حدد السورة ونطاق الآيات أولًا.': 'Choose a surah and verse range first.', 'حدد السورة وبداية ونهاية النطاق ثم اضغط تجهيز النطاق.': 'Choose a surah and range, then press Prepare range.', 'النموذج جاهز على هذا الجهاز. اضغط «ابدأ المراجعة».': 'The model is ready on this device. Press “Start review”.', 'ابدأ من الآية': 'Start at verse', 'انتهِ عند الآية': 'End at verse', 'نفس الآية': 'Same verse', 'تجهيز النطاق': 'Prepare range', 'ما التقطه النموذج': 'What the model heard', 'ستظهر الكلمات هنا عند بدء التلاوة.': 'Recognized words will appear here as you recite.',
        'حدد السورة وأول وآخر آية، ثم ابدأ. يظهر النص الملتقط وتقدّم الكلمات أثناء التلاوة. يعمل النموذج على جهازك؛ لا يُرفع صوتك، ويحتاج تنزيله أول مرة إلى اتصال (نحو 75 ميجابايت) ثم قد يحفظه المتصفح محليًا. اترك الصفحة مفتوحة.': 'Choose a surah and its first and last verses, then start. The recognized text and word progress appear as you recite. The model runs on your device; your voice is not uploaded. Its first download requires an internet connection (about 75 MB), and the browser may cache it afterward. Keep this page open.',
        'حدد السورة وبداية ونهاية النطاق ثم اضغط «تجهيز النطاق».': 'Choose a surah and range, then press “Prepare range”.', 'استأنف بعد المراجعة': 'Resume after review', 'استأنف المراجعة': 'Resume review', 'أعد القراءة': 'Retry recitation', 'أوقفنا الالتقاط عند موضع يحتاج مراجعة محتملة. أعده أو تابعه؛ قد يخطئ النموذج.': 'Capture paused at a possible issue. Repeat it or continue; the model can be wrong.', 'اضغط استأنف للمتابعة من الموضع الحالي.': 'Press Resume to continue from this point.', 'أعد قراءة الآية المحددة ثم سيكمل النموذج من نفس الموضع.': 'Repeat the flagged verse, then the model will continue from this point.',
        'جارٍ البحث في الحديث النبوي…': 'Searching the hadith collection…', 'جارٍ البحث في التفسير…': 'Searching tafsir…', 'جارٍ البحث في العقيدة…': 'Searching creed…', 'جارٍ البحث في الفقه…': 'Searching jurisprudence…', 'جارٍ البحث في الأخلاق…': 'Searching ethics…', 'جارٍ البحث في الآداب…': 'Searching etiquette…', 'جارٍ البحث في التاريخ…': 'Searching history…',
        'لم يُضف مفتاح الدرر المعتمد بعد. يلزم طلب موافقة الخدمة التي اخترتها ثم حفظ الرمز في إعدادات الإدارة.': 'The approved Dorar token is not configured. Request access to the selected service, then save the token in admin settings.',
        'آية اليوم · سورة': 'Verse of the day · Surah',
        'آية اليوم': 'Verse of the day', 'آية قرآنية تتغير يوميًا، مع انتقال مباشر إلى القراءة والاستماع والتفسير.': 'A Quran verse selected for the day, with direct links to reading, listening, and tafsir.',
        'ورد يومي': 'Daily reading', 'اقرأ آية اليوم': 'Read today’s verse', 'تثبيت الحفظ': 'Memorization practice', 'مراجعة الحفظ': 'Memorization review',
        'اخفِ نص الآيات واكشفها واحدةً واحدةً أثناء المراجعة.': 'Hide the verses and reveal them one at a time as you review.', 'ابدأ المراجعة': 'Start review',
        'الحزب': 'Hizb', 'بالحزب': 'By hizb',
        'استمع إلى الآية': 'Listen to the verse', 'افتح التفسير': 'Open tafsir',
        'موسوعة الدرر السنية': 'Dorar Encyclopedia', 'بحث موحّد بالمصادر': 'Source-linked search',
        'مثال: بر الوالدين': 'Example: kindness to parents', 'خدمات واجهة الدرر السنية ↗': 'Dorar API services ↗', 'تظهر النتيجة مع نسبتها ورابطها الأصلي.': 'Each result includes its attribution and original link.',
        'لا توجد نتائج لهذه العبارة. جرّب كلمات عربية أخرى.': 'No results for this phrase. Try other Arabic terms.', 'لم تتوفر نتائج مكتملة مرتبطة بمصدرها.': 'No complete, source-linked results were available.',
        'تعذر البحث الآن. تحقق من اتصال الخادم وحاول مرة أخرى.': 'Search is unavailable. Check the server connection and try again.',
        'ابحث بالعربية في الحديث والتفسير والعقيدة والفقه والأخلاق والآداب والتاريخ. كل نتيجة تعرض رابط مصدرها.': 'Search hadith, tafsir, creed, jurisprudence, ethics, etiquette, and history in Arabic. Every result links to its source.',
        'كلمة أو عبارة للبحث': 'Search term or phrase', 'الموسوعة': 'Collection', 'ابحث في الدرر': 'Search Dorar',
        'الحديث النبوي': 'Prophetic hadith', 'التفسير': 'Tafsir', 'العقيدة': 'Creed', 'الفقه': 'Jurisprudence', 'الأخلاق': 'Ethics', 'الآداب': 'Etiquette', 'التاريخ': 'History',
        'المصدر: الدرر السنية ↗': 'Source: Dorar ↗', 'افتح البحث في التفسير والعقيدة والفقه والأخلاق والآداب والتاريخ ↗': 'Search tafsir, creed, jurisprudence, ethics, etiquette, and history ↗',
        'الموسوعة الحديثية': 'Hadith encyclopedia',
        'اختر أداة القرآن': 'Choose a Quran tool', 'ابدأ القراءة': 'Start reading', 'اختر قارئًا': 'Choose a reciter', 'افتح التفسير': 'Open tafsir', 'اختر آية': 'Choose a verse', 'ابحث الآن': 'Search now',
        'موضع بعينه': 'A specific passage', 'آية محددة': 'Specific verse', 'الوصول السريع': 'Quick access', 'تدبر': 'Reflect',
        'تصفح القرآن بالسورة أو الجزء أو الصفحة، مع أدوات مريحة للقراءة.': 'Read by surah, juz, or page with comfortable reading tools.',
        'اختر قارئًا من مكتبة واسعة، وشغّل آية محددة أو سورة كاملة.': 'Choose from a large reciter library and play one verse or a complete surah.',
        'انتقل مباشرة إلى تفسير الآية التي تختارها.': 'Go straight to the tafsir for the verse you select.',
        'افتح آية واحدة، ثم اقرأها أو استمع إليها أو اعرض تفسيرها.': 'Open a single verse, then read, listen, or view its tafsir.',
        'اكتب كلمة أو عبارة عربية للوصول إلى مواضعها في القرآن.': 'Search for an Arabic word or phrase in the Quran.',
        'القراءة': 'Reading', 'البحث في القرآن': 'Quran search', 'كل أدوات القرآن': 'All Quran tools', 'مكتبة القراء': 'Reciter library', 'الموسوعة': 'Encyclopedia',
        'رقم الآية': 'Verse number', 'تجهيز الاستماع': 'Prepare audio', 'عرض الآية والتفسير': 'Show verse and tafsir', 'افتح الآية': 'Open verse',
        'اختر نطاق الاستماع': 'Choose what to play', 'ماذا تريد أن تستمع؟': 'What would you like to hear?', 'آية واحدة': 'One verse',
        'ابدأ من رقم الآية المحددة فقط': 'Play only the selected verse', 'السورة كاملة': 'Full surah', 'تشغيل كامل السورة من بدايتها': 'Play the surah from its beginning', 'تكرار السورة': 'Repeat surah', 'تكرار التلاوة': 'Repeat recitation',
        'السور الكاملة عبر': 'Full surahs via', 'والآيات المنفردة عبر': 'and individual verses via', 'اختر السورة ورقم الآية ثم اضغط عرض.': 'Choose a surah and verse, then select Show.',
        '، والآيات المنفردة عبر': ', and individual verses via',
        'الآية الحالية فقط': 'Selected verse only', 'السورة كاملة من بدايتها': 'Full surah from the beginning',
        'افتح آية واحدة واقرأ نصها وتفسيرها مباشرة.': 'Open a single verse and read its text and tafsir.',
        'افتح آية واحدة واقرأ نصها أو اعرض تفسيرها مباشرة.': 'Open a single verse to read its text or view its tafsir.',
        'اختيار السورة الكاملة يفتح مكتبة QuranicAudio الأوسع؛ والآية المنفردة تستخدم قراء Al Quran Cloud.': 'Full-surah playback opens the broader QuranicAudio library; individual verses use Al Quran Cloud reciters.',
        'اختر من مكتبة قراء السور الكاملة على QuranicAudio.': 'Choose from QuranicAudio’s full-surah reciter library.',
        'الآية المنفردة متاحة من قراء Al Quran Cloud؛ اختر السورة كاملة للوصول إلى المكتبة الأوسع.': 'Individual verses use Al Quran Cloud reciters. Choose the full surah for the larger library.',
        'التفسير من Quranpedia، والبحث ونص المصحف والتلاوة آيةً آية من Al Quran Cloud. السور الكاملة من مكتبة QuranicAudio. لا ننشئ نسخة محلية من النص أو ملفات الصوت.': 'Tafsir comes from Quranpedia; search, Quran text, and verse-by-verse audio use Al Quran Cloud. Full-surah recitations come from QuranicAudio. We do not host copies of the text or audio.',
        'انتقل إلى المحتوى': 'Skip to content', 'الصفحة الرئيسية': 'Home', 'التنقل الرئيسي': 'Main navigation',
        'القرآن': 'Quran', 'الحديث': 'Hadith', 'الكتب': 'Books', 'الدروس': 'Lessons', 'الأذكار': 'Adhkar',
        'القرآن الكريم': 'The Holy Quran', 'الحديث النبوي': 'Prophetic Hadith', 'الأذكار والأدعية': 'Dhikr & Supplications',
        'المكتبة': 'Library', 'الدروس والمحاضرات': 'Lessons & Lectures', 'عن أثر': 'About Athar', 'عن المنصة': 'About the platform',
        'استكشف أثر': 'Explore Athar', 'حسابك': 'Your account', 'تسجيل الدخول': 'Sign in', 'دخول': 'Sign in', 'خروج': 'Sign out',
        'إنشاء حساب': 'Create account', 'إنشاء حساب جديد': 'Create an account', 'أهلًا بعودتك': 'Welcome back',
        'أنشئ حسابك في أثر': 'Create your Athar account', 'حسابي': 'My account', 'ملفي الشخصي': 'My profile', 'الإدارة': 'Admin',
        'الوضع الداكن': 'Dark mode', 'الوضع الفاتح': 'Light mode', 'فتح القائمة': 'Open menu', 'إغلاق القائمة': 'Close menu',
        'منصة عربية مفتوحة للقرآن والمعرفة والعبادة.': 'An open platform for Quran, knowledge, and worship.',
        'مساحة هادئة للقرآن والمعرفة والعبادة.': 'A welcoming space for Quran, knowledge, and worship.',
        'محتوى منظّم، ومصادر واضحة، وتجربة مريحة على كل جهاز.': 'Organized content, clear sources, and a comfortable experience on every device.',
        'تابع قراءتك واحفظ تقدّمك وإحصاءاتك.': 'Continue reading and keep track of your progress and activity.',
        'صُمّم ليكون قريبًا منك، أينما قرأت.': 'Made to feel close, wherever you read.', 'الرئيسية': 'Home', 'العودة إلى الرئيسية': 'Back to home',
        'الصفحة غير موجودة': 'Page not found', 'العودة': 'Go back', 'عرض المزيد': 'Show more', 'عرض الكل': 'View all',
        'ابحث': 'Search', 'بحث': 'Search', 'بحث في القرآن': 'Search the Quran', 'ابحث في القرآن': 'Search the Quran',
        'كلمة أو عبارة من الآية': 'A word or phrase from a verse', 'ابحث في القرآن': 'Search the Quran',
        'عرض الموضع': 'Open passage', 'عرض الآيات': 'Show verses', 'طريقة التصفح': 'Browse by', 'بالسورة': 'Surah', 'بالجزء': 'Juz', 'بالحزب': 'Hizb', 'بالصفحة': 'Page',
        'السورة': 'Surah', 'الجزء': 'Juz', 'رقم الصفحة': 'Page number', 'ابدأ من الآية (اختياري)': 'Start at verse (optional)',
        'أول السورة': 'Beginning of surah', 'حجم الخط': 'Text size', 'تصغير الخط': 'Decrease text size', 'تكبير الخط': 'Increase text size',
        'اختر موضع القراءة': 'Choose a passage', 'المصحف': 'Reader', 'أدوات القرآن': 'Quran tools', 'القراءة': 'Read', 'الاستماع': 'Listen',
        'البحث': 'Search', 'التفسير': 'Tafsir', 'استمع واختر القارئ': 'Listen and choose a reciter', 'مشغل التلاوة': 'Recitation player',
        'القارئ': 'Reciter', 'نطاق الاستماع': 'Playback range', 'من الموضع المحدد': 'Current verse only', 'السورة حتى آخرها': 'Rest of this surah',
        'الموضع المعروض كاملًا': 'Entire displayed passage', 'تكرار الآية الحالية': 'Repeat current verse', 'الآية السابقة': 'Previous verse',
        'الآية التالية': 'Next verse', 'وضع مراجعة الحفظ': 'Memorization review', 'إنهاء مراجعة الحفظ': 'Finish review',
        'أظهر الآية': 'Reveal verse', 'عرض التفسير': 'Show tafsir', 'إخفاء التفسير': 'Hide tafsir', 'تفسير الآية': 'Verse tafsir',
        'ابحث في القرآن': 'Search Quran', 'مثال: إن مع العسر يسرا': 'Example: Indeed, with hardship comes ease',
        'النص بالرسم العثماني والتلاوة عبر': 'Uthmani text and recitation provided by', 'مصدر النص والتلاوة: Al Quran Cloud': 'Text and recitation: Al Quran Cloud',
        'اختر السورة والقارئ، ثم اضغط تشغيل.': 'Choose a surah and reciter, then press play.',
        'الموضع جاهز. اضغط تشغيل للاستماع.': 'Ready. Press play to listen.',
        'عند دعم المتصفح، تظهر بيانات التلاوة وأزرار التحكم في شاشة القفل وسماعة الهاتف.': 'Where supported, recitation details and playback controls appear on your lock screen and headset.',
        'جارٍ تحميل الآيات…': 'Loading verses…', 'جارٍ تجهيز موضع القراءة…': 'Preparing passage…', 'الآن:': 'Now:',
        'تعذر تحميل الآيات الآن. تحقق من الاتصال ثم حاول مجددًا.': 'Could not load the verses. Check your connection and try again.',
        'إعادة المحاولة': 'Try again', 'اكتملت التلاوة لهذا النطاق.': 'Playback range complete.',
        'تعذر تشغيل التلاوة. اختر قارئًا آخر أو تحقق من الاتصال.': 'Could not play this recitation. Try another reciter or check your connection.',
        'اضغط تشغيل من مشغل الصوت لبدء التلاوة.': 'Press play on the audio player to start recitation.',
        'النص من': 'Text from', 'والتلاوة من': 'and recitation from', 'سجّل الدخول للحفظ': 'Sign in to save progress',
        'الشيخ': 'Sheikh', 'السلاسل': 'Series', 'فيديوهات حرة': 'Standalone videos', 'كل الدروس': 'All lessons', 'شاهد الدرس': 'Watch lesson',
        'عرض السلسلة': 'View series', 'ابدأ المشاهدة': 'Start watching', 'الفيديوهات': 'Videos', 'التعليقات': 'Comments', 'اكتب تعليقك': 'Write a comment',
        'أرسل تعليقك': 'Post comment', 'لا توجد تعليقات بعد.': 'No comments yet.', 'الكتب الإسلامية': 'Islamic books', 'تحميل الكتاب': 'Download book',
        'تحميل مباشر': 'Direct download', 'الأذكار اليومية': 'Daily adhkar', 'الصباح': 'Morning', 'المساء': 'Evening', 'بعد الصلاة': 'After prayer',
        'العدد': 'Count', 'تقدمك اليوم': 'Your progress today', 'تم': 'Done', 'احفظ': 'Save', 'حفظ': 'Save', 'حفظ التغييرات': 'Save changes',
        'الاسم': 'Name', 'الاسم الظاهر': 'Display name', 'اسم المستخدم': 'Username', 'كلمة المرور': 'Password', 'كلمة المرور الجديدة': 'New password',
        'البريد الإلكتروني': 'Email address', 'رمز البريد الإلكتروني': 'Email verification code', 'نسيت كلمة المرور؟': 'Forgot your password?',
        'استعادة كلمة المرور': 'Reset password', 'إرسال رمز الاستعادة': 'Send recovery code', 'تحديث كلمة المرور': 'Update password',
        'ليس لديك حساب؟': 'New to Athar?', 'لديك حساب؟': 'Already have an account?', 'العودة إلى تسجيل الدخول': 'Back to sign in',
        'بيانات الحساب': 'Account details', 'تعديل الملف الشخصي': 'Edit profile', 'إحصاءاتك': 'Your activity', 'موضع القراءة': 'Reading position',
        'علاماتي المحفوظة': 'Saved bookmarks', 'لا توجد بيانات قرآن محفوظة محليًا.': 'No Quran data is saved in this browser.',
        'الاسم الظاهر<input': 'Display name<input', 'إعدادات البريد': 'Email settings', 'حفظ الإعدادات': 'Save settings',
        'تواصل معنا': 'Contact', 'المصادر': 'Sources', 'جميع الحقوق محفوظة': 'All rights reserved', 'المحتوى': 'Content', 'المزيد': 'More',
        'الكتب والدروس': 'Books and lessons', 'اكتشف المزيد': 'Explore more', 'المميز اليوم': 'Featured today', 'أحدث الدروس': 'Latest lessons',
        'كتب مختارة': 'Featured books', 'المزيد من القرآن': 'More Quran', 'أذكار وأدعية': 'Dhikr and supplications',
        'الموقع غير متاح مؤقتًا': 'This page is temporarily unavailable', 'جارٍ التحميل…': 'Loading…', 'لا توجد نتائج.': 'No results found.',
        'لا توجد دروس منشورة حاليًا.': 'No published lessons yet.', 'لا توجد كتب منشورة حاليًا.': 'No published books yet.',
        'لا توجد سلاسل منشورة حاليًا.': 'No published series yet.', 'جميع الأقسام': 'All sections', 'التالي': 'Next', 'السابق': 'Previous',
        'حساب جديد': 'New account', 'معلوماتك محفوظة بأمان.': 'Your information is stored securely.', 'أنشئ حسابك': 'Create your account',
        'مساحةٌ للعلم والعبادة': 'A space for learning and worship', 'معرفةٌ تنفع،': 'Knowledge that benefits,', 'وأثرٌ يبقى.': 'and a lasting impact.',
        'اقرأ القرآن، وابحث في مصادر إسلامية متاحة، وتابع ما ينفعك — في تجربة عربية هادئة ومجانية.': 'Read the Quran, explore Islamic sources, and follow what benefits you in a calm, free experience.',
        'ابدأ بالقرآن': 'Start with the Quran', 'استكشف الدروس': 'Explore lessons', 'تصفّح واقرأ دون الحاجة إلى إنشاء حساب': 'Browse and read without creating an account',
        'اقرأ': 'Read', 'تجربة القراءة': 'A better reading experience', 'قراءةٌ واضحة': 'Clear reading', 'ومصدرٌ ظاهر': 'with visible sources', 'المصدر ظاهر مع النص': 'Sources shown with the text',
        'مساراتك في أثر': 'Explore Athar', 'اختر ما تبحث عنه': 'Choose what you are looking for', 'مجالاتٌ متكاملة، ومصادرها واضحة.': 'Connected areas of knowledge, with clear sources.',
        'قراءة مباشرة مع اختيار السورة والآية وتغيير حجم الخط.': 'Read and choose a surah and verse, with adjustable text size.',
        'بحثٌ مع توضيح المصدر والمرجع والدرجة عند ورودها.': 'Search with source, reference, and grading details where available.',
        'بحث في فهرس كتب التفسير وعلوم القرآن.': 'Search a catalog of Quranic commentary and Quran studies books.',
        'شغّل درسًا عبر المشغل الرسمي بإدخال رابطه.': 'Play a lesson in the official player using its link.',
        'أذكارٌ مصنفة بنصوصها ومراجعها.': 'Organized adhkar with their texts and references.', 'متاح': 'Available',
        'محتوى الموقع': 'From the library', 'ابدأ من هنا': 'Start here', 'الدروس والسلاسل': 'Lessons and series',
        'السلاسل العلمية': 'Scholarly series', 'الكتب المتاحة': 'Available books', 'استكشف الدروس المنشورة في مكتبة العلماء.': 'Explore lessons published in the scholars’ library.',
        'تظهر هنا السلاسل التي يضيفها فريق المحتوى.': 'Series added by the content team appear here.', 'تصفح السلاسل': 'Browse series', 'فتح المكتبة': 'Open the library',
        'المصدر جزءٌ من الأمانة': 'Sources are part of our trust', 'نعرض النصوص مع مراجعها ومعلومات مصادرها. أثر منصةٌ للمعرفة والتنظيم، وليس جهةً لإصدار الفتاوى.': 'We present texts with their references and source details. Athar organizes knowledge; it does not issue religious rulings.',
        'عن منهجية المصادر': 'Our source policy', 'العودة إلى الرئيسية': 'Back to home',
        'الموسوعة الحديثية': 'Hadith Encyclopedia', 'ابحث بالعربية، وتظهر خلاصة حكم المحدّث مع كل نتيجة ورابطها المرجعي.': 'Search in Arabic. Each result includes the hadith grading summary and a reference link.',
        'كلمة أو عبارة في الحديث': 'A word or phrase in the hadith', 'مثال: إنما الأعمال بالنيات': 'Example: Deeds are judged by intentions',
        'المصدر:': 'Source:', 'الموسوعة الحديثية في الدرر السنية ↗': 'Dorar Hadith Encyclopedia ↗',
        'فهرس الكتب المتصلة بالقرآن': 'Quran-related book catalog', 'ابحث عن كتب التفسير وعلوم القرآن مع اسم المؤلف ومعلومات المصدر المتاحة.': 'Search Quranic commentary and Quran studies books by title, author, and available source details.',
        'عنوان أو موضوع الكتاب': 'Book title or topic', 'مثال: تفسير': 'Example: commentary', 'ابحث في الفهرس': 'Search the catalog',
        'الفهرس من': 'Catalog by', 'هذه نتائج ببليوغرافية وليست ملفات PDF؛ الإتاحة وحقوق كل كتاب يحددها ناشره.': 'These are catalog records, not PDF files. The publisher determines availability and rights.',
        'ابحث في مكتبة أثر المحلية': 'Search Athar’s library', 'عنوان أو مؤلف أو كلمات مفتاحية': 'Title, author, or keywords', 'بحث في كتب أثر': 'Search Athar books',
        'الكتب المحلية:': 'Athar books:', 'نتيجة · الصفحة': 'results · page', 'من': 'of', 'إتاحة موثقة من إدارة أثر': 'Availability verified by Athar',
        'كتب مختارة': 'Selected books', 'حقوق الإتاحة موثقة': 'Availability rights verified', 'المؤلف:': 'Author:', 'صاحب الحقوق:': 'Rights holder:',
        'العربية': 'Arabic', 'الإنجليزية': 'English', 'لغة أخرى': 'Other language', 'تنزيل الكتاب': 'Download book', 'المصدر ↗': 'Source ↗',
        'إزالة من المفضلة': 'Remove from favorites', 'أضف للمفضلة': 'Add to favorites', 'سجّل الدخول للحفظ': 'Sign in to save',
        'لا توجد كتب محلية تطابق البحث.': 'No local books match your search.', 'صفحات الكتب': 'Book pages', 'السابقة': 'Previous', 'التالية': 'Next',
        'مكتبة الدروس': 'Lesson library', 'الدروس والسلاسل': 'Lessons and series', 'ابدأ من صفحة الشيخ، تابع السلاسل ودروسها بالترتيب، أو شاهد درسًا مستقلًا.': 'Start with a scholar, follow a series in order, or watch a standalone lesson.',
        'تصفح حسب الشيخ': 'Browse by scholar', 'العلماء': 'Scholars', 'السلاسل والدروس': 'Series and lessons', 'مرتبة حسب الشيخ': 'Organized by scholar',
        'فيديوهات خارج السلاسل': 'Standalone videos', 'دروس مستقلة': 'Standalone lessons', 'للشيخ': 'By Sheikh', 'درس منشور': 'published lessons', 'درس مستقل': 'Standalone lesson',
        'سجّل الدخول لحفظ الدرس': 'Sign in to save this lesson', 'فتح الفيديو على YouTube ↗': 'Open video on YouTube ↗',
        'مشاركة المشاهدين': 'Viewer discussion', 'التعليقات': 'Comments', 'اكتب تعليقك': 'Write a comment', 'إرسال للمراجعة': 'Submit for review',
        'يظهر التعليق بعد مراجعته.': 'Comments appear after review.', 'سجّل الدخول': 'Sign in', 'للمشاركة بتعليق.': 'to leave a comment.', 'بانتظار المراجعة': 'Awaiting review',
        'مشاهدة مباشرة': 'Watch a video', 'فيديو حر': 'Standalone video', 'أدخل رابط فيديو YouTube لمشاهدته بالمشغل الرسمي. لا نحمّل الفيديو ولا نخفي أدوات المشغل أو إعلاناته.': 'Enter a YouTube link to watch it in the official player. We do not download videos or hide player controls or ads.',
        'رابط فيديو YouTube': 'YouTube video link', 'عرض الفيديو': 'Play video', 'المشغل الرسمي من': 'Official player by',
        'أهلًا بعودتك': 'Welcome back', 'أنشئ حسابك في أثر': 'Create your Athar account', 'تابع قراءتك وعلاماتك وخطة الختمة.': 'Continue reading, bookmarks, and your Quran plan.',
        'احفظ موضع قراءتك وإحصاءاتك وخطة الختمة.': 'Save your reading position, activity, and Quran plan.', 'أدخل بريد حسابك لإرسال رمز إعادة التعيين.': 'Enter your account email to receive a reset code.',
        'أدخل رمز البريد وكلمة مرور جديدة لا تقل عن 10 أحرف.': 'Enter the email code and a new password of at least 10 characters.', 'اسم المستخدم<input': 'Username<input',
        '3–24 حرفًا إنجليزيًا صغيرًا أو رقمًا أو شرطة سفلية.': '3–24 lowercase English letters, numbers, or underscores.',
        'مراجعة ودمج موضع القراءة وعلامات القرآن المحفوظة محليًا في هذا المتصفح فقط': 'Review and merge reading position and Quran bookmarks saved in this browser only',
        'لا يوجد دمج حتى أختاره وأؤكده.': 'Nothing will be merged until you select and confirm.', 'إنشاء الحساب': 'Create account',
        'التصفح العام متاح دون حساب. لا تُرسل بياناتك إلا عند اختيار الحفظ.': 'Public browsing is available without an account. Your details are sent only when you choose to save.',
        'مساحة شخصية اختيارية': 'Optional personal account', 'تعيين كلمة مرور جديدة': 'Set a new password', 'لا توجد بيانات قرآن محفوظة محليًا.': 'No Quran data is saved in this browser.',
        'روابط الموقع': 'Explore', 'مكتبة الدروس': 'Lesson library', 'المكتبة': 'Library', 'مكتبة أثر': 'Athar Library',
        'الوضع الداكن': 'Dark mode', 'الوضع الفاتح': 'Light mode', 'مساحة هادئة للقرآن والمعرفة والعبادة.': 'A calm space for Quran, knowledge, and worship.',
        'محتوى منظّم، ومصادر واضحة، وتجربة مريحة على كل جهاز.': 'Organized content, clear sources, and a comfortable experience on every device.',
        'تابع قراءتك واحفظ تقدّمك وإحصاءاتك.': 'Keep track of your reading, progress, and activity.', 'صُمّم ليكون قريبًا منك، أينما قرأت.': 'Made to be with you, wherever you read.',
        'قراءة ومصادر': 'Reading & sources', 'قراءة القرآن والاستماع': 'Read and listen to the Quran', 'البحث في الحديث': 'Search hadith',
        'جديد أثر': 'New on Athar', 'أحدث المحاضرات': 'Latest lectures', 'تابع أحدث ما أُضيف إلى مكتبة الدروس.': 'Explore the newest additions to the lesson library.',
        'كل المحاضرات': 'All lectures', 'شاهد المحاضرة:': 'Watch lecture:', 'محاضرة مستقلة': 'Standalone lecture', 'شاهد المحاضرة': 'Watch lecture',
        'قريبًا في مكتبة الدروس': 'Coming soon to the lesson library', 'ستظهر هنا أحدث المحاضرات التي يضيفها فريق المحتوى.': 'The latest lectures added by the content team will appear here.',
        'مساحات للمعرفة': 'Explore the library', 'اكتشف المزيد': 'Discover more', 'كتب متاحة للقراءة': 'Books to read', 'تظهر هنا السلاسل المنشورة من العلماء.': 'Published scholar series appear here.',
        'تصفح كتب التفسير وعلوم القرآن المتاحة للجميع.': 'Browse public books on Quranic studies and tafsir.',
        'سياسة المحتوى والإحالات': 'Content and source policy', 'ملفي الشخصي والإحصاءات': 'Profile and activity', 'إنشاء حساب اختياري': 'Create an optional account',
        'مساحتك الخاصة': 'Your personal space', 'علاماتك وتقدمك لا يراها غيرك.': 'Your bookmarks and progress are private.', 'نشاطك في أثر': 'Your activity on Athar',
        'جلسات القرآن': 'Quran sessions', 'دروس مكتملة': 'Lessons completed', 'أذكار مكتملة': 'Adhkar completed', 'كتب نُزّلت': 'Books downloaded', 'أيام نشطة': 'Active days',
        'نحتفظ بأعداد يومية إجمالية فقط، من دون سجلّ بأسماء الآيات أو الأذكار التي قرأتها.': 'We keep daily totals only, not a record of the verses or adhkar you read.',
        'البريد:': 'Email:', 'اسم المستخدم:': 'Username:', 'كلمة المرور الحالية': 'Current password', 'حفظ بيانات الحساب': 'Save account details',
        'تأكيد البريد الإلكتروني': 'Verify email', 'حالة البريد: غير مؤكد. أدخل الرمز الذي أرسلناه إلى': 'Email status: unverified. Enter the code sent to',
        'رمز التحقق': 'Verification code', 'تأكيد البريد': 'Verify email', 'إرسال رمز جديد': 'Send a new code', 'تم تأكيد البريد الإلكتروني.': 'Email verified.',
        'آخر موضع قراءة': 'Last reading position', 'تابع القراءة': 'Continue reading', 'سيظهر موضعك هنا بعد القراءة وتسجيل الدخول.': 'Your position will appear here after you read while signed in.',
        'افتح القرآن الكريم': 'Open the Quran', 'خطة الختمة': 'Quran completion plan', 'المتبقي': 'Remaining', 'صفحة حتى': 'pages until', 'الحالة:': 'Status:',
        'نشطة': 'Active', 'متوقفة مؤقتًا': 'Paused', 'مكتملة': 'Complete', 'المعدل الحالي:': 'Current pace:', 'صفحة يوميًا': 'pages per day', 'على': 'for', 'يومًا متبقيًا.': 'days remaining.',
        'إيقاف مؤقت': 'Pause', 'استئناف': 'Resume', 'إنهاء الخطة': 'Finish plan', 'اختر موعدًا مستهدفًا، ويمكنك تعديل خطتك لاحقًا.': 'Choose a target date. You can change your plan later.',
        'موعد الإتمام': 'Target date', 'الصفحات المنجزة من 604': 'Pages completed out of 604', 'حفظ الخطة': 'Save plan', 'مرجعك الشخصي': 'Your library',
        'الآيات المحفوظة': 'Saved verses', 'تصدير بياناتي JSON': 'Export my data (JSON)', 'لم تحفظ آيات بعد. استخدم زر الحفظ بجوار الآية في صفحة القرآن.': 'No saved verses yet. Use the bookmark button beside a verse on the Quran page.',
        'حذف هذه العلامة؟': 'Delete this bookmark?', 'حذف': 'Delete', 'قائمة شخصية': 'Personal list', 'المفضلة': 'Favorites', 'لم تضف كتبًا أو دروسًا إلى المفضلة بعد.': 'You have not added any books or lessons to favorites yet.',
        'كتاب': 'Book', 'درس': 'Lesson', 'عرض': 'View', 'مشاهدة الدروس': 'Lesson activity', 'تقدم الدروس': 'Lesson progress',
        'سيظهر تقدم الدروس التي تتابعها من فهرس أثر هنا.': 'Progress for lessons you watch from Athar will appear here.', 'مكتمل': 'Completed', 'آخر موضع محفوظ عند': 'Last saved position:', 'ثانية': 'seconds', 'استئناف تقريبي': 'Resume lesson',
        'حذف الحساب': 'Delete account', 'سيؤدي ذلك إلى حذف الحساب وعلاماته وتقدمه وخطة الختمة. قد يُحتفظ بسجل تدقيق محدود لعملية الحذف لأغراض أمنية وتشغيلية.': 'This deletes your account, bookmarks, progress, and Quran plan. A limited audit record may be retained for security and operations.',
        'أكد كلمة المرور': 'Confirm password', 'حذف حسابي': 'Delete my account', 'سيُحذف الحساب وبياناته نهائيًا، مع الاحتفاظ بسجل تدقيق محدود. هل تريد المتابعة؟': 'Your account and data will be deleted permanently, with a limited audit record retained. Continue?',
        'تغيير كلمة المرور': 'Change password', 'سيؤدي حفظ كلمة مرور جديدة إلى إبطال الجلسات الأخرى على أجهزتك.': 'Saving a new password signs you out of your other devices.',
        'كلمة المرور الجديدة': 'New password', 'تفضيلات الحساب': 'Account preferences', 'المظهر': 'Appearance', 'فاتح': 'Light', 'داكن': 'Dark', 'حفظ التفضيلات': 'Save preferences',
        'إدارة المحتوى': 'Content management', 'لوحة أثر': 'Athar dashboard', 'تظهر المواد المنشورة للزوار. راجع حقوق النشر والمصدر قبل الإتاحة.': 'Published materials are visible to visitors. Check source and usage rights before publishing.',
        'خدمات المنصة': 'Platform services', 'البريد والبحث الحديثي': 'Email and hadith search', 'خادم SMTP': 'SMTP host', 'المنفذ': 'Port', 'التشفير': 'Encryption',
        'اسم المستخدم': 'Username', 'كلمة مرور SMTP': 'SMTP password', 'محفوظة؛ اتركها فارغة للإبقاء عليها': 'Saved; leave blank to keep it', 'أدخل كلمة المرور': 'Enter password',
        'حذف كلمة المرور المحفوظة': 'Remove saved password', 'بريد المرسل': 'Sender email', 'اسم المرسل': 'Sender name', 'رمز API المعتمد للدرر السنية': 'Approved Dorar API token',
        'محفوظ؛ اتركه فارغًا للإبقاء عليه': 'Saved; leave blank to keep it', 'أدخل الرمز بعد موافقة الدرر': 'Enter the token after Dorar approval', 'حذف رمز API المحفوظ': 'Remove saved API token',
        'حالة ربط البحث:': 'Search connection:', 'مُعدّ': 'Configured', 'ينتظر رمز API رسميًا': 'Waiting for an official API token', 'تطلب الدرر السنية موافقة يدوية على الوصول.': 'Dorar requires manual approval for access.',
        'حفظ الإعدادات': 'Save settings', 'حفظ وإرسال رسالة اختبار إلى بريدي': 'Save and send a test email to my address', 'نظرة عامة': 'Overview',
        'الحسابات': 'Accounts', 'العلماء المنشورون': 'Published scholars', 'السلاسل المنشورة': 'Published series', 'الدروس المنشورة': 'Published lessons', 'الكتب المنشورة': 'Published books',
        'عمليات المالك': 'Owner operations', 'نسخ احتياطية مشفرة': 'Encrypted backups', 'إضافة عالم': 'Add scholar', 'إضافة سلسلة': 'Add series', 'إضافة درس YouTube': 'Add YouTube lesson',
        'إضافة كتاب': 'Add book', 'إضافة ذكر مُراجع': 'Add reviewed dhikr', 'محتوى محفوظ': 'Saved content', 'إدارة الحسابات': 'Manage accounts', 'التعليقات بانتظار المراجعة': 'Comments awaiting review',
        'البحث في النص أو الاسم': 'Search text or title', 'نوع المحتوى': 'Content type', 'الأدعية': 'Supplications', 'الصلاة على النبي ﷺ': 'Blessings upon the Prophet ﷺ',
        'تُعرض مراجع الأذكار مع كل نصّ، وتظهر بقية النتائج عند طلب المزيد.': 'References are shown with each dhikr. Load more to see additional results.', 'مختارة ومراجَعة للموقع': 'Selected and reviewed for Athar',
        'أذكار موثقة': 'Referenced adhkar', 'عدد التكرار كما ورد بالمصدر:': 'Repetitions stated by source:', 'المرجع:': 'Reference:',
        'منهجية أثر': 'About Athar', 'نعرض المعرفة مع الإحالة إلى مصدرها، ونفصل بين نص المصدر وأدوات الموقع. أثر منصة للقراءة والتنظيم، وليست جهةً لإصدار الفتاوى.': 'We link knowledge to its sources and distinguish source material from site tools. Athar organizes reading and knowledge; it does not issue religious rulings.',
        'القرآن والتفسير والتلاوة': 'Quran, tafsir, and recitation', 'قراءة وتفسير مباشر من Quranpedia، والبحث والتلاوة عبر Al Quran Cloud. لا ننشئ نسخة محلية من النص أو ملفات الصوت.': 'Live Quran text and tafsir from Quranpedia, with search and recitation via Al Quran Cloud. We do not host local copies of the text or audio.',
        'البحث باللغة العربية من موسوعة الدرر السنية. نعرض نص الحديث ودرجته ومرجعه ورابطه إلى المصدر كما وردت، ولا ننسب حكمًا من عندنا.': 'Arabic search through Dorar’s Hadith Encyclopedia. Results retain the supplied text, grading, reference, and source link.',
        'تُعرض الأذكار مع المراجع المتاحة، ويمكن تصفحها كاملةً عبر أقسامها وصفحاتها. نحتفظ بإحصاء إجمالي لما أتممه المستخدم دون تسجيل الذكر الذي قرأه.': 'Adhkar are shown with available references and can be browsed by section. We keep completion totals without recording which adhkar you read.',
        'التشغيل عبر المشغل الرسمي، وتوافر الفيديو يعتمد على إعدادات صاحبه وسياسات YouTube.': 'Videos play in the official player. Availability depends on the video owner and YouTube policies.',
        'مراجعة خاصة على جهازك': 'Private on-device review', 'راجع تلاوتك كلمةً كلمة': 'Review your recitation word by word', 'الصوت لا يغادر جهازك': 'Audio stays on your device',
        'اختر السورة والآيات ثم ابدأ التلاوة. تبقى مساحة المتابعة فارغة حتى تبدأ، ثم تكشف الكلمات بالتدريج مع تقدّمك. يعمل النموذج محليًا؛ يلزم اتصال لتنزيله أول مرة (نحو 75 ميجابايت)، واترك الصفحة مفتوحة أثناء المراجعة.': 'Choose a surah and verse range, then start reciting. The reading area stays blank until you begin, then reveals Quran text as you progress. The model runs locally; its first download requires internet (about 75 MB). Keep this page open during review.',
        'متابعة التلاوة': 'Recitation progress', 'جاهز للاستماع': 'Ready to listen', 'ستظهر الكلمات هنا تدريجيًا بعد بدء التلاوة.': 'Words will appear here gradually after you start reciting.', 'تقدم النطاق': 'Passage progress',
        'مصحف المراجعة': 'Review mushaf', 'اختر نطاق الآيات للبدء': 'Choose a verse range to begin', 'صفحة المراجعة': 'Review sheet', 'مواضع الآيات جاهزة؛ ستظهر الكلمات هنا مع تقدمك في التلاوة.': 'Verse spaces are ready; the words will appear here as you recite.',
        'ينتظر صوتك': 'Waiting for your voice', 'الصوت يصل بوضوح': 'Audio detected', 'لم نلتقط صوتًا واضحًا؛ تحقق من الميكروفون': 'No clear audio yet; check the microphone', 'مراجعة مطلوبة': 'Review needed', 'اكتملت المحاولة': 'Review complete',
    };
    const patterns = [
        [/^شاهد المحاضرة: (.+)$/, 'Watch lecture: $1'],
        [/^سورة (.+) · (\d+) آية$/, 'Surah $1 · $2 verses'],
        [/^الجزء (\d+) · (\d+) آية$/, 'Juz $1 · $2 verses'],
        [/^صفحة (\d+) · (\d+) آية$/, 'Page $1 · $2 verses'],
        [/^الآية (\d+)$/, 'Verse $1'],
        [/^الآية (\d+) من (.+)$/, 'Verse $1 of $2'],
        [/^سورة (.+)$/, 'Surah $1'],
        [/^الجزء (\d+)$/, 'Juz $1'],
        [/^صفحة (\d+)$/, 'Page $1'],
        [/^الآن: (.+) · الآية (\d+)$/, 'Now: $1 · Verse $2'],
        [/^الآن: سورة (.+) كاملة$/, 'Now: Surah $1, complete'],
        [/^الآية (\d+) · (.+)$/, 'Verse $1 · $2'],
        [/^الحزب (\d+)$/, 'Hizb $1'],
        [/^الجزء (\d+) · (\d+) آية$/, 'Juz $1 · $2 verses'],
        [/^الحزب (\d+) · (\d+) آية$/, 'Hizb $1 · $2 verses'],
        [/^آية اليوم · سورة (.+) · الآية (\d+)$/, 'Verse of the day · Surah $1 · verse $2'],
    ];
    const translate = (value) => {
        const exact = english[value];
        if (exact) return exact;
        for (const [pattern, replacement] of patterns) if (pattern.test(value)) return value.replace(pattern, replacement);
        return value;
    };
    const translateTree = (scope) => {
        if (document.documentElement.lang !== 'en') return;
        const walker = document.createTreeWalker(scope, NodeFilter.SHOW_TEXT);
        const nodes = [];
        while (walker.nextNode()) nodes.push(walker.currentNode);
        nodes.forEach((node) => {
            const original = node.nodeValue || '';
            const trimmed = original.trim();
            const replacement = translate(trimmed);
            if (trimmed && replacement !== trimmed) node.nodeValue = original.replace(trimmed, replacement);
        });
        scope.querySelectorAll?.('[placeholder],[aria-label],[aria-description],[title]').forEach((element) => {
            for (const attribute of ['placeholder', 'aria-label', 'aria-description', 'title']) {
                const value = element.getAttribute(attribute);
                if (value) element.setAttribute(attribute, translate(value));
            }
        });
    };
    let language = 'ar';
    try { language = localStorage.getItem('athar-language') === 'en' ? 'en' : 'ar'; } catch (_) { /* Arabic remains the default. */ }
    const applyLanguage = () => {
        const button = document.querySelector('[data-language-toggle]');
        document.documentElement.lang = language;
        document.documentElement.dir = language === 'en' ? 'ltr' : 'rtl';
        if (button) {
            button.textContent = language === 'en' ? 'عربي' : 'EN';
            button.setAttribute('aria-label', language === 'en' ? 'التبديل إلى العربية' : 'Switch to English');
            button.title = button.getAttribute('aria-label');
        }
        if (language === 'en') translateTree(document.body);
        if (language === 'en') {
            const title = document.title.replace(/\s*\|\s*أثر$/, '');
            const translatedTitle = translate(title);
            if (translatedTitle !== title) document.title = `${translatedTitle} | Athar`;
        }
    };
    applyLanguage();
    document.querySelector('[data-language-toggle]')?.addEventListener('click', () => {
        language = language === 'en' ? 'ar' : 'en';
        try { localStorage.setItem('athar-language', language); } catch (_) { /* Language remains selected for this navigation. */ }
        window.location.reload();
    });
    if (language === 'en') new MutationObserver((records) => records.forEach((record) => {
        record.addedNodes.forEach((node) => { if (node.nodeType === Node.TEXT_NODE) { const value = node.nodeValue.trim(); const translated = translate(value); if (value && translated !== value) node.nodeValue = node.nodeValue.replace(value, translated); } else if (node.nodeType === Node.ELEMENT_NODE) translateTree(node); });
        if (record.type === 'characterData') { const value = record.target.nodeValue.trim(); const translated = translate(value); if (value && translated !== value) record.target.nodeValue = record.target.nodeValue.replace(value, translated); }
    })).observe(document.body, { childList: true, subtree: true, characterData: true });
})();
