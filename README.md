# Patrimonio

App Android (Kotlin + Jetpack Compose) per gestire a mano il proprio patrimonio, gli investimenti e i
rifornimenti di carburante. Parte vuota: i dati si inseriscono con dei moduli e restano **solo sul telefono**
(un file JSON privato dell'app). Nessun account, nessuna rete.

- **Patrimonio**: un record per mese (entrate, uscite, liquidità, deposito, azioni, monetari) con andamento,
  composizione e risparmio.
- **Azioni** e **Monetari**: acquisti, prezzo attuale per strumento, valore e plusvalenza.
- **Auto**: rifornimenti per veicolo, con spesa, prezzo medio, consumo e costo al km.
- **Quotazioni in tempo reale**: per ogni strumento puoi indicare un simbolo (es. `VWCE.DE`, `XEON.DE`, Xetra in euro);
  l'app aggiorna il prezzo ogni minuto (Yahoo Finance, nessuna chiave) e ricalcola valore, plusvalenza e patrimonio.
  L'import dal foglio ricava il simbolo dai link Teleborsa.
- **Al netto delle tasse**: aliquota per strumento (importata dal foglio o impostata a mano, default 26%) sulla
  plusvalenza complessiva; le perdite non generano imposta. Mostrati lordo e netto, anche per la proiezione.
- **Proiezione a fine anno**: ogni acquisto prosegue al suo rendimento storico (solo se detenuto da 30+ giorni),
  più il risparmio medio degli ultimi mesi. È una stima, non una previsione di mercato. Non include il bollo.
- **Previsione km a chiusura periodo**: per ogni veicolo imposti periodo, km iniziali e un limite facoltativo;
  l'app stima il contachilometri a fine periodo dal ritmo attuale e lo confronta con il limite.
- **Importazione da Google Sheets**: tocca 📥 → "Da Google Sheets". L'app legge in sola lettura i fogli Bal, Rif,
  Azioni e Monetari del documento *Balance* (accesso con il tuo account Google; il foglio resta privato).
  Richiede la configurazione di Google Cloud descritta sotto.
- **Importazione da Excel**: apri il file .xlsx con l'app (Condividi / Apri con) o tocca 📥. Legge i fogli
  Bal, Rif, Azioni e Monetari (vedi `docs/STRUTTURA_FOGLIO.md`) e, dopo conferma, sostituisce i dati dell'app.
  Ogni blocco di Rif (es. "Auto 2025") diventa un veicolo con il suo periodo. Il file viene letto sul telefono.
- Grafici disegnati a mano, modalità privacy (occhio in alto) che nasconde gli importi.

Il layout del foglio di partenza dell'autore è descritto in [`docs/STRUTTURA_FOGLIO.md`](docs/STRUTTURA_FOGLIO.md)
(riferimento per i campi; l'app non lo legge).

## Build

Versione leggera (consigliata, ~2 MB, ottimizzata con R8): `gradle :app:assembleRelease` →
`app/build/outputs/apk/release/app-release.apk`. È firmata con la stessa chiave fissa della debug (stesso SHA-1,
aggiorna l'app installata senza perdere i dati).

```
gradle :app:assembleDebug        # JDK 17 e Android SDK 35
```

L'APK esce in `app/build/outputs/apk/debug/app-debug.apk`; la GitHub Action `Build APK` lo produce a ogni push.
`app/debug.keystore` è una chiave di debug pubblica e fissa: serve a poter aggiornare l'app installata
senza perdere i dati (stessa firma ogni volta). Non protegge nulla.

## Configurare l'accesso a Google (una volta sola)

1. Su <https://console.cloud.google.com> crea un progetto e abilita **Google Sheets API**.
2. *Schermata consenso OAuth*: tipo **Esterno**, stato "In test", aggiungi il tuo account Google tra gli
   **utenti di test**; tra gli ambiti aggiungi `.../auth/spreadsheets.readonly`.
3. *Credenziali → Crea credenziali → ID client OAuth → Android*:
   - Nome pacchetto: `com.dpoles.patrimonio`
   - Impronta SHA-1: `75:BA:54:A5:F7:DA:36:44:A7:46:4D:AE:1E:CF:B6:B1:FF:06:F8:20` (chiave di debug fissa in `app/debug.keystore`).
4. In app: 📥 → *Da Google Sheets* → scegli l'account e consenti la sola lettura.

Se il documento cambia, aggiorna `SHEET_ID` in `app/src/main/java/com/dpoles/patrimonio/data/SheetsApi.kt`.
