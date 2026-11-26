# План проверки трансляции экрана по моделям

Для Славы (Z9X HB, X50 Ultra, X50 Ultra Max) и владельца (Z9X). Сначала часть «только чтение», она ничего не меняет на
проекторе. Потом живая проверка с телефоном и компьютером. Результат по каждой модели записать в таблицу в конце.

## 1. Только чтение (adb, ничего не меняет)

```
adb shell getprop ro.boot.xgimi.modelname
adb shell pm list features | grep wifi.direct
adb shell pm list packages | grep -E 'org.lumen.airplay|org.lumen.miracast|com.xgimiru.player'
adb shell dumpsys package org.lumen.airplay | grep -E 'versionName|versionCode'
adb shell dumpsys package org.lumen.miracast | grep -E 'versionName|stopped='
adb shell cmd overlay list | grep -i p2p
adb shell ip link show p2p0
adb shell 'ls /system/etc/sysconfig/ | grep -E "z9x|lumen-cast"'
adb shell dumpsys media.player | grep -i -E 'c2.mtk|OMX.MTK' | head
adb logcat -d -s LumenCast Z9xAirPlay LumenMiracast | tail -50
```

Ожидаем: `android.hardware.wifi.direct` есть, все три пакета стоят, AirPlay 1.0.11, у org.lumen.miracast
`stopped=false`, оверлей Wi-Fi Direct включён, интерфейс p2p0 есть.

## 2. Живая проверка (на каждой модели)

1. Главный экран: есть плитка «Трансляция экрана», она открывает экран трансляции.
2. Меню проектора, «Все настройки», строка «Трансляция экрана» открывает тот же экран. Плитка «Повтор экрана» в
   быстром меню есть и открывает окошко приёмника.
3. Экран трансляции: имя проектора, три карточки (AirPlay, Android и Windows, Видео из приложений), у каждой состояние
   «Готово». Переименовать проектор: новое имя видно и в AirPlay на iPhone, и в списке Miracast на Android.
4. AirPlay, iPhone с iOS 17, 18 и 26 (какие есть): повтор экрана 2 минуты, видео из YouTube и Safari по кнопке AirPlay,
   музыка. Режим «Код на экране»: код появился, подключение по коду работает. Mac: повтор экрана.
5. Miracast: Android (Smart View или Трансляция) и Windows (Win+K), 2 минуты повтора, отключение с телефона, повторное
   подключение. Выключить «Повтор экрана» на экране трансляции: проектор пропал из списка на телефоне.
6. DLNA: из приложения с кнопкой «Воспроизвести на» выбрать проектор, видео открылось в Lumen Play. На экране трансляции карточка DLNA «Готово» с именем «Lumen Play на ...». В Lumen Play выключить DLNA (Настройки, Трансляция): через 3 секунды карточка «Выкл», включить обратно: «Готово».
7. Задержка: секундомер на телефоне с сотыми, снять телефон и стену одним кадром, 10 замеров, взять средний.
   Повтор экрана 1080p, потом 4K там, где есть такой пункт (на Play 6 его нет).
8. После перезагрузки проектора всё из пунктов 1, 3 и 5 работает без открытия приложений.

## 3. Особое по моделям

| Модель | Что смотреть особо |
|---|---|
| Z9X (владелец) | Обновление поверх 1.0.10: настройки AirPlay (имя, код) сохранились. |
| Z9X HB (Слава) | Miracast на HB впервые: пункт 5 целиком. На G0148 Wi-Fi AIC8800, проверить p2p0 отдельно. |
| X50 Ultra, Ultra Max (Слава) | AirPlay был старый 1.0.9: пункт 4 целиком, 4K повтор. Miracast впервые: пункт 5. |
| TITAN | AirPlay и Miracast впервые, на стоке трансляции не было. Пункты 4 и 5 целиком. |
| RS30, Play 6 | AirPlay теперь собирается из дерева, а не из пина: пункт 4 коротко. |

## 4. Отчёт

| Модель | Только чтение | Плитка и строка | AirPlay iOS | Код | Miracast Android | Miracast Windows | DLNA | Задержка 1080p / 4K | После перезагрузки |
|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | |
