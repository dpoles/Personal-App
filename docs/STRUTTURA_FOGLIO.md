# Struttura del Google Sheet "Balance"

ID file: `1w_O9NKcD1cZ6LzdovwwgNsJU7MyUN2OO7V0hUKC6w1U`
Questo documento descrive solo il layout (nessun dato). L'app legge i valori con la Sheets API usando
`valueRenderOption=UNFORMATTED_VALUE` e `dateTimeRenderOption=SERIAL_NUMBER`: i numeri arrivano già
numerici e le date come seriali (giorni dal 30/12/1899).

I fogli usati sono i primi 4, nell'ordine: **Bal, Rif, Azioni, Monetari**. Gli altri (archivi `Inv…`,
`Balance…`, `Irpef`, `Bal Sim`, ecc.) vengono ignorati.

## 1. Bal (A1:AU90): bilancio patrimoniale mensile
Riga 1 = intestazioni, una riga per mese da 2021-09 a 2028-12. Intestazioni (le righe vanno cercate per nome,
con trim; alcune si ripetono: usare la prima occorrenza):

| Col | Intestazione | Significato |
|---|---|---|
| A, B | Y, M | anno, mese (numeri) |
| C | T | tipo riga: `A` consuntivo, `C` mese corrente, `E` stima futura |
| D, E | IN, OUT | entrate / uscite del mese |
| F, G | MOVE DEP, MOVE INV | movimenti verso depositi / investimenti |
| H | BAL. | saldo del mese (IN-OUT) |
| I | BAL. TOT. | liquidità cumulata |
| J | C/C | conto corrente |
| K-N | MOVE DEP, DEP, INT, PRG INT | deposito e interessi (progressivi) |
| O-Q | SANTANDER, INT, PRG INT | conto deposito Santander e interessi |
| R | AZIONI | valore portafoglio azioni (ETF) |
| S | MONETARI | valore ETF monetario |
| T-V | %, ETC, OBBL | quote ETC / obbligazioni |
| W-Y | PRG YEAR, PRG NET, PRG NET AVG | progressivi di risparmio |
| Z, AA | TOT NO AZ, TOT AZ | totale senza / con azioni |
| AB-AF | % A, % M, % C, % O, % L | allocazione: azioni, monetari, c/c, obbligazioni, liquidità |
| AG | TOT | **patrimonio totale** (≈ O + P + R + S + I + J) |

A destra (AH:AU) ci sono riepiloghi ausiliari (Media IN/OUT/RISP, spese ricorrenti): non usati.
Numeri in formato italiano nel foglio, ma via API arrivano come numeri.

## 2. Rif (A1:S85): rifornimenti carburante
Più blocchi verticali, uno per veicolo/anno. Ogni blocco inizia con una riga di intestazione `Data`.
Il titolo (es. "Auto 2025", "Moto 2025") sta nella riga sopra l'intestazione; i primi due blocchi non hanno
titolo (sono Auto 2026 e Moto 2026: l'app deduce il nome da km e anno).

Colonne: A Data · B Importo · C Prg · D Pieno (Si/No) · E Prezzo €/L · F KM (contachilometri) · G GG (giorni
dal precedente) · H LT · I Valore GG · J KM Litro · K KM GG · L KM · M Somma KM · N Somma GG · O Media KM ·
P Proiezione.

La prima riga di un blocco è spesso una base (importo 0, solo km). L'ultima riga del blocco contiene
solo il contachilometri attuale (col F), senza importo.

## 3. Azioni (A1:AV65) e 4. Monetari (A1:AB61): stesso layout
- Riga 1: da col X l'etichetta "Mese", poi (Y…) date di fine mese (serie storica).
- Riga 2: intestazioni lotti: A Nome Investimento · B ISIN · C Http · D Data Inv · E Prz Acq · F Qtà · G Comm ·
  H Val Acq · I Bollo · J Prz Act · K Val Act · L Diff · M % Int · N Tax % · O Net · P GG Tot · Q Int Day ·
  R Avg Int % · S To Earn · T/W Total EoY · U Total EoY % · V Total Actual.
- Righe lotti: una per acquisto (Azioni: ETF VWCE, molti lotti; Monetari: ETF XEON, un lotto).
- Righe riepilogo (cercare per etichetta in col A): `TOTALE INVESTITO`, `Imposte bollo`, `Saldo Disponibile`,
  `TOTALE CONTO FINECO` (da col Y: serie mensile del valore), `Variazione Su Valore Netto`,
  `Crescita Mensile Lorda`, `Crescita Annuale Lorda`, `Bollo Conto Deposito`.
- In col W compare l'etichetta `Capitale` (serie del capitale versato) e `Interesse Netto`.
- Sezione `Versamenti`: righe con A importo, B cumulato, D data, E causale (Credit/Ing/Santander).
  Una data può essere testo malformato: va ignorata.
