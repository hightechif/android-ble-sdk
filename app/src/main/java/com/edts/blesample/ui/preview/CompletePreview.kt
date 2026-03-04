package com.edts.blesample.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Standard", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Big Font", fontScale = 1.5f, showBackground = true)
@Preview(name = "Large Screen", device = Devices.PIXEL_C, showBackground = true)
annotation class CompletePreview