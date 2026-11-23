package com.vaisentra.mobility.rider.feature.pool

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.launch

// Screen 21: Eco-Pool Shared Seat Booking
@Composable
fun EcoPoolBookingScreen(onNavigate: (String) -> Unit) {
    var seats by remember { mutableStateOf(1) }
    val maxDetourMin = 10
    val currentDetourMin = 5
    val baseFareSolitary = 120.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        NeoHeader(
            title = "Eco-Pool Shared E-Rickshaw",
            subtitle = "Ultra-low cost high-density corridors",
            onBack = { onNavigate("fare_matrix") }
        )
        Spacer(modifier = Modifier.height(14.dp))

        NeoCard {
            Text(text = "Anand Vihar ⇌ Connaught Place Express", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            NeoBadge(text = "HIGH-FREQUENCY FEEDER CORRIDOR", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Seats to Reserve:", color = TextMuted, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(1, 2, 3).forEach { s ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (seats == s) SapphirePrimary else SurfaceCard)
                                .border(1.dp, if (seats == s) BorderFocus else BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { seats = s }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "$s", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            val perSeat = 35.0
            val total = perSeat * seats
            val savings = (baseFareSolitary * seats) - total

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Total Shared Tariff:", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "You save ₹${savings.toInt()} (35% OFF)", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(text = "₹${total.toInt()}.00", color = ElectricCyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Domain J: Detour Constraint & Multi-Rider Timeline Visualizer
        NeoCard(borderColor = SapphireGlow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "DETOUR OPTIMIZATION ENGINE", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                NeoBadge(text = "MAX 10 MIN DETOUR", backgroundColor = SurfaceDark, textColor = AmberGold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Detour Constraint: Extra detour ≤ min(3.0 km, 20% distance), max 10 mins delay guaranteed.",
                color = TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Detour Progress Indicator
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Estimated Detour Buffer:", color = TextDim, fontSize = 11.sp)
                    Text(text = "$currentDetourMin / $maxDetourMin mins", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SurfaceDark)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentDetourMin.toFloat() / maxDetourMin.toFloat())
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(AmberGold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "RIDE STOPS TIMELINE:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Multi-node corridor stops
            val stops = listOf(
                Triple("🟢 You (Boarding Point)", "Anand Vihar Metro Gate 1", "08:15 AM"),
                Triple("🟡 Priya (+1.1 km detour)", "Karkardooma Court Metro", "+3 mins"),
                Triple("🟡 Amit (+0.6 km detour)", "Laxmi Nagar Feeder Pillar", "+2 mins"),
                Triple("🔴 Common Destination", "Connaught Place Gate 4", "08:35 AM")
            )

            stops.forEachIndexed { idx, (title, loc, eta) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "${idx + 1}", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = loc, color = TextMuted, fontSize = 10.sp)
                    }
                    Text(text = eta, color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🌱", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Eco-Bonus: Pooling saves 0.42 kg CO₂ and gives 15 Green Club credits!",
                        color = EmeraldGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(text = "Confirm Shared Pool Seat", onClick = {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                RiderRepository.bookSharedPool(
                    com.vaisentra.mobility.rider.data.PoolBookingRequest(
                        categoryId = "9b1c4e58-78b3-4a0b-80bb-f625f2d6ea2a",
                        pickupAddress = "Anand Vihar Metro",
                        destinationAddress = "Connaught Place Gate 4",
                        pickupLatitude = 28.6469,
                        pickupLongitude = 77.3160,
                        destinationLatitude = 28.6328,
                        destinationLongitude = 77.2197,
                        seatsReserved = seats
                    )
                )
            }
            DialogController.showToast("$seats Shared seat(s) reserved on Eco-Pool corridor!")
            onNavigate("pool_chat")
        }, icon = "👥")
    }
}

// Screen 22: Shared Pool Passenger Chat
@Composable
fun EcoPoolChatScreen(onNavigate: (String) -> Unit) {
    var inputMessage by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf<Triple<String, String, String>>()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Corridor Group Chat", subtitle = "Encrypted broadcast for verified co-riders", onBack = { onNavigate("eco_pool_booking") })
        Spacer(modifier = Modifier.height(12.dp))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "👥", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "No Messages Yet", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "You are connected to the pool chat. Send a message to co-riders.", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { (sender, msg, time) ->
                    NeoCard(
                        backgroundColor = if (sender.startsWith("Pilot")) Color(0xFF1E283D) else SurfaceCard,
                        cornerRadius = 14.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = sender, color = if (sender.startsWith("Pilot")) ElectricCyan else TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = time, color = TextDim, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = msg, color = TextWhite, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                NeoTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = "Message co-passengers..."
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SapphirePrimary)
                    .clickable {
                        if (inputMessage.isNotBlank()) {
                            val text = inputMessage
                            messages.add(Triple("You", text, "Just now"))
                            inputMessage = ""
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                RiderRepository.sendPoolChatMessage("trip-eco-1", text)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "➤", color = TextWhite, fontSize = 18.sp)
            }
        }
    }
}

