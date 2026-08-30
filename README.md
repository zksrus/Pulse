# Pulse

Android-приложение для получения количества шагов с фитнес-браслета Huawei.

## Что делает

- Подключается к **Huawei Health Kit** (Health Service Kit) для чтения данных о шагах
- Показывает количество шагов за сегодня с анимированным кольцом прогресса
- Отображает статистику: калории, расстояние, время активности
- Недельный график активности
- Выбор дневной цели (5K / 8K / 10K / 15K / 20K)
- Автоматический demo-режим если Huawei Health Kit недоступен

## Технологии

- **Kotlin**, **Jetpack Compose**, **Material 3**
- **Huawei Health Kit SDK** (`com.huawei.hms:health:13.0.11.301`)
- **Huawei Account Kit** (`com.huawei.hms:hwid:6.11.0.300`)
- minSdk 26, targetSdk 34

## Настройка Huawei Health Kit

1. Зарегистрируйтесь на [Huawei Developer Console](https://developer.huawei.com)
2. Создайте приложение и получите **App ID**
3. В консоли включите **Health Kit** в каталоге HMS
4. Настройте разрешения: `HEALTHKIT_STEP_BOTH`
5. В `app/src/main/AndroidManifest.xml` замените `YOUR_APP_ID` на ваш App ID
6. Добавьте `agconnect-services.json` в `app/` (скачайте из AppGallery Connect)

## Сборка

```sh
./gradlew assembleDebug
```

## Структура проекта

```
app/src/main/java/com/freebuff/pulse/
├── MainActivity.kt              # Точка входа
├── PulseApplication.kt          # Application-класс для HMS Core
├── HuaweiHealthManager.kt       # Обёртка для Health Kit API
├── viewmodel/
│   └── StepViewModel.kt         # Управление состоянием UI
└── ui/
    ├── theme/
    │   ├── Color.kt             # Цветовая палитра
    │   ├── Type.kt              # Типографика
    │   └── Theme.kt             # Тёмная тема Material 3
    ├── components/
    │   └── StepRing.kt          # Анимированное кольцо прогресса
    └── screens/
        └── StepCounterScreen.kt # Главный экран
```

## Как это работает

Приложение использует **Huawei Health Kit** (Health Service Kit) — расширенный API для чтения данных здоровья из приложения Huawei Health. При первом запуске происходит авторизация через Huawei ID с запросом разрешений на чтение данных о шагах (`HEALTHKIT_STEP_BOTH`). 

### Интеграция с Huawei Health Kit

- **HealthKitClient** — основной клиент для доступа к данным здоровья
- **DataType.DT_CONTINUOUS_STEPS_TOTAL** — тип данных для агрегированных шагов за день
- **Automation.ReadOption** — запрос на чтение данных с фильтром по времени
- **HuaweiIdAuthManager** — авторизация через Huawei ID с расширенными разрешениями

Если Huawei Health Kit недоступен на устройстве (нет HMS Core или нет Huawei Health), приложение автоматически переключается в demo-режим с тестовыми данными.

## API

- `HuaweiHealthManager.isAuthorized()` — проверка авторизации
- `HuaweiHealthManager.signIn()` — авторизация через Huawei ID
- `HuaweiHealthManager.getTodaySteps()` — шаги за сегодня
- `HuaweiHealthManager.getWeeklySteps()` — данные за неделю для графика
- `HuaweiHealthManager.signOut()` — выход из аккаунта
