package com.kangasep.pos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Product(val id: Long, val name: String, val category: String, val price: Int)
data class ModifierOption(val name: String, val price: Int)
data class CartLine(
    val product: Product,
    var qty: Int = 1,
    val seasonings: MutableList<String> = mutableListOf(),
    var spiceLevel: String = "Tidak pedas",
    var topping: ModifierOption? = null
) {
    val unitPrice: Int get() = product.price + (topping?.price ?: 0)
}

class PosDatabase(context: Context) :
    SQLiteOpenHelper(context, "pos_cimol.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                pin_hash TEXT NOT NULL,
                role TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1
            )
        """)
        db.execSQL("""
            CREATE TABLE products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                price INTEGER NOT NULL,
                active INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE shifts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cashier TEXT NOT NULL,
                opened_at TEXT NOT NULL,
                closed_at TEXT,
                opening_cash INTEGER NOT NULL,
                physical_cash INTEGER,
                expected_cash INTEGER,
                difference INTEGER,
                status TEXT NOT NULL DEFAULT 'ACTIVE'
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE orders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_no TEXT NOT NULL,
                shift_id INTEGER NOT NULL,
                order_type TEXT NOT NULL,
                total INTEGER NOT NULL,
                payment_method TEXT NOT NULL,
                amount_received INTEGER NOT NULL,
                change_amount INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PAID',
                void_reason TEXT,
                void_by TEXT,
                created_at TEXT NOT NULL,
                FOREIGN KEY(shift_id) REFERENCES shifts(id)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE modifier_options(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                option_type TEXT NOT NULL,
                name TEXT NOT NULL,
                price INTEGER NOT NULL DEFAULT 0,
                active INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE order_item_options(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_item_id INTEGER NOT NULL,
                option_type TEXT NOT NULL,
                option_name_snapshot TEXT NOT NULL,
                option_price INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(order_item_id) REFERENCES order_items(id)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE order_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                product_id INTEGER,
                product_name_snapshot TEXT NOT NULL,
                unit_price INTEGER NOT NULL,
                qty INTEGER NOT NULL,
                subtotal INTEGER NOT NULL,
                FOREIGN KEY(order_id) REFERENCES orders(id)
            )
        """.trimIndent())

        db.execSQL("INSERT INTO users(username,pin_hash,role) VALUES('admin','${pinHash("1234")}','ADMIN')")
        db.execSQL("INSERT INTO users(username,pin_hash,role) VALUES('kasir','${pinHash("1111")}','CASHIER')")

        listOf(
            Triple("BUMBU", "Original", 0),
            Triple("BUMBU", "Balado", 0),
            Triple("BUMBU", "Keju", 0),
            Triple("BUMBU", "BBQ", 0),
            Triple("BUMBU", "Jagung Bakar", 0),
            Triple("TOPPING", "Keju parut", 3000),
            Triple("TOPPING", "Saus", 2000),
            Triple("TOPPING", "Sosis", 4000)
        ).forEach { (type, name, price) ->
            db.execSQL("INSERT INTO modifier_options(option_type,name,price) VALUES(?,?,?)",
                arrayOf(type, name, price))
        }

        val seed = listOf(
            Triple("Cimol Bojot Original", "Cimol", 10000),
            Triple("Cimol Bojot Pedas", "Cimol", 12000),
            Triple("Cimol Keju", "Cimol", 13000),
            Triple("Cimol Balado", "Cimol", 12000),
            Triple("Es Teh Manis", "Minuman", 5000),
            Triple("Paket Hemat", "Paket", 20000)
        )
        seed.forEach { (name, category, price) ->
            val values = ContentValues().apply {
                put("name", name); put("category", category); put("price", price)
            }
            db.insert("products", null, values)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS users(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    pin_hash TEXT NOT NULL,
                    role TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
            db.execSQL("INSERT OR IGNORE INTO users(username,pin_hash,role) VALUES('admin','${pinHash("1234")}','ADMIN')")
            db.execSQL("INSERT OR IGNORE INTO users(username,pin_hash,role) VALUES('kasir','${pinHash("1111")}','CASHIER')")
        }
        if (oldVersion < 3) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS modifier_options(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    option_type TEXT NOT NULL,
                    name TEXT NOT NULL,
                    price INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS order_item_options(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_item_id INTEGER NOT NULL,
                    option_type TEXT NOT NULL,
                    option_name_snapshot TEXT NOT NULL,
                    option_price INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(order_item_id) REFERENCES order_items(id)
                )
            """.trimIndent())
            val options = listOf(
                arrayOf("BUMBU", "Original", "0"),
                arrayOf("BUMBU", "Balado", "0"),
                arrayOf("BUMBU", "Keju", "0"),
                arrayOf("BUMBU", "BBQ", "0"),
                arrayOf("BUMBU", "Jagung Bakar", "0"),
                arrayOf("TOPPING", "Keju parut", "3000"),
                arrayOf("TOPPING", "Saus", "2000"),
                arrayOf("TOPPING", "Sosis", "4000")
            )
            options.forEach { db.execSQL(
                "INSERT INTO modifier_options(option_type,name,price) VALUES(?,?,?)", it
            ) }
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE orders ADD COLUMN void_reason TEXT")
            db.execSQL("ALTER TABLE orders ADD COLUMN void_by TEXT")
        }
    }


    fun authenticate(username: String, pin: String): String? {
        val hash = pinHash(pin)
        readableDatabase.rawQuery(
            "SELECT role FROM users WHERE username=? AND pin_hash=? AND active=1",
            arrayOf(username, hash)
        ).use { return if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun pinHash(pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun changePin(username: String, currentPin: String, newPin: String): Boolean {
        require(newPin.length in 4..8 && newPin.all { it.isDigit() }) { "PIN baru harus 4–8 angka." }
        val values = ContentValues().apply { put("pin_hash", pinHash(newPin)) }
        return writableDatabase.update(
            "users", values, "username=? AND pin_hash=? AND active=1",
            arrayOf(username, pinHash(currentPin))
        ) == 1
    }

    fun modifierOptions(type: String): List<ModifierOption> {
        val result = mutableListOf<ModifierOption>()
        readableDatabase.rawQuery(
            "SELECT name,price FROM modifier_options WHERE option_type=? AND active=1 ORDER BY id",
            arrayOf(type)
        ).use { c ->
            while (c.moveToNext()) result.add(ModifierOption(c.getString(0), c.getInt(1)))
        }
        return result
    }

    fun products(category: String? = null, keyword: String = ""): List<Product> {
        val result = mutableListOf<Product>()
        val sql = StringBuilder("SELECT id,name,category,price FROM products WHERE active=1")
        val args = mutableListOf<String>()
        if (!category.isNullOrBlank() && category != "Semua") {
            sql.append(" AND category=?"); args.add(category)
        }
        if (keyword.isNotBlank()) {
            sql.append(" AND name LIKE ?"); args.add("%$keyword%")
        }
        sql.append(" ORDER BY id")
        readableDatabase.rawQuery(sql.toString(), args.toTypedArray()).use { c ->
            while (c.moveToNext()) result.add(Product(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3)))
        }
        return result
    }

    fun addProduct(name: String, category: String, price: Int) {
        val v = ContentValues().apply {
            put("name", name); put("category", category); put("price", price); put("active", 1)
        }
        writableDatabase.insert("products", null, v)
    }

    fun activeShiftId(): Long? {
        readableDatabase.rawQuery("SELECT id FROM shifts WHERE status='ACTIVE' ORDER BY id DESC LIMIT 1", null).use {
            return if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    fun openShift(cashier: String, openingCash: Int): Long {
        val v = ContentValues().apply {
            put("cashier", cashier); put("opened_at", now()); put("opening_cash", openingCash)
            put("status", "ACTIVE")
        }
        return writableDatabase.insert("shifts", null, v)
    }

    fun closeShift(physicalCash: Int): Pair<Int, Int>? {
        val shiftId = activeShiftId() ?: return null
        val db = writableDatabase
        var opening = 0
        db.rawQuery("SELECT opening_cash FROM shifts WHERE id=?", arrayOf(shiftId.toString())).use {
            if (it.moveToFirst()) opening = it.getInt(0)
        }
        var cashSales = 0
        db.rawQuery(
            "SELECT COALESCE(SUM(total),0) FROM orders WHERE shift_id=? AND payment_method='Tunai' AND status='PAID'",
            arrayOf(shiftId.toString())
        ).use { if (it.moveToFirst()) cashSales = it.getInt(0) }
        val expected = opening + cashSales
        val difference = physicalCash - expected
        val v = ContentValues().apply {
            put("closed_at", now()); put("physical_cash", physicalCash)
            put("expected_cash", expected); put("difference", difference); put("status", "CLOSED")
        }
        db.update("shifts", v, "id=?", arrayOf(shiftId.toString()))
        return Pair(expected, difference)
    }

    fun saveOrder(lines: List<CartLine>, method: String, received: Int, orderType: String): Long {
        val shiftId = activeShiftId() ?: throw IllegalStateException("Buka shift terlebih dahulu.")
        val total = lines.sumOf { it.unitPrice * it.qty }
        require(lines.isNotEmpty()) { "Keranjang masih kosong." }
        if (method == "Tunai" && received < total) throw IllegalArgumentException("Uang diterima kurang.")
        val change = if (method == "Tunai") received - total else 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            val count = db.rawQuery(
                "SELECT COUNT(*) FROM orders WHERE date(created_at)=date('now','localtime')", null
            ).use { if (it.moveToFirst()) it.getInt(0) + 1 else 1 }
            val orderNo = "KA-${java.text.SimpleDateFormat("yyMMdd", java.util.Locale("id","ID")).format(java.util.Date())}-${count.toString().padStart(3,'0')}"
            val order = ContentValues().apply {
                put("order_no", orderNo); put("shift_id", shiftId); put("order_type", orderType)
                put("total", total); put("payment_method", method); put("amount_received", received)
                put("change_amount", change); put("created_at", now()); put("status", "PAID")
            }
            val orderId = db.insertOrThrow("orders", null, order)
            lines.forEach { line ->
                val item = ContentValues().apply {
                    put("order_id", orderId); put("product_id", line.product.id)
                    put("product_name_snapshot", line.product.name); put("unit_price", line.unitPrice)
                    put("qty", line.qty); put("subtotal", line.unitPrice * line.qty)
                }
                val itemId = db.insertOrThrow("order_items", null, item)
                line.seasonings.forEach { seasoning ->
                    val option = ContentValues().apply {
                        put("order_item_id", itemId); put("option_type", "BUMBU")
                        put("option_name_snapshot", seasoning); put("option_price", 0)
                    }
                    db.insertOrThrow("order_item_options", null, option)
                }
                val spice = ContentValues().apply {
                    put("order_item_id", itemId); put("option_type", "LEVEL_PEDAS")
                    put("option_name_snapshot", line.spiceLevel); put("option_price", 0)
                }
                db.insertOrThrow("order_item_options", null, spice)
                line.topping?.let { topping ->
                    val option = ContentValues().apply {
                        put("order_item_id", itemId); put("option_type", "TOPPING")
                        put("option_name_snapshot", topping.name); put("option_price", topping.price)
                    }
                    db.insertOrThrow("order_item_options", null, option)
                }
            }
            db.setTransactionSuccessful()
            return orderId
        } finally { db.endTransaction() }
    }


    fun updateProduct(id: Long, name: String, category: String, price: Int) {
        val v = ContentValues().apply { put("name", name); put("category", category); put("price", price) }
        writableDatabase.update("products", v, "id=?", arrayOf(id.toString()))
    }

    fun deactivateProduct(id: Long) {
        val v = ContentValues().apply { put("active", 0) }
        writableDatabase.update("products", v, "id=?", arrayOf(id.toString()))
    }

    fun productById(id: Long): Product? {
        readableDatabase.rawQuery("SELECT id,name,category,price FROM products WHERE id=?", arrayOf(id.toString())).use {
            return if (it.moveToFirst()) Product(it.getLong(0), it.getString(1), it.getString(2), it.getInt(3)) else null
        }
    }

    fun shiftSummary(): String {
        val shiftId = activeShiftId() ?: return "Tidak ada shift aktif."
        var cashier = ""; var opened = ""; var opening = 0
        readableDatabase.rawQuery("SELECT cashier,opened_at,opening_cash FROM shifts WHERE id=?", arrayOf(shiftId.toString())).use {
            if (it.moveToFirst()) { cashier = it.getString(0); opened = it.getString(1); opening = it.getInt(2) }
        }
        var cash = 0; var qris = 0; var transfer = 0; var count = 0
        readableDatabase.rawQuery("SELECT payment_method,COALESCE(SUM(total),0),COUNT(*) FROM orders WHERE shift_id=? AND status='PAID' GROUP BY payment_method", arrayOf(shiftId.toString())).use {
            while (it.moveToNext()) {
                when (it.getString(0)) { "Tunai" -> cash = it.getInt(1); "QRIS" -> qris = it.getInt(1); "Transfer" -> transfer = it.getInt(1) }
                count += it.getInt(2)
            }
        }
        return "Kasir: $cashier\nMulai: $opened\nModal awal: ${rupiah(opening)}\nTunai: ${rupiah(cash)}\nQRIS: ${rupiah(qris)}\nTransfer: ${rupiah(transfer)}\nTransaksi: $count\nKas diharapkan: ${rupiah(opening + cash)}"
    }

    fun reportSummary(): String {
        val totals = salesSummary()
        var cash = 0; var qris = 0; var transfer = 0
        readableDatabase.rawQuery("SELECT payment_method,COALESCE(SUM(total),0) FROM orders WHERE status='PAID' AND date(created_at)=date('now','localtime') GROUP BY payment_method", null).use {
            while (it.moveToNext()) when (it.getString(0)) {
                "Tunai" -> cash = it.getInt(1); "QRIS" -> qris = it.getInt(1); "Transfer" -> transfer = it.getInt(1)
            }
        }
        return "LAPORAN HARI INI\nOmzet: ${rupiah(totals.first)}\nTransaksi: ${totals.second}\n\nTunai: ${rupiah(cash)}\nQRIS: ${rupiah(qris)}\nTransfer: ${rupiah(transfer)}"
    }

    fun salesSummary(): Pair<Int, Int> {
        var total = 0; var count = 0
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(total),0),COUNT(*) FROM orders WHERE status='PAID' AND date(created_at)=date('now','localtime')", null
        ).use { if (it.moveToFirst()) { total = it.getInt(0); count = it.getInt(1) } }
        return Pair(total, count)
    }

    fun recentOrders(): List<String> {
        val rows = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT order_no,total,payment_method,created_at FROM orders ORDER BY id DESC LIMIT 30", null
        ).use { c ->
            while (c.moveToNext()) rows.add("${c.getString(0)} • ${rupiah(c.getInt(1))} • ${c.getString(2)} • ${c.getString(3)}")
        }
        return rows
    }

    fun dashboardSummary(): String {
        val today = reportSummary()
        val avg = readableDatabase.rawQuery("SELECT CASE WHEN COUNT(*)=0 THEN 0 ELSE SUM(total)/COUNT(*) END FROM orders WHERE status='PAID' AND date(created_at)=date('now','localtime')", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val units = readableDatabase.rawQuery("SELECT COALESCE(SUM(oi.qty),0) FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.status='PAID' AND date(o.created_at)=date('now','localtime')", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val top = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT oi.product_name_snapshot,SUM(oi.qty) qty FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.status='PAID' AND date(o.created_at)=date('now','localtime') GROUP BY oi.product_name_snapshot ORDER BY qty DESC LIMIT 5", null).use { c -> while(c.moveToNext()) top.add("${c.getString(0)} — ${c.getInt(1)} terjual") }
        return "$today\nRata-rata transaksi: ${rupiah(avg)}\nItem terjual: $units\n\nTOP PRODUK\n${if(top.isEmpty()) "Belum ada penjualan" else top.joinToString("\n")}"
    }

    fun orderDetail(orderNo: String): String {
        val out = StringBuilder()
        readableDatabase.rawQuery("SELECT id,total,payment_method,amount_received,change_amount,created_at,status,order_type,void_reason FROM orders WHERE order_no=?", arrayOf(orderNo)).use { c ->
            if (!c.moveToFirst()) return "Transaksi tidak ditemukan."
            val id=c.getLong(0)
            out.append("$orderNo\n${c.getString(5)}\n${c.getString(7)}\nStatus: ${c.getString(6)}\n\n")
            readableDatabase.rawQuery("SELECT id,product_name_snapshot,unit_price,qty,subtotal FROM order_items WHERE order_id=?", arrayOf(id.toString())).use { items ->
                while(items.moveToNext()) {
                    out.append("${items.getString(1)} x${items.getInt(3)} — ${rupiah(items.getInt(4))}\n")
                    readableDatabase.rawQuery("SELECT option_type,option_name_snapshot,option_price FROM order_item_options WHERE order_item_id=?", arrayOf(items.getLong(0).toString())).use { opts ->
                        while(opts.moveToNext()) out.append("  • ${opts.getString(1)}${if(opts.getInt(2)>0) " (+${rupiah(opts.getInt(2))})" else ""}\n")
                    }
                }
            }
            out.append("\nTotal: ${rupiah(c.getInt(1))}\nMetode: ${c.getString(2)}\nDiterima: ${rupiah(c.getInt(3))}\nKembalian: ${rupiah(c.getInt(4))}")
            if (!c.isNull(8)) out.append("\nAlasan void: ${c.getString(8)}")
        }
        return out.toString()
    }

    fun voidOrder(orderNo: String, reason: String, admin: String): Boolean {
        val v = ContentValues().apply { put("status", "VOID"); put("void_reason", reason); put("void_by", admin) }
        return writableDatabase.update("orders", v, "order_no=? AND status='PAID'", arrayOf(orderNo)) > 0
    }

    fun allRecentOrderNumbers(): List<String> {
        val rows= mutableListOf<String>()
        readableDatabase.rawQuery("SELECT order_no FROM orders ORDER BY id DESC LIMIT 50", null).use { c -> while(c.moveToNext()) rows.add(c.getString(0)) }
        return rows
    }

    fun saveModifier(type: String, name: String, price: Int) {
        val v=ContentValues().apply { put("option_type",type); put("name",name); put("price",price); put("active",1) }
        writableDatabase.insert("modifier_options",null,v)
    }

    fun deactivateModifier(type: String, name: String) {
        val v=ContentValues().apply { put("active",0) }
        writableDatabase.update("modifier_options",v,"option_type=? AND name=?", arrayOf(type,name))
    }

    private fun now() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
}

fun rupiah(value: Int): String = "Rp " + String.format(java.util.Locale("id", "ID"), "%,d", value).replace(",", ".")
