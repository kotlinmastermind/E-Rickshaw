package com.vaisentra.mobility.driver.feature.duty

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.R
import com.vaisentra.mobility.driver.theme.DriverGoTheme

@Composable
fun VasantraDriverHomeScreen(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {}
) {
    var isOnline by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFFF4F6FC)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(36.dp))

                VasantraDriverTopHeader(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onNotificationClick = { onNavigate("notifications") }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(20.dp))

                        VasantraMapSection(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            isOnline = isOnline
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        VasantraRideRequestCard(
                            modifier = Modifier.fillMaxWidth(),
                            onAccept = { onNavigate("accept_ride") },
                            onReject = { }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        VasantraCurrentRideCard(
                            modifier = Modifier.fillMaxWidth(),
                            onNavigateClick = { onNavigate("navigation") },
                            onEndRideClick = { onNavigate("end_ride") }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        VasantraEarningsSummaryCard(
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        VasantraQuickActionsGrid(
                            modifier = Modifier.fillMaxWidth(),
                            onActionClick = { route -> onNavigate(route) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        VasantraSosBannerCard(
                            modifier = Modifier.fillMaxWidth(),
                            onSosClick = { onNavigate("sos") }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        VasantraMoreFeaturesGrid(
                            modifier = Modifier.fillMaxWidth(),
                            onViewAllClick = { onNavigate("features") },
                            onFeatureClick = { route -> onNavigate(route) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    VasantraStatusTogglePill(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isOnline = isOnline,
                        onToggle = { isOnline = !isOnline }
                    )
                }
            }

            VasantraBottomNavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    selectedTab = index
                    when (index) {
                        0 -> onNavigate("home")
                        1 -> onNavigate("trips")
                        2 -> onNavigate("earnings")
                        3 -> onNavigate("notifications")
                        4 -> onNavigate("profile")
                    }
                }
            )
        }
    }
}

@Composable
fun VasantraDriverTopHeader(
    modifier: Modifier = Modifier,
    onNotificationClick: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(modifier = Modifier.size(52.dp)) {
                Canvas(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                ) {
                    drawCircle(color = Color(0xFFD1D5DB))
                    drawCircle(color = Color(0xFF374151), radius = size.minDimension / 4f, center = Offset(size.width / 2f, size.height / 2.6f))
                    drawCircle(color = Color(0xFF374151), radius = size.minDimension / 2.4f, center = Offset(size.width / 2f, size.height * 0.95f))
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        val checkPath = Path().apply {
                            moveTo(size.width * 0.2f, size.height * 0.5f)
                            lineTo(size.width * 0.45f, size.height * 0.75f)
                            lineTo(size.width * 0.85f, size.height * 0.25f)
                        }
                        drawPath(
                            path = checkPath,
                            color = Color.White,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = stringResource(id = R.string.driver_name_sample),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(id = R.string.driver_id_sample),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val leftPath = Path().apply {
                        moveTo(size.width * 0.05f, size.height * 0.1f)
                        lineTo(size.width * 0.45f, size.height * 0.9f)
                        lineTo(size.width * 0.3f, size.height * 0.9f)
                        lineTo(size.width * 0.05f, size.height * 0.4f)
                        close()
                    }
                    val rightPath = Path().apply {
                        moveTo(size.width * 0.45f, size.height * 0.9f)
                        lineTo(size.width * 0.95f, size.height * 0.1f)
                        lineTo(size.width * 0.7f, size.height * 0.1f)
                        lineTo(size.width * 0.35f, size.height * 0.75f)
                        close()
                    }
                    drawPath(path = leftPath, color = Color(0xFF1D61E7))
                    drawPath(path = rightPath, color = Color(0xFFFF7A00))
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column {
                    Row {
                        Text(
                            text = stringResource(id = R.string.vasantra_brand_name),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F2147)
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.mobility_brand_name),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D61E7)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onNotificationClick() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val bellPath = Path().apply {
                        moveTo(size.width * 0.5f, size.height * 0.15f)
                        cubicTo(size.width * 0.35f, size.height * 0.15f, size.width * 0.25f, size.height * 0.3f, size.width * 0.25f, size.height * 0.55f)
                        lineTo(size.width * 0.15f, size.height * 0.75f)
                        lineTo(size.width * 0.85f, size.height * 0.75f)
                        lineTo(size.width * 0.75f, size.height * 0.55f)
                        cubicTo(size.width * 0.75f, size.height * 0.3f, size.width * 0.65f, size.height * 0.15f, size.width * 0.5f, size.height * 0.15f)
                        close()
                    }
                    drawPath(path = bellPath, color = Color(0xFF0F172A))
                    drawArc(
                        color = Color(0xFF0F172A),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.38f, size.height * 0.78f),
                        size = Size(size.width * 0.24f, size.height * 0.18f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "3",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun VasantraStatusTogglePill(
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
    onToggle: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .clickable { onToggle() },
        color = Color.White,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isOnline) stringResource(id = R.string.status_online) else stringResource(id = R.string.status_offline),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Canvas(modifier = Modifier.size(12.dp)) {
                val arrowPath = Path().apply {
                    moveTo(0f, size.height * 0.3f)
                    lineTo(size.width * 0.5f, size.height * 0.8f)
                    lineTo(size.width, size.height * 0.3f)
                }
                drawPath(
                    path = arrowPath,
                    color = Color(0xFF0F172A),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
fun VasantraMapSection(
    modifier: Modifier = Modifier,
    isOnline: Boolean = true
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE5EEF5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color(0xFFE3EDF7))

                val roadColor = Color(0xFFFFFFFF)
                val mainRoadColor = Color(0xFFFDE68A)

                drawLine(color = roadColor, start = Offset(0f, size.height * 0.3f), end = Offset(size.width, size.height * 0.5f), strokeWidth = 14.dp.toPx())
                drawLine(color = mainRoadColor, start = Offset(size.width * 0.4f, 0f), end = Offset(size.width * 0.7f, size.height), strokeWidth = 18.dp.toPx())
                drawLine(color = roadColor, start = Offset(0f, size.height * 0.75f), end = Offset(size.width, size.height * 0.65f), strokeWidth = 12.dp.toPx())
                drawLine(color = roadColor, start = Offset(size.width * 0.15f, 0f), end = Offset(size.width * 0.25f, size.height), strokeWidth = 10.dp.toPx())

                val riverPath = Path().apply {
                    moveTo(size.width * 0.7f, 0f)
                    cubicTo(size.width * 0.8f, size.height * 0.3f, size.width * 0.5f, size.height * 0.6f, size.width * 0.9f, size.height)
                }
                drawPath(path = riverPath, color = Color(0xFFBFDBFE), style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round))

                drawRoundRect(
                    color = Color(0xFFDCFCE7),
                    topLeft = Offset(size.width * 0.05f, size.height * 0.1f),
                    size = Size(size.width * 0.25f, size.height * 0.25f),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )

                val center = Offset(size.width * 0.5f, size.height * 0.45f)
                drawCircle(color = Color(0x331D61E7), radius = 32.dp.toPx(), center = center)
                drawCircle(color = Color(0x551D61E7), radius = 22.dp.toPx(), center = center)

                val navArrow = Path().apply {
                    moveTo(center.x, center.y - 14.dp.toPx())
                    lineTo(center.x + 10.dp.toPx(), center.y + 10.dp.toPx())
                    lineTo(center.x, center.y + 4.dp.toPx())
                    lineTo(center.x - 10.dp.toPx(), center.y + 10.dp.toPx())
                    close()
                }
                drawPath(path = navArrow, color = Color(0xFF1D61E7))
            }

            Surface(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawCircle(color = Color(0xFF1D61E7), style = Stroke(width = 2.dp.toPx()))
                        drawCircle(color = Color(0xFF1D61E7), radius = 3.dp.toPx())
                        drawLine(color = Color(0xFF1D61E7), start = Offset(size.width / 2f, 0f), end = Offset(size.width / 2f, 4.dp.toPx()), strokeWidth = 2.dp.toPx())
                        drawLine(color = Color(0xFF1D61E7), start = Offset(size.width / 2f, size.height - 4.dp.toPx()), end = Offset(size.width / 2f, size.height), strokeWidth = 2.dp.toPx())
                        drawLine(color = Color(0xFF1D61E7), start = Offset(0f, size.height / 2f), end = Offset(4.dp.toPx(), size.height / 2f), strokeWidth = 2.dp.toPx())
                        drawLine(color = Color(0xFF1D61E7), start = Offset(size.width - 4.dp.toPx(), size.height / 2f), end = Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx())
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.you_are_here),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.BottomEnd)
                    .size(40.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawCircle(color = Color(0xFF1D61E7), style = Stroke(width = 2.dp.toPx()))
                        drawCircle(color = Color(0xFF1D61E7), radius = 4.dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable
fun VasantraRideRequestCard(
    modifier: Modifier = Modifier,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF7A00))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = Color.White, style = Stroke(width = 1.5.dp.toPx()))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.ride_request_tag),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = stringResource(id = R.string.time_2_min_ago),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1D61E7))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.sample_pickup),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                            .width(2.dp)
                            .height(16.dp)
                            .background(Color(0xFFCBD5E1))
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.sample_drop),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(id = R.string.sample_fare),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = stringResource(id = R.string.sample_distance),
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onReject() },
                    color = Color.White,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEF4444)))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawLine(color = Color(0xFFEF4444), start = Offset(0f, 0f), end = Offset(size.width, size.height), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(color = Color(0xFFEF4444), start = Offset(size.width, 0f), end = Offset(0f, size.height), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.action_reject),
                            color = Color(0xFFEF4444),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAccept() },
                    color = Color(0xFF10B981)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val path = Path().apply {
                                moveTo(size.width * 0.15f, size.height * 0.5f)
                                lineTo(size.width * 0.4f, size.height * 0.75f)
                                lineTo(size.width * 0.85f, size.height * 0.25f)
                            }
                            drawPath(path = path, color = Color.White, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.action_accept),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VasantraCurrentRideCard(
    modifier: Modifier = Modifier,
    onNavigateClick: () -> Unit = {},
    onEndRideClick: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2147)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2210B981))
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.current_ride_tag),
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(id = R.string.label_eta),
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.sample_eta_time),
                        fontSize = 18.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.sample_current_pickup),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "→",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.sample_current_drop),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFF1E293B))
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(3.dp)
                        .background(Color(0xFF1D61E7))
                )

                Box(
                    modifier = Modifier
                        .padding(start = 120.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        drawRoundRect(
                            color = Color(0xFF0F2147),
                            topLeft = Offset(0f, size.height * 0.3f),
                            size = Size(size.width, size.height * 0.5f),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                        drawCircle(color = Color(0xFF1D61E7), radius = 2.dp.toPx(), center = Offset(size.width * 0.25f, size.height * 0.8f))
                        drawCircle(color = Color(0xFF1D61E7), radius = 2.dp.toPx(), center = Offset(size.width * 0.75f, size.height * 0.8f))
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onNavigateClick() },
                    color = Color(0xFF1D61E7)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val path = Path().apply {
                                moveTo(size.width * 0.5f, 0f)
                                lineTo(size.width, size.height)
                                lineTo(size.width * 0.5f, size.height * 0.75f)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(path = path, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.action_navigate),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onEndRideClick() },
                    color = Color(0xFFEF4444)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(14.dp)) {
                            drawRoundRect(color = Color.White, cornerRadius = CornerRadius(2.dp.toPx()))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.action_end_ride),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VasantraEarningsSummaryCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.3f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "₹",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.todays_earnings_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(id = R.string.sample_todays_earnings),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(Color(0xFFE2E8F0))
            )

            Column(
                modifier = Modifier.weight(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.sample_rides_count),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = stringResource(id = R.string.label_rides),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(Color(0xFFE2E8F0))
            )

            Column(
                modifier = Modifier.weight(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.sample_total_distance),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = stringResource(id = R.string.label_distance),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .height(44.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val heights = listOf(0.3f, 0.5f, 0.4f, 0.8f, 1.0f)
                    heights.forEachIndexed { index, h ->
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight(h)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (index == 4) Color(0xFF10B981) else Color(0xFF86EFAC))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VasantraQuickActionsGrid(
    modifier: Modifier = Modifier,
    onActionClick: (String) -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        VasantraQuickActionItem(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.label_wallet),
            subtitle = stringResource(id = R.string.sample_wallet_balance),
            iconType = 0,
            onClick = { onActionClick("wallet") }
        )

        VasantraQuickActionItem(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.label_ride_history),
            subtitle = null,
            iconType = 1,
            onClick = { onActionClick("history") }
        )

        VasantraQuickActionItem(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.label_support),
            subtitle = null,
            iconType = 2,
            onClick = { onActionClick("support") }
        )

        VasantraQuickActionItem(
            modifier = Modifier.weight(1f),
            title = stringResource(id = R.string.label_documents),
            subtitle = null,
            iconType = 3,
            onClick = { onActionClick("documents") }
        )
    }
}

