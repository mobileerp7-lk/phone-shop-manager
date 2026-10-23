package app.phoneshop.manager.data

/** Ready-made categories and questions. The owner can change them later (Settings). */
object SeedData {
    val accounts = listOf(
        Account("acc_cash", "Cash drawer", "CASH", "", 0L, 0),
        Account("acc_bank", "Bank account", "BANK", "", 0L, 1),
        Account("acc_wallet", "Mobile wallet", "WALLET", "", 0L, 2),
    )

    private fun t(id: String, name: String, kind: String, order: Int) = EntryType("type_$id", name, kind, order)

    val entryTypes = listOf(
        t("rent", "Rent", "EXPENSE", 0),
        t("salary", "Salary", "EXPENSE", 1),
        t("electricity", "Electricity and water", "EXPENSE", 2),
        t("tea", "Tea and snacks", "EXPENSE", 3),
        t("transport", "Transport", "EXPENSE", 4),
        t("other_exp", "Other expense", "EXPENSE", 5),
        t("commission", "Commission", "INCOME", 0),
        t("other_inc", "Other income", "INCOME", 1),
    )

    val categories = listOf(
        Category("cat_phone", "Phone", tracksImei = true, hasWarranty = true, sortOrder = 0),
        Category("cat_accessory", "Accessory", tracksImei = false, hasWarranty = false, sortOrder = 1),
        Category("cat_spare", "Spare part", tracksImei = false, hasWarranty = false, sortOrder = 2),
        Category("cat_other", "Other", tracksImei = false, hasWarranty = false, sortOrder = 3),
    )

    private fun f(
        cat: String,
        key: String,
        label: String,
        type: String,
        order: Int,
        options: String = "",
        required: Boolean = false,
    ) = CategoryField("f_${cat}_$key", "cat_$cat", label, type, options, required, order)

    val fields = listOf(
        f("phone", "brand", "Brand", "CHOICE", 0,
            "Samsung|Apple|Xiaomi|Redmi|Oppo|Vivo|Realme|Huawei|Nokia|Infinix|Tecno|Other", true),
        f("phone", "model", "Model", "TEXT", 1, required = true),
        f("phone", "storage", "Storage", "CHOICE", 2, "16 GB|32 GB|64 GB|128 GB|256 GB|512 GB|1 TB"),
        f("phone", "ram", "RAM", "CHOICE", 3, "2 GB|3 GB|4 GB|6 GB|8 GB|12 GB|16 GB"),
        f("phone", "colour", "Colour", "TEXT", 4),
        f("phone", "condition", "Condition", "CHOICE", 5, "New|Used|Refurbished", true),
        f("phone", "box", "Box and charger included", "YESNO", 6),
        f("phone", "battery", "Battery health %", "NUMBER", 7),

        f("accessory", "type", "Type", "CHOICE", 0,
            "Charger|Cable|Cover|Screen guard|Earphones|Power bank|Memory card|Other", true),
        f("accessory", "brand", "Brand", "TEXT", 1),
        f("accessory", "models", "Compatible models", "TEXT", 2),
        f("accessory", "colour", "Colour", "TEXT", 3),

        f("spare", "part", "Part", "CHOICE", 0,
            "Display|Battery|Charging port|Speaker|Camera|Back cover|Other", true),
        f("spare", "models", "Compatible models", "TEXT", 1, required = true),
        f("spare", "quality", "Quality", "CHOICE", 2, "Original|Copy|Refurbished"),

        f("other", "notes", "Notes", "TEXT", 0),
    )
}
