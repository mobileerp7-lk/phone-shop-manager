package app.phoneshop.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phoneshop.manager.data.Category
import app.phoneshop.manager.data.CategoryField
import app.phoneshop.manager.data.Repository
import kotlinx.coroutines.launch

private val typeNames = listOf("Text", "Number", "Choice (pick from a list)", "Yes / No")
private val typeCodes = listOf("TEXT", "NUMBER", "CHOICE", "YESNO")

/** Lets any shop change what is asked when adding a product, without new code. */
@Composable
fun CategoriesScreen(repo: Repository, onBack: () -> Unit) {
    val categories by repo.categories.collectAsState(initial = emptyList())
    val allFields by repo.fields.collectAsState(initial = emptyList())
    val products by repo.products.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && categories.none { it.id == selectedId }) selectedId = categories.first().id
    }
    val category = categories.firstOrNull { it.id == selectedId }
    val fields = allFields.filter { it.categoryId == selectedId }.sortedBy { it.sortOrder }
    val inUse = products.any { it.categoryId == selectedId }

    var newCategory by remember { mutableStateOf(false) }
    var fieldDialog by remember { mutableStateOf<CategoryField?>(null) }
    var addingField by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("Categories and questions", onBack) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { c -> GlassChip(c.name, c.id == selectedId) { selectedId = c.id } }
            item { GlassChip("+ New category", false) { newCategory = true } }
        }
        message?.let { Text(it, color = Bad, fontWeight = FontWeight.SemiBold) }

        if (category != null) {
            SectionLabel("How ${category.name} works")
            SwitchRow(
                "Track each unit by IMEI / serial",
                if (inUse) "Locked because this category already has products." else "For phones and other serial items.",
                category.tracksImei,
                { v -> scope.launch { repo.saveCategory(category, category.name, v, category.hasWarranty) } },
                enabled = !inUse,
            )
            SwitchRow(
                "Has warranty",
                "Asks for warranty months when adding a product.",
                category.hasWarranty,
                { v -> scope.launch { repo.saveCategory(category, category.name, category.tracksImei, v) } },
            )

            SectionLabel("Questions asked when adding a ${category.name.lowercase()}")
            fields.forEachIndexed { index, f ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { fieldDialog = f }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                typeNames[typeCodes.indexOf(f.type).coerceAtLeast(0)] + if (f.required) " · required" else "",
                                color = Muted,
                                fontSize = 13.sp,
                            )
                        }
                        TextButton(
                            onClick = { scope.launch { swap(repo, category.id, fields, index, index - 1) } },
                            enabled = index > 0,
                        ) { Text("Up") }
                        TextButton(
                            onClick = { scope.launch { swap(repo, category.id, fields, index, index + 1) } },
                            enabled = index < fields.lastIndex,
                        ) { Text("Down") }
                    }
                }
            }
            GlassButton("+ Add a question", { addingField = true }, Modifier.fillMaxWidth())
            Text(
                "Each question can be required or optional. This is how the app fits any kind of shop.",
                color = Muted,
                fontSize = 13.sp,
            )
            GlassButton("Delete this category", { confirmDelete = true }, Modifier.fillMaxWidth(), danger = true)
        }
    }

    if (newCategory) {
        var name by remember { mutableStateOf("") }
        EditDialog("New category", name.isNotBlank(), { newCategory = false }, {
            scope.launch { selectedId = repo.saveCategory(null, name, false, false) }
            newCategory = false
        }) { GlassField(name, { name = it }, "Category name (for example Tablet)") }
    }

    if (addingField || fieldDialog != null) {
        val existing = fieldDialog
        val catId = selectedId
        if (catId != null) {
            var label by remember(existing) { mutableStateOf(existing?.label ?: "") }
            var type by remember(existing) { mutableStateOf(existing?.type ?: "TEXT") }
            var options by remember(existing) { mutableStateOf(existing?.options?.replace("|", ", ") ?: "") }
            var required by remember(existing) { mutableStateOf(existing?.required ?: false) }
            var del by remember { mutableStateOf(false) }
            val close = { addingField = false; fieldDialog = null }
            EditDialog(
                if (existing == null) "New question" else "Edit question",
                label.isNotBlank() && (type != "CHOICE" || options.isNotBlank()),
                close,
                {
                    val opts = options.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("|")
                    scope.launch {
                        repo.saveField(existing, catId, label, type, if (type == "CHOICE") opts else "", required, existing?.sortOrder ?: fields.size)
                    }
                    close()
                },
            ) {
                GlassField(label, { label = it }, "Question (for example Storage)")
                PickerField("Answer type", typeNames[typeCodes.indexOf(type).coerceAtLeast(0)], typeNames, onPick = {
                    type = typeCodes[typeNames.indexOf(it)]
                })
                if (type == "CHOICE") GlassField(options, { options = it }, "Choices, separated by commas")
                SwitchRow("Required", "Cannot save a product without it.", required, { required = it })
                if (existing != null) TextButton(onClick = { del = true }) { Text("Delete this question", color = Bad) }
            }
            if (del && existing != null) {
                ConfirmDialog("Delete this question?", "Products keep any answers already saved.", "Delete",
                    onConfirm = { scope.launch { repo.deleteField(existing.id) }; del = false; close() },
                    onDismiss = { del = false })
            }
        }
    }

    if (confirmDelete && category != null) {
        ConfirmDialog("Delete ${category.name}?", "You can only delete a category with no products.", "Delete",
            onConfirm = {
                scope.launch { message = repo.deleteCategory(category.id) }
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false })
    }
}

private suspend fun swap(repo: Repository, categoryId: String, fields: List<CategoryField>, a: Int, b: Int) {
    if (b !in fields.indices) return
    val fa = fields[a]
    val fb = fields[b]
    // Give every question a clean 0..n order, with the two swapped.
    val ordered = fields.toMutableList().also { it[a] = fb; it[b] = fa }
    ordered.forEachIndexed { i, f -> if (f.sortOrder != i) repo.saveField(f, categoryId, f.label, f.type, f.options, f.required, i) }
}
