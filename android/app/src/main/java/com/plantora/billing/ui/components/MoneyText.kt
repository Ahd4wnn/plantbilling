package com.plantora.billing.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.plantora.billing.domain.Money

/**
 * Large, legible rupee amount. Defaults to semibold for emphasis on numbers.
 * Always a single line — a long amount (e.g. ₹1,23,456.00) never wraps into a
 * stacked, one-character-per-line mess. Give it room via the surrounding layout.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emphasize: Boolean = true,
) {
    Text(
        text = money.format(),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = if (emphasize) FontWeight.SemiBold else null,
        maxLines = 1,
        softWrap = false,
    )
}
