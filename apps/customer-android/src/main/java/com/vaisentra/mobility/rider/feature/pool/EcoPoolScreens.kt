package com.vaisentra.mobility.rider.feature.pool

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Eco-Pool Shared E-Rickshaw", subtitle = "Ultra-low cost high-density corridors", onBack = { onNavigate("fare_matrix") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Anand Vihar ⇌ Connaught Place Express", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            NeoBadge(text = "HIGH-FREQUENCY FEEDER CORRIDOR", backgroundColor = EmeraldGlow, textColor = EmeraldGreen)
            Spacer(modifier = Modifier.height(16.dp))
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
            Spacer(modifier = Modifier.height(16.dp))
            val perSeat = 35.0
            val total = perSeat * seats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Total Tariff ($seats Seats):", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = "₹${total.toInt()}.00", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
        mutableStateListOf(
            Triple("Pilot Ram Lal", "Departing Anand Vihar Metro Pillar 42 in 3 mins.", "08:14 AM"),
            Triple("Priya (Co-Rider)", "Waiting near ATM gate.", "08:15 AM"),
            Triple("Amit (Co-Rider)", "I am at Gate 2 with green backpack.", "08:16 AM")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Corridor Group Chat", subtitle = "Encrypted broadcast for verified co-riders", onBack = { onNavigate("eco_pool_booking") })
        Spacer(modifier = Modifier.height(12.dp))

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

