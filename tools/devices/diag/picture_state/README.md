# picture_state: read-only picture state dump of the Z9X

One text file with the picture and light state of a Z9X (G0082): what the vendor HAL answers to 85 getters
(picture mode, brightness, contrast, colour temperature, light level and mode, HDR, colour gamut, AI picture,
ambient adaptation, Eco, Turbo and so on), the picture related system properties, the colour lines of
SurfaceFlinger, the picture related settings and the checksums of the vendor picture tables. The same tool runs on
the stock system and on Lumen OS, so two files can be compared line by line.

It only reads. It sends no setter, presses no key, changes no setting, needs no root and installs nothing. The
only thing it puts on the projector is one small file in `/data/local/tmp`, and it removes it at the end.
Three getters are listed but never sent, because the project rules forbid them on this projector
(`isPlayingHDRVideo`, `getAmbientLight`, `getCurHwCurrent`), and one more because its argument is unknown
(`getLumensAdjState`). They are printed as `SKIPPED`.

Files: `PictureState.java` (the tool), `build.sh` (makes `picture_state.jar`), `picture_state.sh` (runs it over
adb), `picture_state_diff.sh` (compares two dumps), test `tools/devices/test/picture_state_test.sh`.

## For the tester: a stock Z9X, no root

You need a computer with `adb`, the projector on the same network, and the two files `picture_state.jar` and
`picture_state.sh` in one folder.

1. On the projector turn on USB debugging: Settings, About, press OK on the build number seven times, then
   Developer options, USB debugging (and network debugging if the menu has it).
2. Put the projector in the state we compare: the home screen, no film playing, picture mode Standard,
   brightness as it was out of the box. Write down what is on the screen (home screen, HDMI, a film).
3. On the computer:

   ```
   adb connect PROJECTOR_IP:5555
   sh picture_state.sh -s PROJECTOR_IP:5555 stock.txt
   ```

   Accept the debugging question on the projector if it appears. The run takes about ten seconds.
4. Send back `stock.txt`. If you can, repeat while an HDR film plays and send that file as `stock_hdr.txt`.

Without `sh` (Windows) the same by hand:

```
adb -s PROJECTOR_IP:5555 push picture_state.jar /data/local/tmp/picture_state.jar
adb -s PROJECTOR_IP:5555 shell "CLASSPATH=/data/local/tmp/picture_state.jar app_process /system/bin PictureState" > stock.txt
adb -s PROJECTOR_IP:5555 shell rm /data/local/tmp/picture_state.jar
```

The file has no serial number, no MAC address, no account and no network name.

## On Lumen OS

The same command, as the plain shell user (run `adb unroot` first if adb was switched to root):

```
sh tools/devices/diag/picture_state/build.sh
sh tools/devices/diag/picture_state/picture_state.sh -s <lan-ip>:5555 lumen.txt
sh tools/devices/diag/picture_state/picture_state_diff.sh stock.txt lumen.txt
```

The diff prints each stable section as `same` or `DIFFERENT` with the lines of the first file after `<` and of
the second after `>`. The sections `[meta]`, `[hal-volatile]` and `[context]` are shown but not compared.
A row that ends with `ERROR ...` means the HAL refused that getter for the shell user: it is a result, not a
reason to try another way.

---

# picture_state: снимок состояния картинки Z9X, только чтение

Один текстовый файл с состоянием картинки и света Z9X (G0082): ответы HAL на 85 запросов чтения (режим
изображения, яркость, контраст, цветовая температура, уровень и режим света, HDR, цветовой охват, AI картинка,
адаптация к освещению, Eco, Turbo и другие), системные свойства про картинку, строки цвета из SurfaceFlinger,
настройки про картинку и контрольные суммы заводских таблиц картинки. Один и тот же инструмент работает на
стоковой прошивке и на Lumen OS, поэтому два файла можно сравнить построчно.

Инструмент только читает. Он не отправляет ни одной команды записи, не нажимает кнопки, не меняет настройки, не
требует root и ничего не устанавливает. На проектор кладётся один маленький файл в `/data/local/tmp`, в конце он
удаляется. Три запроса в списке есть, но не отправляются, потому что правила проекта запрещают их на этом
проекторе (`isPlayingHDRVideo`, `getAmbientLight`, `getCurHwCurrent`), и ещё один, потому что неизвестен его
аргумент (`getLumensAdjState`). В файле они помечены `SKIPPED`.

## Тестировщику: стоковый Z9X, без root

Нужен компьютер с `adb`, проектор в той же сети и два файла в одной папке: `picture_state.jar` и
`picture_state.sh`.

1. На проекторе включите отладку по USB: Настройки, Об устройстве, семь раз нажмите OK на номере сборки, затем
   Для разработчиков, Отладка по USB (и отладка по сети, если такой пункт есть).
2. Приведите проектор в состояние, которое сравниваем: главный экран, фильм не идёт, режим изображения
   Стандартный, яркость как из коробки. Запишите, что на экране (главный экран, HDMI, фильм).
3. На компьютере:

   ```
   adb connect IP_ПРОЕКТОРА:5555
   sh picture_state.sh -s IP_ПРОЕКТОРА:5555 stock.txt
   ```

   Если проектор спросит про отладку, разрешите. Работа занимает около десяти секунд.
4. Пришлите файл `stock.txt`. Если можете, повторите во время HDR фильма и пришлите второй файл `stock_hdr.txt`.

Без `sh` (Windows) то же самое вручную:

```
adb -s IP_ПРОЕКТОРА:5555 push picture_state.jar /data/local/tmp/picture_state.jar
adb -s IP_ПРОЕКТОРА:5555 shell "CLASSPATH=/data/local/tmp/picture_state.jar app_process /system/bin PictureState" > stock.txt
adb -s IP_ПРОЕКТОРА:5555 shell rm /data/local/tmp/picture_state.jar
```

В файле нет серийного номера, MAC адреса, аккаунта и имени сети.

## На Lumen OS

Та же команда от обычного пользователя shell (если adb переключали в root, сначала `adb unroot`):

```
sh tools/devices/diag/picture_state/build.sh
sh tools/devices/diag/picture_state/picture_state.sh -s <lan-ip>:5555 lumen.txt
sh tools/devices/diag/picture_state/picture_state_diff.sh stock.txt lumen.txt
```

Сравнение печатает каждый стабильный раздел как `same` или `DIFFERENT`, строки первого файла после `<`, второго
после `>`. Разделы `[meta]`, `[hal-volatile]` и `[context]` показаны, но не сравниваются. Строка, которая
кончается на `ERROR ...`, значит, что HAL отказал пользователю shell в этом запросе: это результат, а не повод
пробовать другим способом.
