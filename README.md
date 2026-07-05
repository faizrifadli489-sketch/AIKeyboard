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

- Keyboard QWERTY dasar (huruf, spasi, enter, backspace, shift).
- Tombol **"Rapikan"**: ambil 500 karakter sebelum kursor, kirim ke Kiosapi
  (model `google/gemini-2.5-flash`), lalu ganti teks tsb dengan hasil yang
  sudah dirapikan ejaan/tata bahasanya.

## Ide pengembangan lanjut

- Prediksi kata real-time (bukan cuma tombol manual).
- Tombol "Ubah gaya" (formal/santai/singkat).
- Suggestion strip di atas keyboard (kayak Gboard) yang nampilin 3 opsi kata.
- Simpen histori "rapikan" biar bisa undo kalau hasil AI kurang pas.
