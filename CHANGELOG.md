# Changelog

## 1.6.6

- Der Test-Button öffnet den großen Wecker direkt, solange Morgo vorn ist. Sonst bleibt es beim Vollbild-Hinweis. Fehlt die Freigabe ab Android 14, führen die Einstellungen dorthin. Ein entsperrtes Handy in Benutzung zeigt, wie die Google Uhr, nur die Benachrichtigung oben.
- Die Uhr bekommt eine eigene Wecker-Meldung mit Schlummern und Stopp. Die Vordergrund-Meldung bleibt auf dem Telefon. Die Uhr hält Benachrichtigungen zurück, solange das Handy aktiv benutzt wird.
- Licht vor dem Wecken testet alle eingestellten Stufen für die gewählten Einträge, in derselben Reihenfolge wie beim Wecken. Der Abstand ist kürzer. Der Fortschritt zeigt den Schritt, Fehler stehen darunter, und der Lauf lässt sich abbrechen.

## 1.6.5

- Updates öffnen nur noch die GitHub-Release-Seite. Morgo lädt keine APK mehr und fordert die Installationsfreigabe nicht mehr an.
- Die Kamera für den WebUntis-QR-Code bleibt eine optionale Gerätefunktion. Der Scanner ist nicht exportiert.

## 1.6.4

- Die Einstellungen öffnen wieder. Der Stundenplan für die Extra-Wecker wurde gelesen, bevor sein Zustand angelegt war, und das hat die App sofort beendet. Fehlende neue Werte (Zufallswiedergabe, Sonara-Playlist, Licht beim Losgehen) bleiben bei den bisherigen Vorgaben. Ohne gespeicherten Stundenplan steht „Keine Stunden gefunden“.
- Ist Sonara nicht installiert oder antwortet nicht, bleibt die Playlist bei den Lieblingssongs. Die Verbindung kann die Einstellungen nicht mehr beenden.
- Nach einem Absturz zeigt der nächste Start den letzten Bericht. Er lässt sich kopieren oder teilen.

## 1.6.3

- Sonara 1.0.119 startet die gewählte Playlist gemischt. Die Liste kommt aus Sonara, sonst sind es die Lieblingssongs. Meldet Sonara einen Fehler, steht der Text in der Benachrichtigung und der Klingelton übernimmt.

## 1.6.2

- Der Radio-Stream im Musik-Wecker hält das WLAN wach und wartet die eingestellte Fallback-Zeit, auch wenn das Netz nach dem Wecken erst hochfährt. Eine stille Lautstärkerampe gilt nicht als Ausfall. Scheitert der Stream, stehen Fehlercode und Grund in der Benachrichtigung und beim Testen.
- Extra-Wecker zeigen, an welchen Tagen das Fach im gespeicherten Stundenplan vorkommt: Wochentage, der nächste Termin und die Stunden der kommenden zwei Wochen. Fehlt das Fach, steht dort „Keine Stunden gefunden“.
- Der Musik-Wecker bittet Spotify oder eine andere App beim Start um zufällige Wiedergabe. Der Schalter ist standardmäßig an. Klappt die Steuerung nicht, bleibt der Start wie bisher.
- Sonara startet über den MediaController. In den Einstellungen lässt sich eine Playlist wählen, sobald Sonara sie anbietet; sonst bleiben die Lieblingssongs. Beim Wecken geht PLAY_SHUFFLED nur raus, wenn die App den Befehl anbietet, sonst spielt sie einfach weiter. Ein blindes Umschalten gibt es nicht.

## 1.6.1

- Systemfarbe und die übrigen Darstellungseinstellungen gelten sofort, ohne die App neu zu starten.
- Die Radiosuche spricht `de1.api.radio-browser.info` an, probiert Umlaut-Varianten wie `oe3` und `ö3` und weicht bei einem Ausfall auf weitere Server aus. Ein Fehler erscheint als Hinweis statt als leere Liste. Gespeicherte Adressen des alten Standardservers werden umgestellt.
- Licht vor dem Wecken bietet Lampen, Schalter, Helfer, Skripte und Szenen an. Skripte und Szenen werden bei jedem Schritt aufgerufen, die anderen Einträge ein- und umgeschaltet.
- Der Wecker erscheint bei gesperrtem Bildschirm groß mit Uhrzeit, Grund, Schlummern und Stopp. Auf der Uhr gibt es dieselben Aktionen. Ist das Handy entsperrt in Benutzung, bleibt es bei einer Benachrichtigung. Fehlt die Vollbild-Freigabe ab Android 14, führen Onboarding und Einstellungen dorthin.
- Licht vor dem Wecken kann zum Losgehen ausgehen: Abfahrt minus Fußweg, optional ein paar Minuten später. Verspätung und Plan B verschieben den Zeitpunkt. Lampen, Schalter und Helfer werden ausgeschaltet, Skripte mehrfach aufgerufen oder durch ein Aus-Skript ersetzt. Auf dem Dashboard steht die Uhrzeit.

## 1.6.0

Erster öffentlicher Release.
