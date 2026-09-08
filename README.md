# ElectroNote (Android)

Frühe Android-Version von [ElectroNote](https://github.com/) (iOS/iPadOS-App für digitale Notizbücher).
Kein Port — eine eigenständige Neuentwicklung, die sich nur das Produktkonzept teilt, da
Apples PencilKit/Vision-Frameworks auf Android keine Entsprechung haben.

## Aktueller Stand (MVP)

- Notizbuch-Liste (anlegen, öffnen, löschen)
- Handschrift-/Stift-Eingabe auf einer eigenen Zeichen-Canvas (Finger + Stylus)
- Undo/Redo, Farbauswahl
- Mehrseitige Notizbücher, lokal gespeichert (App-interner Speicher)
- PDF-Import (jede Seite wird als Seiten-Hintergrund importiert, Stift schreibt darüber)

## Bewusst noch nicht enthalten

Cloud-Sync (Nextcloud/Google Drive), KI-Sidebar, Live-Cast, OCR/Handschrift- & Mathe-Erkennung,
Formen-Korrektur, Diagramm-Editoren, ClipArt, Web-Clipper — folgt schrittweise.

## Build

Kein lokales Android Studio/SDK nötig — der Build läuft über GitHub Actions
(`.github/workflows/android-build.yml`). Jeder Push auf `main` aktualisiert den
Debug-APK-Release unter **Releases → „latest-debug"**.

Installation auf dem Tablet: APK von der Release-Seite herunterladen und installieren
(„Installation aus unbekannten Quellen" erlauben), oder per `adb install -r app-debug.apk`.
