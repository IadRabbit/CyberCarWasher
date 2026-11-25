# CyberCarWasher

Android app for the car wash **customer**: it reads the MIFARE Classic 1K loyalty card and shows the holder's details, the remaining balance and the service catalog.

> **CTF project — RomHack Camp 2026**
>
> This repository is part of **Cyber Car Washer**, a CTF challenge brought to **RomHack Camp 2026** on behalf of the **MeetHack** team. The challenge simulates the loyalty card system of a "cyber" car wash and is entirely based on **MIFARE Classic 1K**: the balance, the customer's personal data and the creation date live on the card, protected by per-sector keys derived on the backend.
>
> The project is made of three repositories:
>
> | Repository | Role |
> |---|---|
> | [`cyber_car_washer_be`](https://github.com/IadRabbit/cyber_car_washer_be) | Serverless backend (AWS) that derives the sector keys from the card's block 0 |
> | [`CyberCarWasherAdmin`](https://github.com/IadRabbit/CyberCarWasherAdmin) | Operator Android app: card issuing, top-up, POS, formatting |
> | [`CyberCarWasher`](https://github.com/IadRabbit/CyberCarWasher) | Customer Android app: card details and service catalog |

## Infrastructure

The component marked with ★ is the one contained in this repository.

```
┌─ AWS — VPC, 2 public subnets (Terraform) ───────────────────────────────────────┐
│  ┌──────────────────────────────────┐     ┌──────────────────────────────────┐  │
│  │ Application Load Balancer        │     │ AWS Lambda (Python 3.13)         │  │
│  │ HTTPS :443, cert self-signed     │ ──▶ │ cyber_car_washer_be              │  │
│  │ valid X-Api-Key → forward        │     │ UID → 16 × (Key A, Key B)        │  │
│  │ otherwise → 404                  │     │ deterministic PRNG               │  │
│  └──────────────────────────────────┘     └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                     ▲
                     │  POST {"uid": <block 0 hex>}  →  {"keys": [...]}
                     │  header X-Api-Key + certificate pinning
                ┌────┴──────────────────────────────────┐
┌───────────────┴──────────────┐        ┌───────────────┴──────────────┐
│ CyberCarWasherAdmin          │        │ ★ CyberCarWasher             │
│ operator Android app         │        │ customer Android app         │
│ issuing, top-up, POS,        │        │ card data, balance,          │
│ formatting, raw edit         │        │ service catalog              │
└───────────────┬──────────────┘        └───────────────┬──────────────┘
                │ NFC                                   │ NFC
                └───────────────────┬───────────────────┘
                                    ▼
                 ┌────────────────────────────────────┐
                 │ MIFARE Classic 1K                  │
                 │                                    │
                 │ sector 0     block 0 (UID)         │
                 │ sectors 1-11 static data           │
                 │ sector 12    balance (value block) │
                 │ sector 13    creation date         │
                 │ sector 14    surname               │
                 │ sector 15    name                  │
                 │ per-sector A/B keys                │
                 └────────────────────────────────────┘
```

## Features

| Screen | Description |
|---|---|
| **My card** (`CardActivity`) | On card tap, fetches the keys from the backend and reads name, surname, creation date and balance |
| **Services** (`ServicesActivity`) | Car wash catalog with prices |

Operations that change the balance (top-up and charge) are only available in the operator app, [`CyberCarWasherAdmin`](https://github.com/IadRabbit/CyberCarWasherAdmin).

## Card layout (MIFARE Classic 1K)

| Sector | Content | Format |
|---|---|---|
| 0 | Manufacturer block 0 (UID), used to derive the keys | read-only |
| 1–11 | Static data written at issuing time | ASCII text, 48 bytes per sector |
| 12 | Balance | value block (int32 little endian, with inverted copy and address bytes) |
| 13 | Creation date | 3 value blocks: year, month, day |
| 14 | Surname | UTF-8 text, max 48 bytes |
| 15 | Name | UTF-8 text, max 48 bytes |

Sector A/B keys are not stored in the app: on every tap the app reads block 0, sends it to the backend and receives the 16 key pairs used to authenticate.

## Project structure

```
app/src/main/java/com/meethack/romhack/camp2026/
├── activities/      # screens (UI) + NfcActivity, shared base for NFC dispatch
├── wrappers/
│   ├── NfcWrapper.java      # MIFARE logic: auth and sector reads
│   ├── RequestWrapper.java  # backend call + certificate pinning
│   └── Utils.java
├── assets/          # Customer, Services catalog, Money
└── exceptions/
```

## Build

Requirements: Android Studio, JDK 17+, an Android 11+ device (minSdk 30) with MIFARE Classic compatible NFC (NXP chip).

```bash
./gradlew assembleDebug
```

Backend configuration lives in `app/src/main/res/values/strings.xml` (`keys_endpoint`, `keys_header_name`, `keys_header_value`); the pinned ALB certificate is in `app/src/main/res/raw/cyber_car_washer_cert.pem`.

## Development note

The **visual part** (layouts, animations, screen styling) was *vibe-coded* with the help of AI tools.

The following parts were **not**:

- the **design** of the challenge and of the data layout on the card;
- the **core logic** (MIFARE sector handling, authentication, value blocks, increment/decrement operations, key derivation);
- **validation** of user input and of data read from the card;
- the **integrity checks** (value block format and access bits verification before every balance operation);
- the **infrastructure** (AWS, Terraform, ALB, Lambda, certificate pinning).
