package com.example.data.model

data class Category(
    val id: String,
    val name: String,
    val sector: SectorType,
    val description: String,
    val iconName: String,
    val isEmergencySupported: Boolean = false,
    val popularTags: List<String> = emptyList()
)

val APP_CATEGORIES = listOf(
    // 1. Ev & Tamirat
    Category(
        id = "boyaci",
        name = "Boyacı & Badana",
        sector = SectorType.HOME_REPAIR,
        description = "İç/dış cephe, tavan, su bazlı ve silikonlu boya",
        iconName = "brush",
        isEmergencySupported = false,
        popularTags = listOf("Marshall", "Filli Boya", "Dyo", "Jotun", "3+1 Ev", "Tavan Boyası")
    ),
    Category(
        id = "tesisatci",
        name = "Sıhhi Tesisatçı",
        sector = SectorType.HOME_REPAIR,
        description = "Su kaçağı tespiti, tıkanıklık açma, batarya montajı",
        iconName = "plumbing",
        isEmergencySupported = true,
        popularTags = listOf("Acil Kaçak", "Kırmadan Tespit", "Klozet Tamiri", "Kombi Girişi")
    ),
    Category(
        id = "elektrikci",
        name = "Elektrik & Aydınlatma",
        sector = SectorType.HOME_REPAIR,
        description = "Sigorta arızası, avize montajı, kablolama, priz tamiri",
        iconName = "bolt",
        isEmergencySupported = true,
        popularTags = listOf("Acil Arıza", "Avize", "LED Aydınlatma", "Şalter Değişimi")
    ),
    Category(
        id = "cilingir",
        name = "Çilingir & Kilit",
        sector = SectorType.HOME_REPAIR,
        description = "Kapıda kalma, kilit ve barel değişimi, çelik kapı açma",
        iconName = "key",
        isEmergencySupported = true,
        popularTags = listOf("15 Dk Kapıda", "Kilit Değişimi", "Hasarsız Açma", "Oto Çilingir")
    ),
    Category(
        id = "marangoz",
        name = "Marangoz & Mobilya",
        sector = SectorType.HOME_REPAIR,
        description = "Mutfak dolabı, ray dolap tamiri, kapı sürtmesi, ahşap işçiliği",
        iconName = "handyman",
        isEmergencySupported = false,
        popularTags = listOf("Menteşe Değişimi", "Ray Dolap", "Özel Ölçü Raf", "Lake Boya")
    ),
    Category(
        id = "klima_kombi",
        name = "Klima & Kombi",
        sector = SectorType.HOME_REPAIR,
        description = "Kombi periyodik bakım, petek temizliği, klima gaz dolumu",
        iconName = "ac_unit",
        isEmergencySupported = true,
        popularTags = listOf("Petek Temizleme", "Gaz Dolumu", "Acil Isınmama", "Yıllık Bakım")
    ),

    // 2. Temizlik & Bakım
    Category(
        id = "ev_temizligi",
        name = "Ev & Ofis Temizliği",
        sector = SectorType.CLEANING,
        description = "Boş ev, inşaat sonrası veya eşyalı daire detaylı dip köşe temizlik",
        iconName = "cleaning_services",
        isEmergencySupported = false,
        popularTags = listOf("İnşaat Sonrası", "Buharlı Temizlik", "Cam Silimi", "Gündelik Yardımcı")
    ),
    Category(
        id = "koltuk_yikama",
        name = "Koltuk & Yatak Yıkama",
        sector = SectorType.CLEANING,
        description = "Yerinde vakumlu antibakteriyel koltuk, yatak ve araç koltuğu yıkama",
        iconName = "chair",
        isEmergencySupported = false,
        popularTags = listOf("Vakumlu Yıkama", "Leke Çıkarma", "Antibakteriyel", "L Koltuk")
    ),
    Category(
        id = "ilaclama",
        name = "Haşere & Böcek İlaçlama",
        sector = SectorType.CLEANING,
        description = "Sağlık Bakanlığı onaylı kokusuz jel ve sıvı böcek, fare ilaçlama",
        iconName = "pest_control",
        isEmergencySupported = true,
        popularTags = listOf("Kokusuz İlaçlama", "Hamam Böceği", "Pire İlaçlama", "Ruhsatlı")
    ),

    // 3. Etkinlik & Eğlence
    Category(
        id = "palyaco",
        name = "Palyaço & Animatör",
        sector = SectorType.EVENT_ENTERTAINMENT,
        description = "Çocuk doğum günleri, oyunlar, danslar ve eğlenceler",
        iconName = "sentiment_very_satisfied",
        isEmergencySupported = false,
        popularTags = listOf("Yüz Boyama", "Sosis Balon", "Mini Disco", "Hazine Avı")
    ),
    Category(
        id = "maskot",
        name = "Maskot & Karakter",
        sector = SectorType.EVENT_ENTERTAINMENT,
        description = "Spiderman, Elsa, Dev Ayıcık, Pamuk Prenses ve süper kahramanlar",
        iconName = "theater_comedy",
        isEmergencySupported = false,
        popularTags = listOf("Spiderman", "Karlar Kraliçesi Elsa", "Dev Ayıcık", "Pamuk Prenses", "Batman")
    ),
    Category(
        id = "sihirbaz",
        name = "Sihirbaz & İllüzyon",
        sector = SectorType.EVENT_ENTERTAINMENT,
        description = "İnteraktif çocuk ve yetişkin illüzyon gösterileri",
        iconName = "auto_awesome",
        isEmergencySupported = false,
        popularTags = listOf("Tavşan Çıkarma", "İnteraktif", "Kart Numaraları", "Uçan Masa")
    ),
    Category(
        id = "dj_ses",
        name = "DJ & Ses Sistemi",
        sector = SectorType.EVENT_ENTERTAINMENT,
        description = "Parti müzikleri, kablosuz mikrofon ve hoparlör paketi",
        iconName = "music_note",
        isEmergencySupported = true,
        popularTags = listOf("Bluetooth Ses Bombası", "Kablosuz Mikrofon", "Işık Sistemi")
    ),
    Category(
        id = "fotografci",
        name = "Etkinlik Fotoğrafçısı",
        sector = SectorType.EVENT_ENTERTAINMENT,
        description = "Doğum günü, nişan, sünnet profesyonel dış ve iç mekan çekimi",
        iconName = "photo_camera",
        isEmergencySupported = false,
        popularTags = listOf("Tüm Kareler Teslim", "Drone Çekimi", "Klip Montajı", "Albüm")
    ),

    // 4. Nakliye & Montaj
    Category(
        id = "nakliyeci",
        name = "Evden Eve Nakliyat",
        sector = SectorType.MOVING_ASSEMBLY,
        description = "Şehir içi ve şehirler arası sigortalı, asansörlü ev taşıma",
        iconName = "local_shipping",
        isEmergencySupported = true,
        popularTags = listOf("Asansörlü", "Paketleme Dahil", "Sigortalı", "Parça Eşya")
    ),
    Category(
        id = "mobilya_montaj",
        name = "Mobilya Montajı (IKEA vb.)",
        sector = SectorType.MOVING_ASSEMBLY,
        description = "Gardırop, baza, TV ünitesi söküm ve montaj ustası",
        iconName = "build",
        isEmergencySupported = true,
        popularTags = listOf("IKEA Montajı", "Sürgülü Dolap", "Duvara Sabitleme", "Hızlı Servis")
    ),

    // 5. Özel Ders & Danışmanlık
    Category(
        id = "matematik_ders",
        name = "Matematik Özel Ders",
        sector = SectorType.TUTORING_CONSULTING,
        description = "LGS, YKS, TYT ve okul derslerine takviye birebir özel ders",
        iconName = "calculate",
        isEmergencySupported = false,
        popularTags = listOf("LGS Hazırlık", "Birebir", "Evde Ders", "Yeni Nesil Sorular")
    ),
    Category(
        id = "muzik_ders",
        name = "Gitar & Piyano Dersi",
        sector = SectorType.TUTORING_CONSULTING,
        description = "Başlangıç ve ileri seviye enstrüman, şan ve nota eğitimi",
        iconName = "queue_music",
        isEmergencySupported = false,
        popularTags = listOf("Akustik Gitar", "Piyano", "Kulak Eğitimi", "Her Yaş")
    ),

    // 6. Kişisel Bakım & Sağlık
    Category(
        id = "evde_kuafor",
        name = "Evde Kuaför & Bakım",
        sector = SectorType.PERSONAL_CARE,
        description = "Gelin başı, manikür, pedikür, saç kesimi ve boya adrese servis",
        iconName = "content_cut",
        isEmergencySupported = false,
        popularTags = listOf("Gelin Saçı", "Protez Tırnak", "Fön", "Evde Hizmet")
    ),
    Category(
        id = "pet_kuafor",
        name = "Mobil Pet Kuaför",
        sector = SectorType.PERSONAL_CARE,
        description = "Kedi ve köpekler için anestezi olmadan makas/makine traşı ve banyo",
        iconName = "pets",
        isEmergencySupported = false,
        popularTags = listOf("Anestezisiz", "Mobil Araç", "Tırnak Kesimi", "Kedi Traşı")
    )
)
