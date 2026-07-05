# AI Keyboard

Custom keyboard Android dengan tombol AI ("Rapikan") yang manggil Kiosapi buat
memperbaiki kalimat yang sedang diketik.

## Cara build (dari HP, via GitHub)

1. Push folder ini jadi repo baru di GitHub (bisa lewat Termux: `git init`, `git add .`, `git commit`, `git push`).
2. Di repo GitHub, masuk **Settings > Secrets and variables > Actions > New repository secret**:
   - Name: `KIOSAPI_KEY`
   - Value: key Kiosapi lo (`kios_live_xxxx`)
3. Push ke branch `main` → tab **Actions** otomatis jalan build.
4. Setelah selesai (~3-5 menit), buka run-nya → download artifact `ai-keyboard-debug.zip`
   → extract → dapet `app-debug.apk`.
5. Install APK itu di HP (aktifkan "Install from unknown sources" kalau diminta).

## Cara aktifin keyboard-nya

1. Settings HP > System > Languages & input > On-screen keyboard > Manage keyboards
   → aktifkan "AI Keyboard".
2. Pas lagi ngetik di app manapun, tekan lama ikon keyboard di navigation bar
   (atau via notification pemilih keyboard) → pilih "AI Keyboard".

## Fitur saat ini

Layout **Gboard-style**: top toolbar (ikon) + suggestion strip (3 slot) + QWERTY rounded + bottom bar.

- **Top toolbar** — A (switch IME), 📋 clip, 🔍 search, ✨ Rapikan, 🎙 voice, 💬 Tanya AI, ⋮ help.
- **Suggestion strip** — slot 1: tombol **"Rapikan"** (potong teks sebelum kursor, kirim ke Kiosapi, ganti dengan versi ejaan rapi). Slot 2 & 3: placeholder, siap diisi prediksi nanti.
- **Keyboard** — QWERTY rounded dengan hint angka kecil di pojok atas (Q=1, W=2, dst). Mode `?123`/`ABC` toggle. Halaman simbol: `1234567890`, `!@# ...`, `-_=/.,?`.
- **Bottom bar** — ?123, emoji 😊, globe 🌐, space (`ID • DE`), period, enter.
- **📋 Klip panel** (expanded view, dibuka dari toolbar) — histori copy maks 15 item, tap buat paste.
- **✨ Tanya AI panel** (expanded view, dibuka dari toolbar) — kotak pertanyaan + tombol Tanya/Hapus/Sisip. Bisa ngetik pertanyaan di keyboard, hasilnya muncul di kotak hijau, "Sisip" masukin ke field yang aktif.

Catatan: panel Klip & Tanya AI dipanggil dari ikon toolbar, bukan tab gede lagi. Tombol ⌨ di header panel buat balik ke keyboard utama.

## Ide pengembangan lanjut

- Prediksi kata real-time (bukan cuma tombol manual).
- Tombol "Ubah gaya" (formal/santai/singkat).
- Suggestion strip beneran (nampilin 3 opsi kata saat ngetik).
- Histori percakapan "Tanya AI" (multi-turn, bukan cuma 1 pertanyaan).
- Simpen histori "rapikan" biar bisa undo kalau hasil AI kurang pas.
