package com.edts.blesample.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ControlPanel(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    onReadNotificationClick: () -> Unit,
    onWriteMessageClick: () -> Unit,
    onDisableNotificationClick: () -> Unit,
    onReadRssiClick: () -> Unit
) {
    Box(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Actions:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Control Panel",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.padding(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    modifier = Modifier.width(160.dp),
                    onClick = onReadNotificationClick,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF005073),
                        contentColor = Color(0xFF03A9F4),
                        disabledContainerColor = Color(0xFF3A3A3A),
                        disabledContentColor = Color(0xFF7A7A7A)
                    )
                ) {
                    Text(text = "Read Notification", fontSize = 12.sp)
                }
                OutlinedButton(
                    modifier = Modifier.width(160.dp),
                    onClick = onWriteMessageClick,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF005073),
                        contentColor = Color(0xFF03A9F4),
                        disabledContainerColor = Color(0xFF3A3A3A),
                        disabledContentColor = Color(0xFF7A7A7A)
                    )
                ) {
                    Text(text = "Write Message", fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    modifier = Modifier.width(160.dp),
                    onClick = onDisableNotificationClick,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF800020),
                        contentColor = Color(0xFFCE2029),
                        disabledContainerColor = Color(0xFF3A3A3A),
                        disabledContentColor = Color(0xFF7A7A7A)
                    )
                ) {
                    Text(text = "Disable Notification", fontSize = 12.sp)
                }
                OutlinedButton(
                    modifier = Modifier.width(160.dp),
                    onClick = onReadRssiClick,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF005073),
                        contentColor = Color(0xFF03A9F4),
                        disabledContainerColor = Color(0xFF3A3A3A),
                        disabledContentColor = Color(0xFF7A7A7A)
                    )
                ) {
                    Text(text = "Read RSSI", fontSize = 12.sp)
                }
            }
        }
    }
}

// --- Previews ---
@Preview
@Composable
private fun ControlPanelPreview() {
    ControlPanel(
        isConnected = false,
        onReadNotificationClick = { },
        onWriteMessageClick = { },
        onDisableNotificationClick = { },
        onReadRssiClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp)
    )
}