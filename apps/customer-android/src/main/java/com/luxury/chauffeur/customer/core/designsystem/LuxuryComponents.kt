package com.luxury.chauffeur.customer.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LuxuryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSecondary: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSecondary) LuxuryColors.CardSurface else LuxuryColors.ChampagneGold,
            contentColor = if (isSecondary) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack,
            disabledContainerColor = LuxuryColors.CardSurface.copy(alpha = 0.5f),
            disabledContentColor = LuxuryColors.SubtleGray
        ),
        border = if (isSecondary) BorderStroke(1.dp, LuxuryColors.ChampagneGold) else null,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = if (isSecondary) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = text,
                style = LuxuryTypography.HeadlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSecondary) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack
                )
            )
        }
    }
}

@Composable
fun LuxuryCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = BorderStroke(1.dp, LuxuryColors.CardBorder),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
        color = LuxuryColors.CardSurface,
        border = border,
        modifier = modifier,
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(LuxuryDimensions.PaddingMedium),
            content = content
        )
    }
}

@Composable
fun GoldBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
        color = LuxuryColors.ChampagneGold.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = LuxuryTypography.TitleGold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
