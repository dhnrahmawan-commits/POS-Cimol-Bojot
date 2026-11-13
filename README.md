# POS Cimol Bojot Kang Asep — Android + SQLite

Starter project native Android menggunakan Kotlin dan SQLiteOpenHelper (tanpa backend/internet).

## Cara menjalankan
1. Ekstrak ZIP.
2. Buka folder `POS_Cimol_Bojot_Android` melalui Android Studio.
3. Gunakan JDK 17 dan Android Gradle Plugin 8.7.3.
4. Tunggu Gradle Sync, lalu jalankan pada emulator/perangkat Android (minimum Android 6 / API 23).

## Yang sudah ada
- Daftar menu awal sesuai mockup (Original, Pedas, Keju, Balado, Es Teh Manis, Paket Hemat).
- Pencarian dan filter kategori.
- Keranjang dan perhitungan total.
- Pembayaran Tunai, QRIS, Transfer; uang diterima dan kembalian Tunai.
- Shift buka/tutup pada lapisan database.
- Penyimpanan transaksi dan item transaksi ke SQLite.
- Snapshot nama dan harga produk pada item transaksi.
- Ringkasan omzet dan jumlah transaksi hari ini serta daftar riwayat pada lapisan database.

## Catatan penting
Ini adalah **starter MVP**, bukan aplikasi produksi final. UI utama kasir dan alur simpan transaksi sudah dibuat. Beberapa bagian PRD masih perlu dikembangkan sebelum dipakai operasional:
- Halaman admin lengkap untuk CRUD produk/varian/topping.
- Halaman tutup shift yang memanggil `closeShift()` dan laporan JPG.
- Halaman dashboard, laporan periode, riwayat/detail transaksi dan void dengan PIN admin.
- Cetak printer Bluetooth ESC/POS 58 mm.
- QRIS masih konfirmasi manual; aplikasi tidak memverifikasi pembayaran otomatis.
- Login PIN dan pembatasan role perlu ditambahkan.
- Migrasi database dan backup/restore perlu dibuat sebelum rilis.

## Database
File database bernama `pos_cimol.db`. Tabel: `products`, `shifts`, `orders`, `order_items`.
