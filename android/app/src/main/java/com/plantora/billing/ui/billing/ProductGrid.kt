package com.plantora.billing.ui.billing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.plantora.billing.domain.Product
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.theme.Dimens

@Composable
fun ProductGrid(
    products: List<Product>,
    onAdd: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.screenPadding),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.md),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.md),
    ) {
        items(products, key = { it.id }) { product ->
            ProductCell(product = product, onAdd = { onAdd(product) })
        }
    }
}

@Composable
private fun ProductCell(product: Product, onAdd: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.clickable(onClick = onAdd),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f),
                contentAlignment = Alignment.Center,
            ) {
                if (product.photoUrl != null) {
                    AsyncImage(
                        model = product.photoUrl,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.LocalFlorist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(Dimens.lg),
                            )
                        }
                    }
                }
                Icon(
                    Icons.Rounded.AddCircle,
                    contentDescription = "Add ${product.name}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Dimens.sm),
                )
            }
            Column(Modifier.padding(Dimens.md)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MoneyText(
                    money = product.retailPrice,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Dimens.xs),
                )
            }
        }
    }
}
