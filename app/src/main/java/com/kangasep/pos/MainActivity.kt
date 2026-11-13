package com.kangasep.pos

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class MainActivity : Activity() {
    private lateinit var db: PosDatabase
    private val cart = mutableListOf<CartLine>()
    private var category = "Semua"
    private var keyword = ""
    private var currentRole = "CASHIER"
    private var currentUser = "kasir"
    private lateinit var list: LinearLayout
    private lateinit var totalLabel: TextView
    private lateinit var countLabel: TextView
    private val maroon = Color.rgb(142, 12, 31)
    private val red = Color.rgb(200, 16, 46)
    private val cream = Color.rgb(255, 246, 234)
    private val peach = Color.rgb(255, 227, 211)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = PosDatabase(this)
        showLogin()
    }


    private fun showLogin() {
        val user = EditText(this).apply { hint = "Username"; setText("kasir") }
        val pin = EditText(this).apply {
            hint = "PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0)
            addView(user); addView(pin)
        }
        AlertDialog.Builder(this).setTitle("Login POS Cimol")
            .setMessage("Masukkan username dan PIN")
            .setView(box).setCancelable(false)
            .setPositiveButton("Masuk") { _, _ ->
                val name = user.text.toString().trim()
                val role = db.authenticate(name, pin.text.toString())
                if (role == null) {
                    toast("Username atau PIN salah")
                    showLogin()
                } else {
                    currentUser = name; currentRole = role
                    if (db.activeShiftId() == null) showOpenShift()
                    render()
                }
            }.show()
    }

    private fun render() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(cream)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(20, 22, 20, 16); setBackgroundColor(maroon)
        }
        header.addView(label("KA  Cimol Bojot Kang Asep", 23, Color.WHITE, true))
        header.addView(label("Pesanan • Dibawa pulang", 14, Color.WHITE, false))
        val search = EditText(this).apply {
            hint = "Cari menu..."; isSingleLine = true; textSize = 16f
            setPadding(16, 8, 16, 8); setBackground(round(Color.WHITE, 18))
            setText(keyword)
        }
        search.addTextChangedListener(SimpleWatcher { keyword = it; refreshProducts() })
        val searchLp = LinearLayout.LayoutParams(-1, 52).apply { topMargin = 14 }
        header.addView(search, searchLp)
        page.addView(header)

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(12, 12, 12, 8) }
        listOf("Semua", "Cimol", "Topping", "Minuman", "Paket").forEach { c ->
            val b = Button(this).apply {
                text = c; textSize = 12f; isAllCaps = false
                setTextColor(if (category == c) Color.WHITE else maroon)
                background = round(if (category == c) red else Color.WHITE, 24)
                setOnClickListener { category = c; refreshProducts(); render() }
            }
            chips.addView(b, LinearLayout.LayoutParams(0, 46, 1f).apply { setMargins(3,0,3,0) })
        }
        page.addView(chips)

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 2, 8, 6)
        }
        listOf("Admin", "Opsi", "Shift", "Dashboard", "Riwayat", "JPG", "Backup", "Restore", "PIN").forEach { action ->
            val b = Button(this).apply {
                text = action; isAllCaps = false; textSize = 11f
                setTextColor(maroon); background = round(peach, 16)
                setOnClickListener {
                    when (action) {
                        "Admin" -> showAdmin()
                        "Opsi" -> showModifierAdmin()
                        "Shift" -> showShift()
                        "Dashboard" -> showDashboard()
                        "Riwayat" -> showHistory()
                        "JPG" -> exportReportJpg()
                        "Backup" -> backupDatabase()
                        "Restore" -> restoreDatabase()
                        "PIN" -> changePinDialog()
                    }
                }
            }
            tools.addView(b, LinearLayout.LayoutParams(88, 42).apply { setMargins(3,0,3,0) })
        }
        val toolsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tools)
        }
        page.addView(toolsScroll)

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 4, 16, 12) }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 12, 18, 12); setBackgroundColor(maroon)
        }
        val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        countLabel = label("0 item", 14, Color.WHITE, false)
        totalLabel = label(rupiah(0), 24, 0xFFFFB400.toInt(), true)
        summary.addView(countLabel); summary.addView(totalLabel)
        footer.addView(summary, LinearLayout.LayoutParams(0, -2, 1f))
        val pay = Button(this).apply {
            text = "Bayar"; isAllCaps = false; textSize = 18f; setTextColor(Color.WHITE)
            background = round(red, 18); setOnClickListener { showPayment() }
        }
        footer.addView(pay, LinearLayout.LayoutParams(120, 58))
        page.addView(footer)
        setContentView(page)
        refreshProducts()
        refreshCart()
    }

    private fun refreshProducts() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        db.products(category, keyword).forEach { p ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 12, 12, 12); background = round(Color.WHITE, 18)
            }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(label(p.name, 17, Color.rgb(45, 15, 20), true))
            info.addView(label(rupiah(p.price), 15, maroon, true))
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val plus = Button(this).apply {
                text = "+"; textSize = 24f; setTextColor(Color.WHITE); background = round(red, 14)
                setOnClickListener {
                    if (p.category.equals("Cimol", ignoreCase = true)) {
                        showCustomization(p)
                    } else {
                        cart.add(CartLine(p))
                        refreshCart()
                        toast("${p.name} ditambahkan")
                    }
                }
                setOnLongClickListener {
                    val matches = cart.filter { it.product.id == p.id }
                    if (matches.isNotEmpty()) {
                        val labels = matches.map { line ->
                            "${line.product.name} • ${line.seasonings.joinToString(", ")} • ${line.spiceLevel} • ${line.topping?.name ?: "Tanpa topping"} • Qty ${line.qty}"
                        }.toTypedArray()
                        AlertDialog.Builder(this@MainActivity).setTitle("Item keranjang")
                            .setItems(labels) { _, index ->
                                val selected = matches[index]
                                AlertDialog.Builder(this@MainActivity).setTitle("Kelola item")
                                    .setItems(arrayOf("Tambah jumlah", "Kurangi jumlah", "Hapus item")) { _, action ->
                                        when (action) {
                                            0 -> selected.qty++
                                            1 -> { selected.qty--; if (selected.qty <= 0) cart.remove(selected) }
                                            2 -> cart.remove(selected)
                                        }
                                        refreshCart()
                                    }.show()
                            }.show()
                    } else toast("Produk belum ada di keranjang")
                    true
                }
            }
            row.addView(plus, LinearLayout.LayoutParams(58, 54))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
    }


    private fun showCustomization(product: Product) {
        val seasonings = db.modifierOptions("BUMBU")
        val toppings = db.modifierOptions("TOPPING")
        val selectedSeasonings = mutableListOf<String>()
        val spiceLevels = arrayOf("Tidak pedas", "Level 1", "Level 2", "Level 3", "Level 4", "Level 5")
        val selectedSpice = arrayOf("Tidak pedas")

        if (seasonings.isEmpty()) {
            toast("Pilihan bumbu belum diatur di database")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("1/3 • Pilih bumbu (boleh beberapa)")
            .setMultiChoiceItems(seasonings.map { it.name }.toTypedArray(), null) { _, which, checked ->
                val name = seasonings[which].name
                if (checked && !selectedSeasonings.contains(name)) selectedSeasonings.add(name)
                if (!checked) selectedSeasonings.remove(name)
            }
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjut") { _, _ ->
                if (selectedSeasonings.isEmpty()) {
                    toast("Pilih minimal satu bumbu")
                    showCustomization(product)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("2/3 • Pilih level pedas")
                        .setSingleChoiceItems(spiceLevels, 0) { _, which ->
                            selectedSpice[0] = spiceLevels[which]
                        }
                        .setNegativeButton("Kembali") { _, _ -> showCustomization(product) }
                        .setPositiveButton("Lanjut") { _, _ ->
                            val toppingLabels = arrayOf("Tanpa topping") +
                                toppings.map { "${it.name} (+${rupiah(it.price)})" }
                            val selectedToppingIndex = intArrayOf(0)
                            AlertDialog.Builder(this)
                                .setTitle("3/3 • Pilih satu topping")
                                .setSingleChoiceItems(toppingLabels, 0) { _, which -> selectedToppingIndex[0] = which }
                                .setNegativeButton("Kembali") { _, _ ->
                                    showSpiceAgain(product, selectedSeasonings, selectedSpice[0], toppings)
                                }
                                .setPositiveButton("Masukkan keranjang") { _, _ ->
                                    val chosen = selectedToppingIndex[0]
                                    val topping = if (chosen <= 0) null else toppings.getOrNull(chosen - 1)
                                    cart.add(CartLine(product, 1, selectedSeasonings.toMutableList(), selectedSpice[0], topping))
                                    refreshCart()
                                    toast("${product.name} ditambahkan ke keranjang")
                                }.show()
                        }.show()
                }
            }.show()
    }

    private fun showSpiceAgain(
        product: Product,
        seasonings: List<String>,
        initialSpice: String,
        toppings: List<ModifierOption>
    ) {
        val levels = arrayOf("Tidak pedas", "Level 1", "Level 2", "Level 3", "Level 4", "Level 5")
        val selected = arrayOf(initialSpice)
        AlertDialog.Builder(this).setTitle("2/3 • Pilih level pedas")
            .setSingleChoiceItems(levels, levels.indexOf(initialSpice).coerceAtLeast(0)) { _, which -> selected[0] = levels[which] }
            .setNegativeButton("Kembali") { _, _ -> showCustomization(product) }
            .setPositiveButton("Lanjut") { _, _ ->
                val labels = arrayOf("Tanpa topping") + toppings.map { "${it.name} (+${rupiah(it.price)})" }
                val selectedToppingIndex = intArrayOf(0)
                AlertDialog.Builder(this).setTitle("3/3 • Pilih satu topping")
                    .setSingleChoiceItems(labels, 0) { _, which -> selectedToppingIndex[0] = which }
                    .setNegativeButton("Kembali") { _, _ -> showSpiceAgain(product, seasonings, selected[0], toppings) }
                    .setPositiveButton("Masukkan keranjang") { _, _ ->
                        val chosen = selectedToppingIndex[0]
                        val topping = if (chosen <= 0) null else toppings.getOrNull(chosen - 1)
                        cart.add(CartLine(product, 1, seasonings.toMutableList(), selected[0], topping))
                        refreshCart()
                        toast("${product.name} ditambahkan ke keranjang")
                    }.show()
            }.show()
    }

    private fun refreshCart() {
        if (!::totalLabel.isInitialized) return
        val qty = cart.sumOf { it.qty }
        val total = cart.sumOf { it.unitPrice * it.qty }
        countLabel.text = "$qty item"
        totalLabel.text = rupiah(total)
    }

    private fun showPayment() {
        if (cart.isEmpty()) { toast("Keranjang masih kosong"); return }
        if (db.activeShiftId() == null) { showOpenShift(); return }
        val total = cart.sumOf { it.unitPrice * it.qty }
        val methods = arrayOf("Tunai", "QRIS", "Transfer")
        var method = "Tunai"
        val amount = EditText(this).apply {
            hint = "Uang diterima (contoh: 50000)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(total.toString())
        }
        val type = arrayOf("Dibawa pulang", "Makan di tempat")
        var orderType = type[0]
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(22, 8, 22, 0)
            addView(label("Total: ${rupiah(total)}", 20, maroon, true))
            cart.forEach { line ->
                val detail = "${line.product.name} x${line.qty} • Bumbu: ${line.seasonings.joinToString(", ")} • ${line.spiceLevel} • ${line.topping?.name ?: "Tanpa topping"}"
                addView(label(detail, 11, Color.DKGRAY, false))
            }
            val methodSpinner = Spinner(this@MainActivity)
            methodSpinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, methods)
            methodSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { method = methods[position] }
            }
            addView(methodSpinner)
            addView(amount)
            val typeSpinner = Spinner(this@MainActivity)
            typeSpinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, type)
            typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { orderType = type[position] }
            }
            addView(typeSpinner)
            addView(label("QRIS/Transfer: kasir wajib memastikan pembayaran diterima.", 12, Color.DKGRAY, false))
        }
        AlertDialog.Builder(this).setTitle("Pembayaran").setView(content)
            .setNegativeButton("Kembali", null)
            .setPositiveButton("Selesaikan") { _, _ ->
                val received = if (method == "Tunai") amount.text.toString().toIntOrNull() ?: 0 else total
                try {
                    db.saveOrder(cart, method, received, orderType)
                    val change = if (method == "Tunai") received - total else 0
                    cart.clear(); refreshCart()
                    AlertDialog.Builder(this).setTitle("Transaksi berhasil")
                        .setMessage("Total: ${rupiah(total)}\nMetode: $method\nKembalian: ${rupiah(change)}\n\nStruk digital tersimpan di riwayat.")
                        .setPositiveButton("OK", null).show()
                } catch (e: Exception) { toast(e.message ?: "Transaksi gagal") }
            }.show()
    }

    private fun showOpenShift() {
        val input = EditText(this).apply {
            hint = "Modal awal kas (angka)"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        val cashier = EditText(this).apply { hint = "Nama kasir" }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0)
            addView(cashier); addView(input)
        }
        AlertDialog.Builder(this).setTitle("Buka Shift").setView(box)
            .setCancelable(false)
            .setPositiveButton("Mulai Shift") { _, _ ->
                val name = cashier.text.toString().ifBlank { currentUser }
                val cash = input.text.toString().toIntOrNull() ?: 0
                db.openShift(name, cash)
                toast("Shift berhasil dibuka")
            }.show()
    }


    private fun showAdmin() {
        if (currentRole != "ADMIN") { toast("Fitur ini hanya untuk admin"); return }
        val products = db.products()
        val names = products.map { "${it.name} — ${rupiah(it.price)}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Admin • Produk")
            .setItems(names + "＋ Tambah produk") { _, which ->
                if (which == products.size) productForm(null)
                else {
                    val p = products[which]
                    AlertDialog.Builder(this).setTitle(p.name)
                        .setItems(arrayOf("Edit produk", "Nonaktifkan produk")) { _, action ->
                            if (action == 0) productForm(p) else {
                                AlertDialog.Builder(this).setMessage("Nonaktifkan ${p.name}?")
                                    .setNegativeButton("Batal", null)
                                    .setPositiveButton("Nonaktifkan") { _, _ ->
                                        db.deactivateProduct(p.id); refreshProducts(); toast("Produk dinonaktifkan")
                                    }.show()
                            }
                        }.show()
                }
            }.setNegativeButton("Tutup", null).show()
    }

    private fun productForm(product: Product?) {
        val name = EditText(this).apply { hint = "Nama produk"; setText(product?.name ?: "") }
        val price = EditText(this).apply {
            hint = "Harga (angka)"; inputType = InputType.TYPE_CLASS_NUMBER
            setText(product?.price?.toString() ?: "")
        }
        val categories = arrayOf("Cimol", "Topping", "Minuman", "Paket")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            val index = categories.indexOf(product?.category ?: "Cimol")
            setSelection(if (index >= 0) index else 0)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0)
            addView(name); addView(spinner); addView(price)
        }
        AlertDialog.Builder(this).setTitle(if (product == null) "Tambah Produk" else "Edit Produk")
            .setView(box).setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                val n = name.text.toString().trim()
                val p = price.text.toString().toIntOrNull()
                if (n.isBlank() || p == null || p < 0) { toast("Nama dan harga harus diisi dengan benar"); return@setPositiveButton }
                if (product == null) db.addProduct(n, spinner.selectedItem.toString(), p)
                else db.updateProduct(product.id, n, spinner.selectedItem.toString(), p)
                refreshProducts(); toast("Produk berhasil disimpan")
            }.show()
    }

    private fun showShift() {
        if (db.activeShiftId() == null) { showOpenShift(); return }
        AlertDialog.Builder(this).setTitle("Shift Aktif")
            .setMessage(db.shiftSummary())
            .setPositiveButton("Tutup Shift") { _, _ -> closeShiftDialog() }
            .setNegativeButton("Kembali", null).show()
    }

    private fun closeShiftDialog() {
        val input = EditText(this).apply {
            hint = "Uang fisik di laci (angka)"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this).setTitle("Konfirmasi Tutup Shift").setMessage(db.shiftSummary())
            .setView(input).setNegativeButton("Batal", null)
            .setPositiveButton("Tutup Permanen") { _, _ ->
                val physical = input.text.toString().toIntOrNull()
                if (physical == null) { toast("Masukkan jumlah kas fisik"); return@setPositiveButton }
                val result = db.closeShift(physical)
                if (result != null) AlertDialog.Builder(this).setTitle("Shift ditutup")
                    .setMessage("Kas diharapkan: ${rupiah(result.first)}\nSelisih kas: ${rupiah(result.second)}")
                    .setPositiveButton("Buka shift baru") { _, _ -> showOpenShift() }
                    .setNegativeButton("Selesai", null).show()
            }.show()
    }

    private fun showDashboard() {
        if (currentRole != "ADMIN") { toast("Dashboard lengkap hanya untuk admin"); return }
        AlertDialog.Builder(this).setTitle("Dashboard Penjualan")
            .setMessage(db.dashboardSummary())
            .setPositiveButton("Tutup", null).show()
    }

    private fun showReport() = showDashboard()

    private fun showHistory() {
        if (currentRole != "ADMIN") { toast("Riwayat lengkap hanya untuk admin"); return }
        val orders = db.recentOrders()
        val numbers = db.allRecentOrderNumbers()
        AlertDialog.Builder(this).setTitle("Riwayat Transaksi • 50 terakhir")
            .setItems(if (orders.isEmpty()) arrayOf("Belum ada transaksi") else orders.toTypedArray()) { _, which ->
                if (which < numbers.size) showOrderDetail(numbers[which])
            }.setPositiveButton("Tutup", null).show()
    }

    private fun showOrderDetail(orderNo: String) {
        val actions = if (currentRole == "ADMIN") arrayOf("Tutup", "Void transaksi") else arrayOf("Tutup")
        AlertDialog.Builder(this).setTitle("Detail $orderNo")
            .setMessage(db.orderDetail(orderNo))
            .setItems(actions) { _, which -> if (currentRole == "ADMIN" && which == 1) confirmVoid(orderNo) }
            .setPositiveButton("Selesai", null).show()
    }

    private fun confirmVoid(orderNo: String) {
        val reason = EditText(this).apply { hint = "Alasan pembatalan wajib diisi" }
        AlertDialog.Builder(this).setTitle("Void transaksi")
            .setMessage("Transaksi yang di-void tidak dihitung sebagai omzet.")
            .setView(reason).setNegativeButton("Batal", null)
            .setPositiveButton("Konfirmasi void") { _, _ ->
                val why = reason.text.toString().trim()
                if (why.isBlank()) { toast("Alasan void wajib diisi"); return@setPositiveButton }
                if (db.voidOrder(orderNo, why, currentUser)) toast("Transaksi berhasil di-void") else toast("Transaksi tidak dapat di-void")
            }.show()
    }

    private fun showModifierAdmin() {
        if (currentRole != "ADMIN") { toast("Fitur ini hanya untuk admin"); return }
        val types = arrayOf("BUMBU", "TOPPING")
        AlertDialog.Builder(this).setTitle("Kelola Bumbu & Topping")
            .setItems(arrayOf("Tambah bumbu gratis", "Tambah topping berbayar", "Nonaktifkan bumbu", "Nonaktifkan topping")) { _, which ->
                if (which == 0 || which == 1) {
                    val name = EditText(this).apply { hint = "Nama pilihan" }
                    val price = EditText(this).apply { hint = "Harga tambahan (angka)"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if(which==0) "0" else "") }
                    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,8,24,0); addView(name); addView(price) }
                    AlertDialog.Builder(this).setTitle("Tambah pilihan").setView(box)
                        .setNegativeButton("Batal",null).setPositiveButton("Simpan") { _, _ ->
                            val n=name.text.toString().trim(); val p=price.text.toString().toIntOrNull()
                            if(n.isBlank() || p==null || p<0 || (which==0 && p!=0)) { toast("Periksa nama dan harga"); return@setPositiveButton }
                            db.saveModifier(types[if(which==0) 0 else 1],n,p); toast("Pilihan disimpan")
                        }.show()
                } else {
                    val type=types[if(which==2) 0 else 1]
                    val opts=db.modifierOptions(type)
                    AlertDialog.Builder(this).setTitle("Pilih untuk dinonaktifkan")
                        .setItems(opts.map { it.name }.toTypedArray()) { _, index ->
                            db.deactivateModifier(type,opts[index].name); toast("Pilihan dinonaktifkan")
                        }.setNegativeButton("Batal",null).show()
                }
            }.setNegativeButton("Tutup",null).show()
    }


    private fun changePinDialog() {
        val current = EditText(this).apply {
            hint = "PIN saat ini"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val next = EditText(this).apply {
            hint = "PIN baru (4–8 angka)"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val confirm = EditText(this).apply {
            hint = "Ulangi PIN baru"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0)
            addView(current); addView(next); addView(confirm)
        }
        AlertDialog.Builder(this).setTitle("Ganti PIN • $currentUser").setView(box)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                val oldPin = current.text.toString()
                val newPin = next.text.toString()
                if (newPin != confirm.text.toString()) { toast("Konfirmasi PIN tidak sama"); return@setPositiveButton }
                try {
                    if (db.changePin(currentUser, oldPin, newPin)) toast("PIN berhasil diganti")
                    else toast("PIN saat ini salah atau PIN baru tidak valid")
                } catch (e: Exception) { toast(e.message ?: "Gagal mengganti PIN") }
            }.show()
    }

    private fun exportReportJpg() {
        val width = 900
        val lines = db.reportSummary().split("\n")
        val height = 180 + lines.size * 58
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(cream)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = maroon; textSize = 42f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        canvas.drawText("CIMOL BOJOT KANG ASEP", 48f, 75f, paint)
        paint.textSize = 30f
        var y = 145f
        lines.forEach { line ->
            canvas.drawText(line, 48f, y, paint)
            y += 58f
        }
        val filename = "Laporan_Cimol_${System.currentTimeMillis()}.jpg"
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/POS Cimol")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Tidak dapat membuat file JPG")
            contentResolver.openOutputStream(uri).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out!!)
            }
            toast("Laporan JPG tersimpan di Galeri/Pictures/POS Cimol")
        } catch (e: Exception) { toast("Gagal menyimpan JPG: ${e.message}") }
        finally { bitmap.recycle() }
    }

    private fun backupDatabase() {
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(android.content.Intent.EXTRA_TITLE, "backup_pos_cimol.db")
        }
        try { startActivityForResult(intent, 701) }
        catch (e: Exception) { toast("Tidak dapat membuka penyimpanan file") }
    }

    private fun restoreDatabase() {
        AlertDialog.Builder(this)
            .setTitle("Pulihkan database")
            .setMessage("Pemulihan akan mengganti seluruh data aplikasi dengan isi file backup. Buat backup terbaru sebelum melanjutkan.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Pilih file backup") { _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                try { startActivityForResult(intent, 702) }
                catch (e: Exception) { toast("Tidak dapat membuka pemilih file") }
            }.show()
    }

    @Deprecated("Deprecated API retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            701 -> try {
                db.close()
                val source = getDatabasePath("pos_cimol.db")
                contentResolver.openOutputStream(uri).use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output!!) }
                }
                db = PosDatabase(this)
                toast("Backup database berhasil disimpan")
            } catch (e: Exception) {
                db = PosDatabase(this)
                toast("Backup gagal: ${e.message}")
            }
            702 -> {
                val temp = File(cacheDir, "restore_pos_cimol.db")
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "File backup tidak dapat dibaca" }
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    val check = android.database.sqlite.SQLiteDatabase.openDatabase(
                        temp.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                    )
                    try {
                        val tables = mutableSetOf<String>()
                        check.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                            while (c.moveToNext()) tables.add(c.getString(0))
                        }
                        require(tables.containsAll(setOf("users", "products", "orders", "order_items", "shifts"))) {
                            "File bukan backup POS Cimol yang valid"
                        }
                    } finally { check.close() }
                    db.close()
                    val target = getDatabasePath("pos_cimol.db")
                    File(target.path + "-wal").delete()
                    File(target.path + "-shm").delete()
                    temp.copyTo(target, overwrite = true)
                    db = PosDatabase(this)
                    db.readableDatabase // trigger schema check before returning to UI
                    toast("Backup berhasil dipulihkan")
                    render()
                } catch (e: Exception) {
                    db = PosDatabase(this)
                    toast("Pemulihan gagal: ${e.message}")
                } finally { temp.delete() }
            }
        }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(2, 4, 2, 4)
    }

    private fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

private class SimpleWatcher(val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString() ?: "") }
    override fun afterTextChanged(s: android.text.Editable?) {}
}
