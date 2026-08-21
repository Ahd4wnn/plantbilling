package com.plantora.billing.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.plantora.billing.R
import com.plantora.billing.domain.Product
import com.plantora.billing.domain.ProductViewMode
import com.plantora.billing.ui.theme.Dimens

/**
 * The product catalogue, in either layout. Shared by the Bill screen's picker
 * (where a tap adds to the cart) and the Products tab (where a tap edits), so the
 * two never drift apart visually.
 *
 * [secondaryLine] and [onDelete] are what differ between the two: only the manage
 * screen shows a category subtitle and a delete affordance. [addable] draws the
 * "+" badge that tells a salesperson a tap puts the item on the bill.
 */
@Composable
fun ProductCatalog(
    products: List<Product>,
    viewMode: ProductViewMode,
    onClick: (Product) -> Unit,
    modifier: Modifier = Modifier,
    addable: Boolean = false,
    secondaryLine: ((Product) -> String)? = null,
    onDelete: ((Product) -> Unit)? = null,
) {
    when (viewMode) {
        ProductViewMode.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            items(products, key = { it.id }) { product ->
                ProductCell(
                    product = product,
                    addable = addable,
                    onClick = { onClick(product) },
                    onDelete = onDelete?.let { { it(product) } },
                )
            }
        }

        ProductViewMode.LIST -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            items(products, key = { it.id }) { product ->
                ProductListRow(
                    product = product,
                    addable = addable,
                    subtitle = secondaryLine?.invoke(product),
                    onClick = { onClick(product) },
                    onDelete = onDelete?.let { { it(product) } },
                )
            }
        }
    }
}

@Composable
private fun ProductCell(
    product: Product,
    addable: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f),
                contentAlignment = Alignment.Center,
            ) {
                ProductPhoto(product, Modifier.fillMaxSize())
                if (addable) {
                    Icon(
                        Icons.Rounded.AddCircle,
                        contentDescription = stringResource(R.string.product_add_cd, product.name),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Dimens.sm),
                    )
                }
            }
            Row(
                Modifier.padding(Dimens.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
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
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.product_delete_cd, product.name),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductListRow(
    product: Product,
    addable: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(Dimens.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp),
            ) {
                ProductPhoto(product, Modifier.size(56.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = Dimens.md)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MoneyText(
                product.retailPrice,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (addable) {
                Icon(
                    Icons.Rounded.AddCircle,
                    contentDescription = stringResource(R.string.product_add_cd, product.name),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Dimens.sm),
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.product_delete_cd, product.name),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductPhoto(product: Product, modifier: Modifier) {
    if (product.photoUrl != null) {
        AsyncImage(
            model = product.photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.LocalFlorist,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Blocks-vs-list switch. Sits beside the search field on both product screens. */
@Composable
fun ProductViewToggle(
    mode: ProductViewMode,
    onChange: (ProductViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = if (mode == ProductViewMode.GRID) ProductViewMode.LIST else ProductViewMode.GRID
    IconButton(
        onClick = { onChange(next) },
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = if (mode == ProductViewMode.GRID) {
                Icons.AutoMirrored.Rounded.ViewList
            } else {
                Icons.Rounded.GridView
            },
            // Labelled by what the tap DOES, not by the current state — that is what
            // a screen reader user needs to hear before deciding to press it.
            contentDescription = stringResource(
                if (mode == ProductViewMode.GRID) R.string.products_view_list_cd
                else R.string.products_view_blocks_cd,
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
