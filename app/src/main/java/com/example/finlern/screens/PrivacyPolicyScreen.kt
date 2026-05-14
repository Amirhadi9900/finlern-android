package com.example.finlern.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finlern.ui.theme.AuroraBlue1
import com.example.finlern.ui.theme.AuroraBlue2
import com.example.finlern.ui.theme.FinLernBackground

@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    FinLernBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to previous screen",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Privacy Policy",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            AuroraBlue1.copy(alpha = 0.2f),
                                            AuroraBlue2.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Privacy Policy for FinLern",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(bottom = 16.dp)
                                        .semantics { contentDescription = "Privacy Policy for FinLern" }
                                )

                                Text(
                                    text = "Last Updated: February 25, 2025",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .padding(bottom = 16.dp)
                                        .semantics { contentDescription = "Last Updated: February 25, 2025" }
                                )

                                // Section 1
                                PolicySection(
                                    title = "1. Who We Are",
                                    content = "FinLern is developed and operated by Hamid Reza Ghorbani, located at Valkeakoski 37600, Pirkanmaa, Finland. As the data controller, we are responsible for processing your personal data. For questions or concerns about this Privacy Policy or your data, please contact us at:\n" +
                                            "• Email: info@fi.finlern.fi\n" +
                                            "• Address: Valkeakoski 37600, Pirkanmaa, Finland"
                                )

                                // Section 2
                                PolicySection(
                                    title = "2. Information We Collect",
                                    content = "We collect the following types of information when you use FinLern:\n\n" +
                                            "a. Information You Provide\n" +
                                            "• Account Information: When you sign up, we collect your email address (used as your unique identifier) and, optionally, your name for your user profile.\n" +
                                            "• Chat Content: Messages you send, including text and uploaded files (e.g., images, documents up to 100 MB), are stored to enable communication within the App.\n" +
                                            "• Uploaded Files: Files you share in chats, such as PDFs or images, are encrypted and stored temporarily before being uploaded to our servers.\n\n" +
                                            "b. Information Collected Automatically\n" +
                                            "• Device Information: We may collect device-specific data, such as your device type, operating system version, and unique device identifiers (e.g., Android ID), to ensure compatibility and troubleshoot issues.\n" +
                                            "• Usage Data: We collect information about how you use FinLern, including timestamps of messages, login/logout times, and file transfer activities, to improve the App’s functionality.\n" +
                                            "• FCM Tokens: We generate and store Firebase Cloud Messaging (FCM) tokens to send you notifications about new messages or updates.\n\n" +
                                            "c. Information Processed via Third-Party Services\n" +
                                            "FinLern uses Firebase services provided by Google LLC. These services process certain data on our behalf:\n" +
                                            "• Authentication: Firebase Authentication uses your email address to verify your identity and manage logins.\n" +
                                            "• Firestore: Firebase Firestore stores your chat messages, user profiles, and metadata (e.g., file names, timestamps).\n" +
                                            "• Storage: Firebase Storage holds encrypted files you upload, with metadata like file names and encryption keys."
                                )

                                // Section 3
                                PolicySection(
                                    title = "3. How We Use Your Information",
                                    content = "We use your information for the following purposes, based on legal grounds under the GDPR:\n\n" +
                                            "a. To Provide and Operate the App (Contractual Necessity, GDPR Art. 6(1)(b))\n" +
                                            "• Authenticate your account and allow login.\n" +
                                            "• Facilitate chat functionality, including sending and receiving messages and files.\n" +
                                            "• Store and deliver uploaded files securely (encrypted) to intended recipients.\n" +
                                            "• Send push notifications about new messages or updates via FCM.\n\n" +
                                            "b. To Improve and Maintain the App (Legitimate Interest, GDPR Art. 6(1)(f))\n" +
                                            "• Analyze usage patterns to enhance features and performance.\n" +
                                            "• Troubleshoot technical issues and ensure compatibility with your device.\n" +
                                            "• Monitor file transfer activities to prevent abuse (e.g., oversized files).\n\n" +
                                            "c. To Comply with Legal Obligations (Legal Obligation, GDPR Art. 6(1)(c))\n" +
                                            "• Retain data as required by Finnish and EU laws (e.g., for tax or auditing purposes).\n" +
                                            "• Respond to lawful requests from authorities, such as data subject access requests.\n\n" +
                                            "d. With Your Consent (Consent, GDPR Art. 6(1)(a))\n" +
                                            "• If we introduce optional features (e.g., analytics), we’ll ask for your explicit consent before processing additional data."
                                )

                                // Section 4
                                PolicySection(
                                    title = "4. Data Retention",
                                    content = "• Account Information: Retained as long as your account is active. If you delete your account, we remove your email and profile data within 30 days, unless required by law to retain it longer.\n" +
                                            "• Chat Content: Messages and files are stored indefinitely unless you or an authorized admin/teacher delete them. Deleted data is removed from our servers within 30 days.\n" +
                                            "• Temporary Files: Encrypted files in transit (e.g., in cache) are deleted immediately after successful upload/download.\n" +
                                            "• Usage Data: Retained for up to 12 months for analytics, then anonymized or deleted."
                                )

                                // Section 5
                                PolicySection(
                                    title = "5. How We Share Your Information",
                                    content = "We do not sell your personal data. We share it only in these cases:\n\n" +
                                            "a. With Service Providers\n" +
                                            "• Google Firebase: Processes data for authentication, storage, and messaging. Google adheres to GDPR via Data Processing Agreements (see Google’s privacy terms).\n" +
                                            "• Hosting Providers: Our servers may use third-party hosting services in the EU, bound by GDPR-compliant contracts.\n\n" +
                                            "b. Within the App\n" +
                                            "• Chat Participants: Your messages and files are shared with other participants in the chat (e.g., students, teachers).\n" +
                                            "• Admins/Teachers: Authorized admins or teachers (identified by email) may access chats to moderate content or manage groups.\n\n" +
                                            "c. Legal Requirements\n" +
                                            "• We may disclose data if required by law, such as in response to a court order or regulatory request in Finland or the EU."
                                ) { text ->
                                    buildAnnotatedString {
                                        append(text)
                                        val googleLinkStart = text.indexOf("see Google’s privacy terms")
                                        val googleLinkEnd = googleLinkStart + "see Google’s privacy terms".length
                                        addStringAnnotation(
                                            tag = "URL",
                                            annotation = "https://firebase.google.com/terms/data-processing-terms",
                                            start = googleLinkStart,
                                            end = googleLinkEnd
                                        )
                                        addStyle(
                                            SpanStyle(
                                                color = Color(0xFF80DEEA),
                                                textDecoration = TextDecoration.Underline
                                            ),
                                            start = googleLinkStart,
                                            end = googleLinkEnd
                                        )
                                    }
                                }

                                // Section 6
                                PolicySection(
                                    title = "6. Data Security",
                                    content = "We take your data security seriously:\n" +
                                            "• Encryption: Files are encrypted using AES-256 with unique keys and initialization vectors (IVs) before upload. Keys and IVs are stored securely in Firebase Storage metadata, accessible only to chat participants.\n" +
                                            "• Authentication: Firebase Authentication uses secure email-based login.\n" +
                                            "• Transmission: Data is transmitted over HTTPS, ensuring end-to-end encryption between your device and our servers.\n" +
                                            "• Access Controls: Only authorized users (chat participants, admins, teachers) can access specific data, enforced by Firebase security rules.\n\n" +
                                            "Despite these measures, no system is 100% secure. If a breach occurs, we’ll notify you and the Finnish Data Protection Ombudsman within 72 hours, as required by GDPR."
                                )

                                // Section 7
                                PolicySection(
                                    title = "7. Your Rights Under GDPR",
                                    content = "As an EU resident, you have the following rights:\n" +
                                            "• Access (Art. 15): Request a copy of your personal data.\n" +
                                            "• Rectification (Art. 16): Correct inaccurate data.\n" +
                                            "• Erasure (Art. 17): Request deletion of your data (\"right to be forgotten\"), subject to legal retention obligations.\n" +
                                            "• Restriction (Art. 18): Limit how we process your data.\n" +
                                            "• Data Portability (Art. 20): Receive your data in a machine-readable format.\n" +
                                            "• Objection (Art. 21): Object to processing based on legitimate interests.\n" +
                                            "• Withdraw Consent (Art. 7): Revoke consent for optional features at any time.\n\n" +
                                            "To exercise these rights, contact us at info@fi.finlern.fi. We’ll respond within one month, extendable to three months for complex requests. If unsatisfied, you may lodge a complaint with the Finnish Data Protection Ombudsman (www.tietosuoja.fi)."
                                ) { text ->
                                    buildAnnotatedString {
                                        append(text)
                                        val ombudsmanLinkStart = text.indexOf("www.tietosuoja.fi")
                                        val ombudsmanLinkEnd = ombudsmanLinkStart + "www.tietosuoja.fi".length
                                        addStringAnnotation(
                                            tag = "URL",
                                            annotation = "https://www.tietosuoja.fi",
                                            start = ombudsmanLinkStart,
                                            end = ombudsmanLinkEnd
                                        )
                                        addStyle(
                                            SpanStyle(
                                                color = Color(0xFF80DEEA),
                                                textDecoration = TextDecoration.Underline
                                            ),
                                            start = ombudsmanLinkStart,
                                            end = ombudsmanLinkEnd
                                        )
                                    }
                                }

                                // Section 8
                                PolicySection(
                                    title = "8. International Data Transfers",
                                    content = "FinLern uses Firebase, hosted by Google, which may process data in the EU or US. Google complies with GDPR through Standard Contractual Clauses (SCCs) and additional safeguards. We ensure all transfers meet EU adequacy standards."
                                )

                                // Section 9
                                PolicySection(
                                    title = "9. Children’s Privacy",
                                    content = "FinLern is intended for educational use and may be used by individuals under 16 (e.g., students). If users are under 16, we process their data only with verifiable parental consent, as required by GDPR (Art. 8). Teachers or admins must ensure consent is obtained where applicable."
                                )

                                // Section 10
                                PolicySection(
                                    title = "10. Changes to This Privacy Policy",
                                    content = "We may update this policy to reflect changes in our practices or legal requirements. We’ll notify you via email or in-app notice at least 30 days before significant changes take effect. The latest version is always available in the App."
                                )

                                // Section 11
                                PolicySection(
                                    title = "11. Contact Us",
                                    content = "For questions, requests, or feedback about this Privacy Policy, please reach out:\n" +
                                            "• Email: info@fi.finlern.fi\n" +
                                            "• Address: Valkeakoski 37600, Pirkanmaa, Finland"
                                )
                            }
                        }
                    }
                }
            }

            // I Understand Button
            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraBlue1,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
            ) {
                Text(
                    text = "I Understand",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: String,
    annotatedText: (String) -> AnnotatedString = { buildAnnotatedString { append(it) } }
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .animateContentSize()
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { contentDescription = title }
        )
        Text(
            text = annotatedText(content),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clickable {
                    annotatedText(content).getStringAnnotations("URL", 0, content.length)
                        .firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                }
                .semantics { contentDescription = content }
        )
    }
}