# WhatStat - Scoperte e Problemi

## 📊 STATO ATTUALE

### ✅ FUNZIONA
- **Media & Links globale**: mostra i link per chat
- **Media & Links in Chat Detail**: mostra breakdown per sender (Me vs Altri)
- **Link tooltip**: rimosso, ora mostra nella lista

### ❌ NON FUNZIONA
- **Radar Parole (Word Search)**: query non restituisce risultati nell'app nonostante i dati esistano nel DB

---

## 🔍 RADAR PAROLE (Word Search)

### Problema
- Query SQL in Kotlin restituisce **0 risultati**
- La **stessa query** in DB Browser restituisce **383 risultati** per "serena"

### Query funzionante in DB Browser
```sql
SELECT
    COALESCE(j.raw_string, 'me@s.whatsapp.net') AS sender_jid,
    COALESCE(j.user, 'Me') AS sender_name,
    COUNT(*) AS cnt
FROM message m
LEFT JOIN jid j ON m.sender_jid_row_id = j._id
WHERE m.chat_row_id = 21 
  AND m.text_data IS NOT NULL
  AND LOWER(m.text_data) LIKE '%serena%'
GROUP BY COALESCE(j.raw_string, 'me@s.whatsapp.net')
ORDER BY cnt DESC;
```

**Risultato**: 383 messaggi da:
- Mamma: 326
- Me: 19
- Papà: 16
- Lara: 12
- @lid: 10

### Query attuale nel DAO (MessageDao.kt line 429)
```kotlin
val query = """
    SELECT
        COALESCE(j.raw_string, 'me@s.whatsapp.net') AS jid,
        COALESCE(j.user, 'Me') AS display_name,
        COUNT(*) AS count
    FROM message m
    LEFT JOIN jid j ON m.sender_jid_row_id = j._id
    WHERE ${conditions.joinToString(" AND ")}
      AND LOWER(m.text_data) LIKE LOWER(?)
    GROUP BY COALESCE(j.raw_string, 'me@s.whatsapp.net')
    ORDER BY count DESC
    LIMIT 50
"""
```

### Possibili cause
1. **Database diverso**: l'app potrebbe usare un DB diverso da quello ispezionato
2. **cursor.use()**: potrebbe chiudere il cursor prima di leggere
3. **Condizioni WHERE**: qualcosa in più esclude tutti i risultati
4. **Args order**: l'ordine degli argomenti potrebbe essere sbagliato

---

## 🔗 MEDIA & LINK - Partecipanti Gruppo

### Problema risoluzione nomi
I partecipanti sono divisi in due gruppi:

| JID | Esempio | stato | Note |
|-----|--------|-------|------|
| `@s.whatsapp.net` | `393204277608@s.whatsapp.net` | ✅ Risolto | Sono nei contatti wa_contacts |
| `@lid` | `137705358913673@lid` | ❌ Non risolto | Non è nei contatti |

### Dati gruppi (chat 21 - "Scialpi's 🐶Family")

**Sender con @s.whatsapp.net** (nei contatti):
| JID | Nome | Link |
|-----|------|------|
| 393204277608@s.whatsapp.net | Mamma | 2 |
| 393287632418@s.whatsapp.net | Serena | 2 |
| 393293719659@s.whatsapp.net | Lara | 2 |
| 393286579097@s.whatsapp.net | Papà | 1 |
| me@s.whatsapp.net | Me | 8 |

**Sender con @lid** (NON nei contatti):
| JID | Messaggi |
|-----|----------|
| 137705358913673@lid | 8 (其中 "Luigi tutto bene?", "Sta facendo la brava?") |
| 266300924375087@lid | 3 |
| 66872808632469@lid | 3 |
| 62607755108471@lid | 1 |

### Scoperta chiave
I **@lid** sono gli **STESSI UTENTI** dei @s.whatsapp.net!

Esempio: `137705358913673@lid` è **Mamma** (stessi messaggi: "Luigi todo bene?").

WhatsApp sometimes usa `@lid` invece di `@s.whatsapp.net` per lo stesso utente.

**In wa_contacts ci sono 644 entry @lid** ma senza nome - nessun collegamento diretto.

### Tabella wa_contacts (wa.db)
```
_id	jid	is_whatsapp_user	status	number	raw_contact_id	display_name	phone_type...
644	55633130823764@lid	1	0	...	...	...
```

### Query per capire i @lid
```sql
-- In wa.db
SELECT * FROM wa_contacts WHERE jid LIKE '%@lid';
```

---

## 🗃️ STRUTTURA DATABASE

### msg.db - Tabelle principali
- `chat` - le chat
- `message` - i messaggi
- `jid` - gli ID (utenti e chat)
- `message_media` - media

### wa.db - Tabelle contatti
- `wa_contacts` - contatti (644 entry @lid senza nome!)
- Varie tabelle group_* per membership

---

## 📝 TODO

### Radar Parole
1. Debug più approfondito nel DAO
2. Verificare che la query Kotlin sia identica a quella in DB Browser
3. Testare senza condizioni WHERE aggiuntive

### Media & Links
1. Collegare @lid agli utenti noti basandosi sui messaggi
2. O mostrare "Membro #N" per utenti anonimi

---

## 📋 DEBUG ATTUALE

### Media Stats Debug Panel
Mostra:
- linkStats (totale per chat)
- linkStatsBySender (breakdown per sender)
- DEBUG INFO con risoluzione nomi

### Word Search Debug Panel
Mostra:
- searchQuery
- chatId
- startTime / endTime
- Direct DAO results count
- Repository results count