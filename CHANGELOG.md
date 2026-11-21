# Changelog

## 1.6.2

- Der Radio-Stream im Musik-Wecker hält das WLAN wach und wartet die eingestellte Fallback-Zeit, auch wenn das Netz nach dem Wecken erst hochfährt. Eine stille Lautstärkerampe gilt nicht als Ausfall. Scheitert der Stream, stehen Fehlercode und Grund in der Benachrichtigung und beim Testen.

## 1.6.1

- Systemfarbe und die übrigen Darstellungseinstellungen gelten sofort, ohne die App neu zu starten.
- Die Radiosuche spricht `de1.api.radio-browser.info` an, probiert Umlaut-Varianten wie `oe3` und `ö3` und weicht bei einem Ausfall auf weitere Server aus. Ein Fehler erscheint als Hinweis statt als leere Liste. Gespeicherte Adressen des alten Standardservers werden umgestellt.
- Licht vor dem Wecken bietet Lampen, Schalter, Helfer, Skripte und Szenen an. Skripte und Szenen werden bei jedem Schritt aufgerufen, die anderen Einträge ein- und umgeschaltet.
- Der Wecker erscheint bei gesperrtem Bildschirm groß mit Uhrzeit, Grund, Schlummern und Stopp. Auf der Uhr gibt es dieselben Aktionen. Ist das Handy entsperrt in Benutzung, bleibt es bei einer Benachrichtigung. Fehlt die Vollbild-Freigabe ab Android 14, führen Onboarding und Einstellungen dorthin.
- Licht vor dem Wecken kann zum Losgehen ausgehen: Abfahrt minus Fußweg, optional ein paar Minuten später. Verspätung und Plan B verschieben den Zeitpunkt. Lampen, Schalter und Helfer werden ausgeschaltet, Skripte mehrfach aufgerufen oder durch ein Aus-Skript ersetzt. Auf dem Dashboard steht die Uhrzeit.

## 1.6.0

Erster öffentlicher Release.
