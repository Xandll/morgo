# Release bauen

Die signierte APK entsteht mit `./gradlew assembleRelease`. Der Keystore liegt nicht im Repo.

Lokal stehen Pfad und Passwortdatei in `local.properties`:

```
morgo.keystore=/pfad/zur/morgo-release.jks
morgo.keystore.properties=/pfad/zur/keystore.properties
```

In GitHub Actions heißen die Secrets `MORGO_KEYSTORE_B64`, `MORGO_KEYSTORE_PASSWORD`, `MORGO_KEY_ALIAS` und `MORGO_KEY_PASSWORD`. Fehlen sie, fällt der Build auf den Debug-Key zurück. Den darf ein öffentliches Release nicht benutzen. Der Release-Workflow bricht ab, wenn das Zertifikat `CN=Android Debug` ist.

Ein Tag `vX.Y.Z` erzeugt das GitHub Release. Details stehen in der README.
