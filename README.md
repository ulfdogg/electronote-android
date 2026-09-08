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
- PDF-Export: Notizbuch wird als PDF gerendert und über Androids Speicherort-Auswahl
  gespeichert — dort erscheinen Google Drive/Nextcloud automatisch als Ziel, wenn die
  jeweilige App installiert ist. Keine eigene Cloud-API-Anbindung nötig.
- KI-Assistent-Button: öffnet ChatGPT/Claude/Gemini in Chrome Custom Tabs (nicht in einer
  eingebetteten WebView) — Google blockiert sonst "Mit Google anmelden", genau wie bei
  WKWebView auf iOS. Custom Tabs nutzt die echte, installierte Chrome-Sitzung.

- Live-Übertragung (LiveCast): selbstgebauter HTTP/WebSocket-Server, überträgt den
  aktuellen Bildschirminhalt live an jeden Browser im selben WLAN, mit QR-Code zum
  schnellen Verbinden. Android-Analogon zum iOS-LiveCast (dort NWListener, hier
  java.net.ServerSocket). Bildschirm bleibt während der Übertragung wach.
- OCR: Bereich mit Finger/Stift umkreisen → On-Device-Texterkennung (ML Kit), Ergebnis
  in die Zwischenablage kopierbar.
- Foto-Import: Bild aus der Galerie als neue Seite mit Hintergrundbild einfügen.
- Dokumentenscanner: Kamera-basiertes Scannen mit automatischer Kantenerkennung
  (ML Kit Document Scanner von Google), jede gescannte Seite wird als Notizbuchseite
  eingefügt.

## Bewusst noch nicht enthalten

Direkte Cloud-Konten-Anbindung (WebDAV/Drive-API mit eigenem Login statt über die
Speicherort-Auswahl), Mathe-Erkennung, Formen-Korrektur, Diagramm-Editoren, ClipArt,
Web-Clipper, automatische Seitenübergabe an die KI, Video-Import, YouTube-Einbettung —
folgt schrittweise.

## Build

Kein lokales Android Studio/SDK nötig — der Build läuft über GitHub Actions
(`.github/workflows/android-build.yml`). Jeder Push auf `main` aktualisiert den
Debug-APK-Release unter **Releases → „latest-debug"**.

Installation auf dem Tablet: APK von der Release-Seite herunterladen und installieren
(„Installation aus unbekannten Quellen" erlauben), oder per `adb install -r app-debug.apk`.
