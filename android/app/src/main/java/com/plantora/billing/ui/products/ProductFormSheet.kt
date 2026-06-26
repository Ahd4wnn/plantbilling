package com.plantora.billing.ui.products

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens

@Composable
fun ProductFormSheet(
    form: ProductFormState,
    onUpdate: ((ProductFormState) -> ProductFormState) -> Unit,
    onSave: () -> Unit,
    onPickImage: (bytes: ByteArray, fileName: String, mime: String) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val name = queryDisplayName(resolver, uri) ?: "photo.jpg"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) onPickImage(bytes, name, mime)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Text(
            if (form.isEdit) "Edit product" else "New product",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(Dimens.lg))

        PlantoraTextField(form.name, { v -> onUpdate { it.copy(name = v) } }, label = "Name")
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(
            form.priceInput, { v -> onUpdate { it.copy(priceInput = v) } },
            label = "Price (₹)", keyboardType = KeyboardType.Decimal,
        )
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(form.category, { v -> onUpdate { it.copy(category = v) } }, label = "Category (optional)")

        if (form.isEdit) {
            Spacer(Modifier.height(Dimens.lg))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Active (shown in billing)", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = form.isActive, onCheckedChange = { c -> onUpdate { it.copy(isActive = c) } })
            }

            SectionHeader("Photo")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(72.dp),
                ) {
                    if (form.photoUrl != null) {
                        AsyncImage(
                            model = form.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                SecondaryButton(
                    text = if (form.photoUrl != null) "Change photo" else "Add photo",
                    onClick = { picker.launch("image/*") },
                    leadingIcon = Icons.Rounded.AddAPhoto,
                )
            }
        } else {
            Spacer(Modifier.height(Dimens.sm))
            Text(
                "You can add a photo after saving the product.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        form.error?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(
            text = if (form.isEdit) "Save changes" else "Add product",
            onClick = onSave,
            enabled = form.canSave,
            loading = form.saving,
        )
    }
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
    return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) c.getString(idx) else null
        } else null
    }
}
