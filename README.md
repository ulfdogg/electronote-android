# ElectroNote (Android)

Android-Version von [ElectroNote](https://github.com/) (iOS/iPadOS-App für digitale Notizbücher).
Kein Port — eine eigenständige Neuentwicklung, die sich nur das Produktkonzept teilt, da
Apples PencilKit/Vision-Frameworks auf Android keine Entsprechung haben.

## Aktueller Stand

- Notizbuch-Liste: anlegen, öffnen, favorisieren (Stern), mit Tags versehen/filtern,
  in den Papierkorb verschieben (wiederherstellbar, oder endgültig löschen/leeren)
- Unendliches, durchgehend scrollendes Notizbuch (statt fester Einzelseiten)
- Zeichnen: Stift/Marker/Bleistift/Radierer, Farbauswahl, Strichstärke, Formen-Korrektur
  (Linie/Kreis/Rechteck), Ink-Presets (gespeicherte Stift-Konfigurationen)
- Papiervorlagen: Blanko, Kariert, Liniert (mit Zeilenabstand-Auswahl), Punktraster, Cornell
- Text einfügen (editierbar), Haftzettel
- Lesezeichen: benannte Sprungmarken im langen Notizbuch, zum schnellen Zurückspringen
- Linke Werkzeug-Seitenleiste (Stift-Auswahl/Farbe/Stärke/Papier/Presets), angelehnt an
  das iPad-Layout; Stylus-Tastendruck schaltet wie Apple Pencils Doppeltipp zum Radierer
  um und zurück
- Dunkelmodus (persistiert, nicht nur System-Einstellung folgend), dunkelt auch das
  Papier ab
- PDF-Import (jede Seite wird als Hintergrund-Ebene importiert, Stift schreibt darüber)
- PDF-Export: Notizbuch wird als PDF gerendert
  - „Exportieren": über Androids Speicherort-Auswahl speichern — dort erscheinen Google
    Drive/Nextcloud automatisch als Ziel, wenn die jeweilige App installiert ist
  - „Teilen": öffnet das native Android-Teilen-Menü (Mail, WhatsApp, Nearby Share, …)
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
- Nextcloud: browserbasierter Login (Login Flow v2, kein manuelles WebDAV-Passwort),
  manueller Upload/Download eines Notizbuchs (kein automatischer Hintergrund-Abgleich)

## Bewusst noch nicht enthalten

Mathe-Erkennung, Diagramm-Editoren (Ablaufplan/MindMap), Whiteboard-Modus,
Elektro-Bauteil-Bibliothek/Schaltungs-Simulator, ClipArt, Web-Clipper,
Volltextsuche über Notizen, iCloud-/Google-Drive-Sync, eigener PDF-Betrachter,
Video-Import/YouTube-Einbettung, Lineal — folgt schrittweise nach Priorität.

## Build

Kein lokales Android Studio/SDK nötig — der Build läuft über GitHub Actions
(`.github/workflows/android-build.yml`). Jeder Push auf `main` aktualisiert den
Debug-APK-Release unter **Releases → „latest-debug"**.

Installation auf dem Tablet: APK von der Release-Seite herunterladen und installieren
(„Installation aus unbekannten Quellen" erlauben), oder per `adb install -r app-debug.apk`.
