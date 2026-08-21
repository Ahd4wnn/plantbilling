package com.plantora.billing.ui.products

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.plantora.billing.R
import com.plantora.billing.i18n.asString
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
    onPickImage: (Uri) -> Unit,
) {
    // Hand the Uri straight to the ViewModel: reading and re-encoding a 12MP photo
    // is far too slow to do here on the main thread (it used to risk an ANR).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) onPickImage(uri)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Text(
            if (form.isEdit) stringResource(R.string.form_edit_product) else stringResource(R.string.form_new_product),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(Dimens.lg))

        PlantoraTextField(form.name, { v -> onUpdate { it.copy(name = v) } }, label = stringResource(R.string.label_name))
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(
            form.priceInput, { v -> onUpdate { it.copy(priceInput = v) } },
            label = stringResource(R.string.price_label), keyboardType = KeyboardType.Decimal,
        )
        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(form.category, { v -> onUpdate { it.copy(category = v) } }, label = stringResource(R.string.form_category))

        if (form.isEdit) {
            Spacer(Modifier.height(Dimens.lg))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.form_active), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = form.isActive, onCheckedChange = { c -> onUpdate { it.copy(isActive = c) } })
            }

            SectionHeader(stringResource(R.string.form_photo))
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
                    text = if (form.photoUrl != null) stringResource(R.string.form_change_photo) else stringResource(R.string.form_add_photo),
                    onClick = { picker.launch("image/*") },
                    leadingIcon = Icons.Rounded.AddAPhoto,
                )
            }
        } else {
            Spacer(Modifier.height(Dimens.sm))
            Text(
                stringResource(R.string.form_photo_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        form.error?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(
            text = if (form.isEdit) stringResource(R.string.action_save_changes) else stringResource(R.string.products_add),
            onClick = onSave,
            enabled = form.canSave,
            loading = form.saving,
        )
    }
}
