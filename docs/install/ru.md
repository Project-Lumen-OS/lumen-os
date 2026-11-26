# Установка Lumen OS

[English version](en.md)

Lumen OS это Android TV 14 для проекторов XGIMI. Найдите свой проектор ниже и откройте его страницу. На каждой
странице все шаги, сервисы Google, обновления, что делать, если что-то пошло не так, и возврат к системе XGIMI.

<!-- models:install:begin -->
| Проектор | Код модели | Что можно | Инструкция |
|---|---|---|---|
| XGIMI Z9X | G0082 | Lumen OS Live, Установить Lumen OS насовсем | [инструкция](z9x/ru.md) |
| XGIMI Z9X HB | G0130, G0148 | Lumen OS Live, Установить Lumen OS насовсем | [инструкция](hb/ru.md) |
| XGIMI X50 Ultra | G0111 | Lumen OS Live, Установить Lumen OS насовсем, бета | [инструкция](x50/ru.md) |
| XGIMI X50 Ultra Max | G0100 | Lumen OS Live, Установить Lumen OS насовсем, бета | [инструкция](x50max/ru.md) |
| XGIMI Play 6 | G0058 | Установка с компьютера | [инструкция](play6/ru.md) |
| XGIMI AURA 3 Max | G0104 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI MIRA 4K | G0109 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI MIRA 4K Pro | G0110 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI RS 20 | G0087 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS 20 Plus | G0063 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS 20 Plus Highlight | G0079 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS 20 Plus New | G0086 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS 20 Pro | G0088 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS 20 Pro Master | G0077 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS 20 Pro Max Highlight | G0089 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS 20 Ultra | G0067 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS 20 Ultra Master | G0078 | только Lumen OS Live, бета | [инструкция](z9x/ru.md) |
| XGIMI RS30 Pro | G0125 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI RS30 Pro Max | G0124 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI RS30 Ultra | G0114 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI RS30 Ultra Highlight | G0170 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI RS30 Ultra Max | G0115 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI RS30 Ultra Max Flagship | G0155 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI TITAN Noir | H0061 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI TITAN Noir Max | H0063 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
| XGIMI TITAN Noir Pro | H0062 | только Lumen OS Live, бета | [инструкция](x50/ru.md) |
<!-- models:end -->

Код модели и прошивку XGIMI вашего проектора Lumen OS Live показывает вверху своего экрана, а справа в карточке
«Версии прошивки» версии всех частей прошивки, которые называет проектор.

<a name="easy"></a>

## С проектора

На всех проекторах из списка, кроме Play 6, Lumen OS ставится одним способом: приложением Lumen OS Live на
самом проекторе. Оно скачивает Lumen OS через интернет проектора, по Wi-Fi или Ethernet. Компьютер и кабель
USB не нужны. Скачайте из [последнего выпуска Lumen OS Live](https://github.com/Project-Lumen-OS/lumen-installer/releases/latest)
файл Lumen OS Live с окончанием `.apk1` и скопируйте его на флешку. Флешка нужна только для приложения.
Сначала приложение Lumen OS Live запускает Lumen OS один раз как Lumen OS Live. Система XGIMI и ваши файлы остаются как есть.
Чтобы оставить Lumen OS, её ставят прямо из Lumen OS Live. Это один раз стирает всё на проекторе. Шаги
есть на странице вашего проектора.

<a name="advanced"></a><a name="flash"></a><a name="verify"></a><a name="play6"></a><a name="play6-need"></a><a name="play6-prepare"></a><a name="play6-files"></a><a name="play6-check"></a><a name="play6-install"></a><a name="play6-wifi"></a><a name="play6-verify"></a><a name="play6-google"></a><a name="play6-problems"></a><a name="play6-rescue"></a><a name="play6-stock"></a><a name="play6-changes"></a>

## Play 6

Только Play 6 ставится с компьютера, см. [страницу Play 6](play6/ru.md). Все остальные проекторы
ставятся только с самого проектора, как описано выше.

<a name="new-model"></a>

## Вашего проектора или прошивки нет в списке

Lumen OS Live скажет об этом. Если модель известна и нет в списке только прошивки XGIMI, можно поставить
галочку **Запустить Lumen OS Live всё равно**. Тогда работает только Lumen OS Live, установить нельзя.

Ещё вы можете помочь нам сделать Lumen OS для своего проектора. Кнопка работает только на системе XGIMI и ничего
на проекторе не меняет.

- **Отправить сведения** отправляет нам сведения о системе проектора, около 100 МБ: модель, версию прошивки,
  приложение настроек XGIMI, список приложений, части памяти, журналы и настройки системы. Пароли, сети Wi-Fi,
  аккаунты, ключи и серийный номер не отправляются. Если отправка прервётся, нажмите кнопку ещё раз, и она
  продолжится с того же места. Вы получите код. Назовите его, когда будете нам писать.

Если нам понадобится вся прошивка вашего проектора, мы расскажем, как её скопировать.

<a name="google"></a><a name="chromecast"></a>

## Сервисы Google

Lumen OS ставится без Google. При установке с проектора выберите **Да** в строке «Сервисы Google». На Play 6
установщик добавляет их из вашего скачанного файла, см. [«Сервисы Google»](play6/ru.md#play6-google).
Chromecast built-in не работает ни с Google, ни без него: Google даёт его только моделям, которые сам
сертифицировал. AirPlay и DLNA работают.

<a name="updates"></a>

## Обновления

Lumen OS обновляется сама через интернет: Настройки > Настройки устройства > Об устройстве > Обновление Lumen
OS. Приложения, настройки и файлы сохраняются. Play 6 пока не обновляется по Wi-Fi, см.
[«Обновления»](play6/ru.md#updates) на его странице.

<a name="problems"></a><a name="rescue"></a><a name="vbmeta"></a>

## Если что-то пошло не так

- В Lumen OS Live перезапустите проектор. Он вернётся к системе XGIMI.
- Что делать в каждом случае, написано на странице вашего проектора: [Z9X](z9x/ru.md#problems),
  [Z9X HB](hb/ru.md#problems), [X50, RS30 и TITAN](x50/ru.md#problems), [Play 6](play6/ru.md#play6-problems).
- Спрашивайте в Telegram-канале [@projector_lumenos](https://t.me/projector_lumenos) или в
  [issues на GitHub](https://github.com/Project-Lumen-OS/lumen-os/issues). Напишите модель, прошивку XGIMI,
  версию Lumen OS и что вы делали. Что нам прислать, Lumen OS Live показывает в «О программе», «Подробности
  для помощи».

<a name="stock"></a>

## Возврат к системе XGIMI

- В Lumen OS Live перезапустите проектор.
- После установки систему XGIMI возвращает флешка восстановления XGIMI с официальным файлом XGIMI для вашей
  модели. Она стирает всё на проекторе. Имя файла для флешки Lumen OS показывает в Настройки > Настройки
  устройства > Об устройстве > Возврат к системе XGIMI.
- Файлы и шаги: [Z9X](z9x/ru.md#stock), [Z9X HB](hb/ru.md#stock),
  [X50 Ultra и X50 Ultra Max](x50/ru.md#stock), [Play 6](play6/ru.md#play6-stock).

Lumen OS делается независимо. XGIMI, Google и LineageOS к ней отношения не имеют. Вы ставите её на свой страх и
риск.
