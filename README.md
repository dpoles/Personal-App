# Patrimonio

App Android (Kotlin + Jetpack Compose) per gestire a mano il proprio patrimonio, gli investimenti e i
rifornimenti di carburante. Parte vuota: i dati si inseriscono con dei moduli e restano **solo sul telefono**
(un file JSON privato dell'app). Nessun account, nessuna rete.

- **Patrimonio**: un record per mese (entrate, uscite, liquidità, deposito, azioni, monetari) con andamento,
  composizione e risparmio.
- **Azioni** e **Monetari**: acquisti, prezzo attuale per strumento, valore e plusvalenza.
- **Auto**: rifornimenti per veicolo, con spesa, prezzo medio, consumo e costo al km.
- **Previsione km a chiusura periodo**: per ogni veicolo imposti periodo, km iniziali e un limite facoltativo;
  l'app stima il contachilometri a fine periodo dal ritmo attuale e lo confronta con il limite.
- **Importazione da Excel**: apri il file .xlsx con l'app (Condividi / Apri con) o tocca 📥. Legge i fogli
  Bal, Rif, Azioni e Monetari (vedi `docs/STRUTTURA_FOGLIO.md`) e, dopo conferma, sostituisce i dati dell'app.
  Ogni blocco di Rif (es. "Auto 2025") diventa un veicolo con il suo periodo. Il file viene letto sul telefono.
- Grafici disegnati a mano, modalità privacy (occhio in alto) che nasconde gli importi.

Il layout del foglio di partenza dell'autore è descritto in [`docs/STRUTTURA_FOGLIO.md`](docs/STRUTTURA_FOGLIO.md)
(riferimento per i campi; l'app non lo legge).

## Build

```
gradle :app:assembleDebug        # JDK 17 e Android SDK 35
```

L'APK esce in `app/build/outputs/apk/debug/app-debug.apk`; la GitHub Action `Build APK` lo produce a ogni push.
`app/debug.keystore` è una chiave di debug pubblica e fissa: serve a poter aggiornare l'app installata
senza perdere i dati (stessa firma ogni volta). Non protegge nulla.
