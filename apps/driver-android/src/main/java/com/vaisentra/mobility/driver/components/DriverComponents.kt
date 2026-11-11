package com.vaisentra.mobility.driver.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.theme.*

@Composable
fun CockpitCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    backgroundColor: Color = PilotSurfaceElevated,
    borderColor: Color = if (glow) PilotOnlineGreen else PilotBorder,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(if (glow) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun CockpitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isOutlined: Boolean = false,
    backgroundColor: Color = if (isOutlined) Color.Transparent else PilotOnlineGreen,
    textColor: Color = if (isOutlined) PilotOnlineGreen else PilotCanvas,
    icon: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (isOutlined) Modifier.border(1.dp, PilotOnlineGreen, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = PilotCard
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                text = text,
                color = if (enabled) textColor else PilotTextDim,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun CockpitBadge(
    text: String,
    backgroundColor: Color = OnlineGlow,
    textColor: Color = PilotOnlineGreen,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun CockpitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingIcon: String? = null,
    trailingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                color = PilotTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(text = placeholder, color = PilotTextDim, fontSize = 14.sp) },
            leadingIcon = if (leadingIcon != null) {
                { Text(text = leadingIcon, fontSize = 16.sp) }
            } else null,
            trailingIcon = if (trailingText != null) {
                { Text(text = trailingText, color = PilotElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            } else null,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PilotSurface,
                unfocusedContainerColor = PilotSurface,
                focusedBorderColor = PilotBorderFocus,
                unfocusedBorderColor = PilotBorder,
                focusedTextColor = PilotTextWhite,
                unfocusedTextColor = PilotTextWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CockpitHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actionIcon: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PilotSurfaceElevated)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "←", color = PilotTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PilotTextWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = PilotTextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (actionIcon != null && onAction != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PilotSurfaceElevated)
                    .clickable { onAction() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = actionIcon, color = PilotTextWhite, fontSize = 16.sp)
            }
        }
    }
}