@Composable
fun VasantraQuickActionItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    iconType: Int,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    when (iconType) {
                        0 -> {
                            drawRoundRect(
                                color = Color(0xFF1D61E7),
                                topLeft = Offset(0f, size.height * 0.2f),
                                size = Size(size.width, size.height * 0.6f),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(color = Color(0xFF1D61E7), radius = 2.dp.toPx(), center = Offset(size.width * 0.75f, size.height * 0.5f))
                        }
                        1 -> {
                            drawCircle(color = Color(0xFF1D61E7), style = Stroke(width = 2.dp.toPx()))
                            drawLine(color = Color(0xFF1D61E7), start = Offset(size.width / 2f, size.height / 2f), end = Offset(size.width / 2f, size.height * 0.25f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(color = Color(0xFF1D61E7), start = Offset(size.width / 2f, size.height / 2f), end = Offset(size.width * 0.75f, size.height / 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                        2 -> {
                            drawArc(
                                color = Color(0xFF1D61E7),
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawRoundRect(color = Color(0xFF1D61E7), topLeft = Offset(0f, size.height * 0.45f), size = Size(4.dp.toPx(), 8.dp.toPx()))
                            drawRoundRect(color = Color(0xFF1D61E7), topLeft = Offset(size.width - 4.dp.toPx(), size.height * 0.45f), size = Size(4.dp.toPx(), 8.dp.toPx()))
                        }
                        else -> {
                            drawRoundRect(
                                color = Color(0xFF1D61E7),
                                topLeft = Offset(size.width * 0.15f, 0f),
                                size = Size(size.width * 0.7f, size.height),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawLine(color = Color(0xFF1D61E7), start = Offset(size.width * 0.3f, size.height * 0.3f), end = Offset(size.width * 0.7f, size.height * 0.3f), strokeWidth = 2.dp.toPx())
                            drawLine(color = Color(0xFF1D61E7), start = Offset(size.width * 0.3f, size.height * 0.5f), end = Offset(size.width * 0.7f, size.height * 0.5f), strokeWidth = 2.dp.toPx())
                            drawLine(color = Color(0xFF1D61E7), start = Offset(size.width * 0.3f, size.height * 0.7f), end = Offset(size.width * 0.55f, size.height * 0.7f), strokeWidth = 2.dp.toPx())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun VasantraSosBannerCard(
    modifier: Modifier = Modifier,
    onSosClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onSosClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFCA5A5)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.5f, 0f)
                            lineTo(size.width, size.height * 0.3f)
                            lineTo(size.width, size.height * 0.7f)
                            lineTo(size.width * 0.5f, size.height)
                            lineTo(0f, size.height * 0.7f)
                            lineTo(0f, size.height * 0.3f)
                            close()
                        }
                        drawPath(path = path, color = Color.White)
                        drawLine(color = Color(0xFFEF4444), start = Offset(size.width / 2f, size.height * 0.25f), end = Offset(size.width / 2f, size.height * 0.55f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(color = Color(0xFFEF4444), radius = 1.5.dp.toPx(), center = Offset(size.width / 2f, size.height * 0.72f))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = stringResource(id = R.string.sos_tag),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFDC2626)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = stringResource(id = R.string.emergency_help_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF991B1B)
                    )
                    Text(
                        text = stringResource(id = R.string.emergency_help_desc),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val phonePath = Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.1f)
                        lineTo(size.width * 0.4f, size.height * 0.3f)
                        lineTo(size.width * 0.3f, size.height * 0.5f)
                        cubicTo(size.width * 0.4f, size.height * 0.7f, size.width * 0.6f, size.height * 0.8f, size.width * 0.7f, size.height * 0.7f)
                        lineTo(size.width * 0.9f, size.height * 0.8f)
                        lineTo(size.width * 0.8f, size.height)
                        cubicTo(size.width * 0.3f, size.height, 0f, size.height * 0.7f, 0f, size.height * 0.2f)
                        close()
                    }
                    drawPath(path = phonePath, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun VasantraMoreFeaturesGrid(
    modifier: Modifier = Modifier,
    onViewAllClick: () -> Unit = {},
    onFeatureClick: (String) -> Unit = {}
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.more_features_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = stringResource(id = R.string.action_view_all),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D61E7),
                modifier = Modifier.clickable { onViewAllClick() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VasantraFeatureItem(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.label_demand_heatmap),
                iconType = 0,
                onClick = { onFeatureClick("heatmap") }
            )

            VasantraFeatureItem(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.label_incentives),
                iconType = 1,
                onClick = { onFeatureClick("incentives") }
            )

            VasantraFeatureItem(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.label_daily_targets),
                iconType = 2,
                onClick = { onFeatureClick("targets") }
            )

            VasantraFeatureItem(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.label_performance),
                iconType = 3,
                onClick = { onFeatureClick("performance") }
            )
        }
    }
}

@Composable
fun VasantraFeatureItem(
    modifier: Modifier = Modifier,
    title: String,
    iconType: Int,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when (iconType) {
                            0 -> Color(0xFFFFF7ED)
                            1 -> Color(0xFFFEFCE8)
                            2 -> Color(0xFFFFF7ED)
                            else -> Color(0xFFF0FDFA)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    when (iconType) {
                        0 -> {
                            val flamePath = Path().apply {
                                moveTo(size.width * 0.5f, 0f)
                                cubicTo(size.width * 0.8f, size.height * 0.4f, size.width, size.height * 0.6f, size.width * 0.8f, size.height)
                                cubicTo(size.width * 0.6f, size.height, size.width * 0.4f, size.height, size.width * 0.2f, size.height)
                                cubicTo(0f, size.height * 0.6f, size.width * 0.2f, size.height * 0.4f, size.width * 0.5f, 0f)
                                close()
                            }
                            drawPath(path = flamePath, color = Color(0xFFF97316))
                        }
                        1 -> {
                            val trophyPath = Path().apply {
                                moveTo(size.width * 0.2f, 0f)
                                lineTo(size.width * 0.8f, 0f)
                                lineTo(size.width * 0.7f, size.height * 0.5f)
                                lineTo(size.width * 0.3f, size.height * 0.5f)
                                close()
                            }
                            drawPath(path = trophyPath, color = Color(0xFFEAB308))
                            drawLine(color = Color(0xFFEAB308), start = Offset(size.width / 2f, size.height * 0.5f), end = Offset(size.width / 2f, size.height * 0.8f), strokeWidth = 3.dp.toPx())
                            drawRect(color = Color(0xFFEAB308), topLeft = Offset(size.width * 0.3f, size.height * 0.8f), size = Size(size.width * 0.4f, size.height * 0.2f))
                        }
                        2 -> {
                            drawCircle(color = Color(0xFFF97316), style = Stroke(width = 2.dp.toPx()))
                            drawCircle(color = Color(0xFFF97316), radius = 4.dp.toPx())
                        }
                        else -> {
                            drawLine(color = Color(0xFF06B6D4), start = Offset(size.width * 0.1f, size.height * 0.9f), end = Offset(size.width * 0.35f, size.height * 0.6f), strokeWidth = 2.dp.toPx())
                            drawLine(color = Color(0xFF06B6D4), start = Offset(size.width * 0.35f, size.height * 0.6f), end = Offset(size.width * 0.65f, size.height * 0.75f), strokeWidth = 2.dp.toPx())
                            drawLine(color = Color(0xFF06B6D4), start = Offset(size.width * 0.65f, size.height * 0.75f), end = Offset(size.width * 0.9f, size.height * 0.2f), strokeWidth = 2.dp.toPx())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VasantraBottomNavigationBar(
    modifier: Modifier = Modifier,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {}
) {
    Surface(
        modifier = modifier.height(64.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                stringResource(id = R.string.nav_home),
                stringResource(id = R.string.nav_trips),
                stringResource(id = R.string.nav_earnings),
                stringResource(id = R.string.nav_notifications),
                stringResource(id = R.string.nav_profile)
            )

            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                val itemColor = if (isSelected) Color(0xFF1D61E7) else Color(0xFF64748B)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            when (index) {
                                0 -> {
                                    val housePath = Path().apply {
                                        moveTo(size.width * 0.5f, 0f)
                                        lineTo(size.width, size.height * 0.45f)
                                        lineTo(size.width * 0.85f, size.height * 0.45f)
                                        lineTo(size.width * 0.85f, size.height)
                                        lineTo(size.width * 0.15f, size.height)
                                        lineTo(size.width * 0.15f, size.height * 0.45f)
                                        lineTo(0f, size.height * 0.45f)
                                        close()
                                    }
                                    drawPath(path = housePath, color = itemColor)
                                }
                                1 -> {
                                    drawRoundRect(
                                        color = itemColor,
                                        topLeft = Offset(0f, size.height * 0.3f),
                                        size = Size(size.width, size.height * 0.5f),
                                        cornerRadius = CornerRadius(3.dp.toPx())
                                    )
                                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(size.width * 0.25f, size.height * 0.8f))
                                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(size.width * 0.75f, size.height * 0.8f))
                                }
                                2 -> {
                                    drawCircle(color = itemColor, style = Stroke(width = 2.dp.toPx()))
                                }
                                3 -> {
                                    val bellPath = Path().apply {
                                        moveTo(size.width * 0.5f, size.height * 0.15f)
                                        cubicTo(size.width * 0.35f, size.height * 0.15f, size.width * 0.25f, size.height * 0.3f, size.width * 0.25f, size.height * 0.55f)
                                        lineTo(size.width * 0.15f, size.height * 0.75f)
                                        lineTo(size.width * 0.85f, size.height * 0.75f)
                                        lineTo(size.width * 0.75f, size.height * 0.55f)
                                        cubicTo(size.width * 0.75f, size.height * 0.3f, size.width * 0.65f, size.height * 0.15f, size.width * 0.5f, size.height * 0.15f)
                                        close()
                                    }
                                    drawPath(path = bellPath, color = itemColor)
                                }
                                else -> {
                                    drawCircle(color = itemColor, radius = size.width * 0.25f, center = Offset(size.width / 2f, size.height * 0.3f))
                                    drawArc(color = itemColor, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(0f, size.height * 0.45f), size = Size(size.width, size.height * 0.55f))
                                }
                            }
                        }

                        if (index == 3) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "3", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = itemColor
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(2.dp)
                                .background(Color(0xFF1D61E7))
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun VasantraDriverHomeScreenPreview() {
    DriverGoTheme {
        VasantraDriverHomeScreen()
    }
}
