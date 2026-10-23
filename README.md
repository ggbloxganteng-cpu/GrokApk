# Grok Build — Floating AI Assistant (MVP)

**Package:** `com.mercury3.assistant`  
**Min SDK:** 23 | **Target:** 34  
**Model:** Mercury 3 (Inception) — Bring Your Own Key

## Fitur
- Floating bubble (draggable + snap)
- Foreground service + notifikasi
- Chat streaming (SSE) ke Mercury 3
- API key terenkripsi
- Mode Privasi
- Input konteks layar manual
- Copy jawaban

## Build dengan GitHub Actions (paling mudah dari HP)

1. Buat repository baru di GitHub (misalnya `GrokBuild`)
2. Upload **semua isi folder ini** ke repository (bisa pakai GitHub mobile / browser)
3. Tunggu Actions selesai (tab **Actions**)
4. Download artifact **GrokBuild-APK**
5. Install APK di HP (aktifkan "Install dari sumber tidak dikenal")

Atau tekan tombol **Run workflow** di tab Actions kalau ingin build ulang manual.

## Build lokal (laptop)
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Setup di HP setelah install
1. Buka app → masukkan API Key Mercury 3
2. Berikan izin **Tampilkan di atas aplikasi lain**
3. Tap ikon bubble di toolbar untuk mengaktifkan floating bubble
