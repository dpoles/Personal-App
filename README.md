# Patrimonio

App Android (Kotlin + Jetpack Compose) che legge in **sola lettura** i primi 4 fogli del tuo Google Sheet
e li mostra in una dashboard: **Patrimonio** (Bal), **Azioni**, **Monetari** e **Auto** (Rif, rifornimenti).

- Grafici disegnati a mano (area con proiezione tratteggiata, ciambella, barre), trascinabili.
- Modalità privacy (occhio in alto) che nasconde tutti gli importi.
- Pull-to-refresh e copia locale dell'ultimo download: funziona anche offline.
- Nessun dato è incorporato nell'app: viene letto dal foglio con il tuo login Google.
- La struttura attesa del foglio è descritta in [`docs/STRUTTURA_FOGLIO.md`](docs/STRUTTURA_FOGLIO.md).

## Configurare l'accesso a Google (una volta sola)

1. Su <https://console.cloud.google.com> crea un progetto e abilita **Google Sheets API**.
2. *Schermata consenso OAuth*: tipo **Esterno**, stato "Test", aggiungi il tuo account Google tra gli **utenti di test**.
   Aggiungi lo scope `.../auth/spreadsheets.readonly`.
3. *Credenziali → Crea credenziali → ID client OAuth → Android*:
   - Nome pacchetto: `com.dpoles.patrimonio`
   - Impronta SHA-1: `75:BA:54:A5:F7:DA:36:44:A7:46:4D:AE:1E:CF:B6:B1:FF:06:F8:20`
     (è la chiave di debug fissa in `app/debug.keystore`, quindi non cambia a ogni build).
4. Installa l'APK e tocca **Accedi con Google**.

Se in futuro cambi il file Google Sheet, aggiorna `SHEET_ID` in `app/src/main/java/com/dpoles/patrimonio/data/SheetsApi.kt`.

## Build

```
gradle :app:assembleDebug        # richiede JDK 17 e Android SDK 35
```

L'APK esce in `app/build/outputs/apk/debug/app-debug.apk`. La GitHub Action `Build APK` lo produce a ogni push
(scaricalo dagli *Artifacts* della run).

> `app/debug.keystore` è una chiave di debug pubblica, non protegge nulla: serve solo ad avere uno SHA-1 stabile.
