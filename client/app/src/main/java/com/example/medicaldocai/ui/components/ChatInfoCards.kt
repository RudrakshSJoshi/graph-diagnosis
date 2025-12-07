package com.example.medicaldocai.ui.components


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MedicalDisclaimerCard(modifier: Modifier = Modifier) {
// Uncomment if you want to use the card again
//    Surface(
//        modifier = modifier,
//        shape = MaterialTheme.shapes.medium,
//        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
//        shadowElevation = 2.dp
//    ) {
//        Column(
//            modifier = Modifier.padding(12.dp),
//            verticalArrangement = Arrangement.spacedBy(6.dp)
//        ) {
//            Text(
//                text = "⚕️ Important",
//                style = MaterialTheme.typography.labelLarge,
//                fontWeight = FontWeight.Bold,
//                color = MaterialTheme.colorScheme.onSecondaryContainer
//            )
//            Text(
//                text = "This is an AI-powered assistant and NOT a substitute for professional medical advice. Always consult a qualified doctor for serious health concerns or emergencies.",
//                style = MaterialTheme.typography.bodySmall,
//                color = MaterialTheme.colorScheme.onSecondaryContainer,
//                lineHeight = 16.sp
//            )
//        }
//    }
}

@Composable
fun EmptyStateUI(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(text = "👋", style = MaterialTheme.typography.displayMedium)
            Text(
                text = "Welcome to Your Medical Assistant",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Ask me about health precautions, symptoms, and medical suggestions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            QuickSuggestionButton(
                text = "How to prevent flu?",
                onClick = { onSuggestionClick("How to prevent flu?") }
            )
            QuickSuggestionButton(
                text = "Tips for better sleep",
                onClick = { onSuggestionClick("Tips for better sleep") }
            )
        }
    }
}

@Composable
fun QuickSuggestionButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 2.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}