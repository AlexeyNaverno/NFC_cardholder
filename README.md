# NFC Cardholder

[![Build](https://github.com/AlexeyNaverno/NFC_cardholder/actions/workflows/build.yml/badge.svg)](https://github.com/AlexeyNaverno/NFC_cardholder/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-GPL--3.0--only-blue.svg)](LICENSE)

Десять слотов для NFC-карт на одном экране. У каждого слота три кнопки:
**скопировать** карту, **передать** её обратно (телефон отвечает считывателю как эта карта)
и **удалить**. Всё, без настроек и лишних экранов.

Ten slots for NFC cards on one screen, each with three buttons: copy a card, transmit it back and
delete it. [English summary below](#in-english).

---

## Что умеет

* **Скопировать** — поднесите карту или метку к задней панели телефона. Приложение сохраняет всё,
  что карта отдаёт: UID, список технологий, ATQA/SAK, historical bytes, сообщение NDEF (и его
  текст), все приложения ISO-DEP, ответившие на SELECT, и память MIFARE, открытую известным ключом
  (секторы Classic, страницы Ultralight).
* **Передать** — телефон начинает отвечать NFC-считывателю как эта карта (эмуляция карт, HCE).
  Одновременно передаётся один слот; повторное нажатие останавливает передачу.
* **Удалить** — очищает слот вместе со всеми данными карты.
* Нажатие на строку слота открывает его свойства: данные карты, переименование, ручное добавление
  AID (если считыватель выбирает приложение, которое телефон не смог найти сам).

## Что телефон может передать

Android умеет эмулировать только **ISO-DEP (ISO 14443-4)** — карты, с которыми считыватель общается
APDU-командами. Поэтому:

* карта с приложениями ISO-DEP передаётся так: на SELECT по AID телефон отвечает сохранённым FCI,
  записанные пары команд/ответов воспроизводятся дословно, остальные команды получают стандартные
  статус-слова;
* карта с NDEF (в том числе NTAG и Ultralight, у которых ISO-DEP нет) передаётся как метка
  **NFC Forum Type 4**: телефон сам строит capability container и файл NDEF и отдаёт их по
  стандартному протоколу;
* **MIFARE Classic** (шифр Crypto1 на уровне ISO 14443-3), **Ultralight без NDEF**, NFC-F и NFC-V
  передать нельзя: NFC-контроллер не открывает эти режимы приложениям. Их данные копируются и
  доступны для просмотра, а приложение честно говорит, что передача невозможна;
* в режиме эмуляции телефон всегда предъявляет **собственный UID** — Android не позволяет его
  менять, поэтому считыватели, проверяющие серийный номер, карту отклонят;
* **банковские карты не поддерживаются**: их ключи живут в защищённом элементе, а каждая операция
  использует одноразовую криптограмму, поэтому повтор сохранённых ответов не сработает.

Копируйте и передавайте только карты, которыми владеете или на использование которых есть
разрешение.

## Установка

* **GitHub Releases** — пре-релиз `0.0.1` со собранным APK: вкладка
  [Releases](https://github.com/AlexeyNaverno/NFC_cardholder/releases). Скачайте
  `NFC-Cardholder-0.0.1.apk` и установите на устройство с NFC (Android 5.0 и новее).
* **F-Droid** — приложение собирается из исходников; метаданные для подачи лежат в
  [`fdroid/io.github.alexnaverno.nfccardholder.yml`](fdroid/io.github.alexnaverno.nfccardholder.yml)
  и в каталоге `fastlane/metadata/android/`.

## Сборка из исходников

Нужны JDK 17 и Android SDK (compileSdk 34); всё остальное подтянет Gradle.

```bash
git clone https://github.com/AlexeyNaverno/NFC_cardholder.git
cd NFC_cardholder
./gradlew assembleDebug          # отладочный APK
./gradlew testDebugUnitTest      # модульные тесты логики APDU и эмуляции
```

APK появится в `app/build/outputs/apk/debug/`. Для релизной сборки без подписи:
`./gradlew assembleRelease`. Чтобы подписать релиз локально, создайте `keystore.properties` в корне
репозитория (файл игнорируется git):

```properties
storeFile=/путь/к/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Релизы через GitHub

Версия живёт в одном месте — `app/build.gradle.kts` (`versionCode` и `versionName`), а изменения
описаны в [`CHANGELOG.md`](CHANGELOG.md).

1. Поднимите `versionName`/`versionCode` и опишите изменения в `CHANGELOG.md`.
2. Создайте тег `v<версия>` на этом коммите и отправьте его: `git tag v0.1.0 && git push origin v0.1.0`.
3. Workflow [`release.yml`](.github/workflows/release.yml) соберёт подписанный APK, посчитает
   SHA-256 и опубликует релиз. Версии `0.x` публикуются как **пре-релизы**, стабильные — как
   обычные релизы.
4. Подпись: если в секретах репозитория заданы `RELEASE_KEYSTORE_BASE64`,
   `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, релиз подписывается
   ключом проекта (обновления ставятся поверх). Если секретов нет, CI генерирует одноразовый ключ
   и прямо пишет об этом в тексте релиза.

Workflow [`build.yml`](.github/workflows/build.yml) на каждый push собирает отладочный APK,
прогоняет тесты и lint.

## Соответствие требованиям F-Droid

* лицензия **GPL-3.0-only**, весь код и ресурсы — собственные или свободные (иконки нарисованы
  скриптом [`tools/make_icons.py`](tools/make_icons.py), шрифтовые глифы Material Icons — Apache-2.0);
* зависимости — только AndroidX и Material Components из `google()`/`mavenCentral()`, без
  проприетарных сервисов, без Google Play services;
* нет рекламы, аналитики, трекинга и сетевых разрешений — приложению не нужен интернет;
* данные карт не резервируются в облако (`android:allowBackup="false"`), см. [`PRIVACY.md`](PRIVACY.md);
* сборка воспроизводима из исходников: фиксированные версии в `gradle/libs.versions.toml`,
  Gradle Wrapper в репозитории, версия читается из `app/build.gradle.kts`, каждому релизу
  соответствует тег;
* метаданные в формате fastlane (`fastlane/metadata/android/{en-US,ru-RU}/`) и готовый файл
  описания для fdroiddata лежат в репозитории;
* минимальный API 21, целевой API 34.

## Структура проекта

```
app/src/main/java/io/github/alexnaverno/nfccardholder/
├── MainActivity.kt            один экран: список слотов, диалоги, режим чтения
├── ui/SlotAdapter.kt          строка слота и три кнопки
├── core/                      Hex и разбор APDU (чистый Kotlin, покрыт тестами)
├── model/                     CardRecord, хранилище cards.json
├── nfc/                       копирование: NDEF, ISO-DEP, MIFARE; словарь ключей
└── hce/                       HostApduService, движок ответов, Type 4 Tag, регистрация AID
```

## Дорожная карта

* запись скопированных данных на пустую метку («болванку»);
* экспорт и импорт слотов в файл, резервные копии внутри устройства;
* журнал APDU-обмена в свойствах слота для отладки считывателей;
* редактирование пар команд/ответов как простого скрипта.

## Лицензия

GNU General Public License version 3, only this version. Подробности — в [`LICENSE`](LICENSE).

---

## In English

NFC Cardholder keeps ten card slots on a single screen. Each slot copies a tag (UID, technologies,
ATQA/SAK, NDEF, every ISO-DEP application that answers SELECT, MIFARE memory opened by a known
key), transmits the copied card back to a reader through host card emulation, or deletes it.

Transmitting works for ISO-DEP applications (recorded SELECT answers and APDU pairs are replayed)
and for NDEF data, which is served as an NFC Forum Type 4 Tag. MIFARE Classic, Ultralight and other
non ISO-DEP tags cannot be transmitted by Android; their data is copied for inspection only. The
phone always presents its own UID and payment cards are not supported.

The app is free software (GPL-3.0-only), has no network permission, no tracking and no ads.
Releases are published on GitHub: `0.x` versions as pre-releases, built and signed by GitHub
Actions. The project is prepared for F-Droid inclusion.
