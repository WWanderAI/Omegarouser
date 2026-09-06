# Omegarouser

Простой Android-браузер в стиле Google: домашняя страница, адресная строка
с поиском через Google, кнопки «назад / вперёд / обновить / домой»,
pull-to-refresh.

## Технологии
- Kotlin
- Android WebView
- Material Components

## Как собрать APK

1. Установи [Android Studio](https://developer.android.com/studio).
2. Открой этот проект (`File → Open` → выбери папку `Omegarouser`).
3. Дождись синхронизации Gradle (Android Studio сама скачает нужные версии
   Gradle/SDK при первом открытии).
4. Собери APK: `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
5. Готовый файл появится в `app/build/outputs/apk/debug/app-debug.apk`.

Либо через терминал в корне проекта:

```bash
./gradlew assembleDebug
```

## Структура проекта

```
app/
  src/main/
    java/com/example/googlebrowser/MainActivity.kt   — логика браузера
    res/layout/activity_main.xml                      — интерфейс
    res/values/                                        — строки, цвета, тема
    AndroidManifest.xml                                — разрешения, точка входа
```

## Что умеет
- Открывает Google как домашнюю страницу
- Вводишь текст → ищет в Google; вводишь адрес → открывает сайт
- Кнопки навигации: назад, вперёд, обновить, домой
- Индикатор загрузки страницы
- Свайп вниз для обновления страницы
