# WexBooks

Android-читалка на Kotlin и Jetpack Compose: спокойная книжная типографика и iOS-inspired Liquid Glass на элементах управления.

## Уже работает

- Импорт EPUB, TXT, текстовых FB2/FB2.zip с телефона; локальное чтение без аккаунта.
- Библиотека Room с поиском, метаданными, прогрессом, удалением управляемой копии и защитой от повторного импорта.
- Свой ReaderCore со сменными рендерерами: Readium для EPUB, собственная нативная вёрстка для TXT/текстового FB2.
- Постраничная навигация, оглавление, размер шрифта, закладки и восстановление позиции. EPUB также поддерживает вертикальный режим.
- Оформление в DataStore: светлая/тёмная тема, уменьшение прозрачности и движения.
- Настоящий Backdrop 2.0.1 в библиотеке; на странице под панелями используется лёгкий материал бумаги без захвата всей книги.
- Локальная release-подпись. Ключи и пароли вне Git.

Нативный FB2 сейчас текстовый: встроенные иллюстрации, интерактивные сноски и произвольное выделение текста идут следующим этапом. PDF/TTS/синхронизация описаны в плане, а не объявлены готовыми.

## Документы

- [Продуктовый план](docs/PRODUCT_PLAN.md)
- [Архитектура ReaderCore](docs/READER_CORE.md)
- [Дизайн-система](docs/DESIGN_SYSTEM.md)
- [Анимации](docs/MOTION.md)
- [Производительность на Pixel](docs/PERFORMANCE.md)
- [Проверки](docs/VERIFICATION.md)

## Сборка

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:assembleRelease :app:bundleRelease
```

Release использует локальные `keystore.properties` и `keystore/wexbooks-release.jks`. Тестовая сборка называется **WexBooks Dev** и имеет отдельный applicationId `.dev`.

```bash
ANDROID_SERIAL=47101FDAS0028Q ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=47101FDAS0028Q ./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Тестируем на Pixel 9 Pro XL через USB ADB; эмуляторы не запускаем. История исходников — в Git, без дублирующих папок проекта. Удалённый репозиторий не подключён.
