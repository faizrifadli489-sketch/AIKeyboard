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

Ada 3 tab di atas keyboard (mirip Gboard):

- **⌨ Keyboard** — QWERTY dasar (huruf, spasi, enter, backspace, shift) +
  tombol **"Rapikan"** yang ambil teks sebelum kursor, kirim ke Kiosapi,
  lalu ganti dengan hasil yang sudah dirapikan ejaan/tata bahasanya.
- **📋 Klip** — otomatis nyimpen histori copy (maks 15 item terakhir,
  persist walau keyboard ditutup). Tap salah satu item buat paste ke teks
  yang lagi diketik. Ada tombol "Kosongkan" buat hapus semua histori.
- **✨ Tanya AI** — mode chat mini: ketik pertanyaan pake tombol keyboard
  di bawah (ga langsung ke app, cuma masuk ke kotak pertanyaan), tekan
  "Tanya" buat kirim ke Kiosapi, jawabannya muncul di kotak hijau. Ada
  tombol "Sisipkan ke teks" buat masukin jawaban ke field yang lagi aktif,
  dan "Hapus" buat reset.

Catatan: QWERTY di bagian bawah selalu kepake buat ngetik di tab manapun —
di tab "Tanya AI", tombol yang sama dipakai buat ngisi kotak pertanyaan,
bukan langsung ngetik ke app.

## Ide pengembangan lanjut

- Prediksi kata real-time (bukan cuma tombol manual).
- Tombol "Ubah gaya" (formal/santai/singkat).
- Suggestion strip beneran (nampilin 3 opsi kata saat ngetik).
- Histori percakapan "Tanya AI" (multi-turn, bukan cuma 1 pertanyaan).
- Simpen histori "rapikan" biar bisa undo kalau hasil AI kurang pas.
