# CyberCarWasher

App Android del **cliente** dell'autolavaggio: legge la fidelity card MIFARE Classic 1K e mostra i dati del titolare, il saldo residuo e il catalogo dei servizi.

> **Progetto CTF — RomHack Camp 2026**
>
> Questa repository fa parte della challenge **Cyber Car Washer**, una CTF portata al **RomHack Camp 2026**. La challenge simula il sistema di fidelity card di un autolavaggio "cyber" ed è interamente basata su **MIFARE Classic 1K**: il saldo, l'anagrafica del cliente e la data di creazione vivono sulla card, protetti da chiavi diverse per ogni settore derivate lato backend.
>
> Il progetto è composto da tre repository:
>
> | Repository | Ruolo |
> |---|---|
> | [`cyber_car_washer_be`](https://github.com/IadRabbit/cyber_car_washer_be) | Backend serverless (AWS) che deriva le chiavi dei settori a partire dal blocco 0 della card |
> | [`CyberCarWasherAdmin`](https://github.com/IadRabbit/CyberCarWasherAdmin) | App Android dell'operatore: emissione card, ricarica, POS, formattazione |
> | [`CyberCarWasher`](https://github.com/IadRabbit/CyberCarWasher) | App Android del cliente: consultazione della card e catalogo servizi |

## Infrastruttura

Il componente evidenziato con ★ è quello contenuto in questa repository.

```
┌─ AWS — VPC, 2 subnet pubbliche (Terraform) ─────────────────────────────────────┐
│  ┌──────────────────────────────────┐     ┌──────────────────────────────────┐  │
│  │ Application Load Balancer        │     │ AWS Lambda (Python 3.13)         │  │
│  │ HTTPS :443, cert self-signed     │ ──▶ │ cyber_car_washer_be              │  │
│  │ X-Api-Key valida → forward       │     │ UID → 16 × (Key A, Key B)        │  │
│  │ altrimenti → 404                 │     │ PRNG deterministico              │  │
│  └──────────────────────────────────┘     └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                     ▲
                     │  POST {"uid": <blocco 0 hex>}  →  {"keys": [...]}
                     │  header X-Api-Key + certificate pinning
                ┌────┴──────────────────────────────────┐
┌───────────────┴──────────────┐        ┌───────────────┴──────────────┐
│ CyberCarWasherAdmin          │        │ ★ CyberCarWasher             │
│ app Android operatore        │        │ app Android cliente          │
│ emissione, ricarica, POS,    │        │ dati card, saldo,            │
│ formattazione, raw edit      │        │ catalogo servizi             │
└───────────────┬──────────────┘        └───────────────┬──────────────┘
                │ NFC                                   │ NFC
                └───────────────────┬───────────────────┘
                                    ▼
                  ┌──────────────────────────────────┐
                  │ MIFARE Classic 1K                │
                  │                                  │
                  │ sett. 0    blocco 0 (UID)        │
                  │ sett. 1-11 dati statici          │
                  │ sett. 12   saldo (value block)   │
                  │ sett. 13   data creazione        │
                  │ sett. 14   cognome               │
                  │ sett. 15   nome                  │
                  │ chiavi A/B diverse per settore   │
                  └──────────────────────────────────┘
```

## Funzionalità

| Schermata | Descrizione |
|---|---|
| **La mia card** (`CardActivity`) | Al tap della card recupera le chiavi dal backend e legge nome, cognome, data di creazione e saldo |
| **Servizi** (`ServicesActivity`) | Catalogo dei lavaggi con i relativi prezzi |

Le operazioni che modificano il saldo (ricarica e addebito) sono disponibili solo nell'app operatore, [`CyberCarWasherAdmin`](https://github.com/IadRabbit/CyberCarWasherAdmin).

## Layout della card (MIFARE Classic 1K)

| Settore | Contenuto | Formato |
|---|---|---|
| 0 | Blocco 0 del produttore (UID), usato per derivare le chiavi | sola lettura |
| 1–11 | Dati statici scritti in fase di emissione | testo ASCII, 48 byte per settore |
| 12 | Saldo | value block (int32 little endian, con copia invertita e byte di indirizzo) |
| 13 | Data di creazione | 3 value block: anno, mese, giorno |
| 14 | Cognome | testo UTF-8, max 48 byte |
| 15 | Nome | testo UTF-8, max 48 byte |

Le chiavi A/B dei settori non sono salvate nell'app: a ogni tap l'app legge il blocco 0, lo invia al backend e riceve le 16 coppie di chiavi con cui autenticarsi.

## Struttura del progetto

```
app/src/main/java/com/meethack/romhack/camp2026/
├── activities/      # schermate (UI) + NfcActivity, base comune per il dispatch NFC
├── wrappers/
│   ├── NfcWrapper.java      # logica MIFARE: auth e lettura dei settori
│   ├── RequestWrapper.java  # chiamata al backend + certificate pinning
│   └── Utils.java
├── assets/          # Customer, catalogo Services, Money
└── exceptions/
```

## Build

Requisiti: Android Studio, JDK 17+, dispositivo Android 11+ (minSdk 30) con NFC compatibile MIFARE Classic (chip NXP).

```bash
./gradlew assembleDebug
```

La configurazione del backend si trova in `app/src/main/res/values/strings.xml` (`keys_endpoint`, `keys_header_name`, `keys_header_value`); il certificato pinnato dell'ALB è in `app/src/main/res/raw/cyber_car_washer_cert.pem`.

## Nota sullo sviluppo

La **parte visiva** (layout, animazioni, stile delle schermate) è stata *vibecodata* con l'aiuto di strumenti di AI.

**Non** lo sono stati invece:

- la **progettazione** della challenge e del layout dei dati sulla card;
- la **logica core** (gestione dei settori MIFARE, autenticazione, value block, operazioni di incremento/decremento, derivazione delle chiavi);
- la **validazione** dell'input e dei dati letti dalla card;
- il **controllo di integrità** (verifica del formato dei value block e degli access bits prima di ogni operazione sul saldo);
- l'**infrastruttura** (AWS, Terraform, ALB, Lambda, certificate pinning).
