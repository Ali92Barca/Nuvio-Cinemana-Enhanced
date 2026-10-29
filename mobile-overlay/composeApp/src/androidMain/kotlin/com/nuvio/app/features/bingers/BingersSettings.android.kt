package com.nuvio.app.features.bingers

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.core.auth.AuthRepository

@Composable
actual fun BingersSettingsContent(isTablet: Boolean) {
    val profile by ProfileRepository.state.collectAsStateWithLifecycle()
    val auth by AuthRepository.state.collectAsStateWithLifecycle()
    val key = remember(profile, auth) { BingersPlatform.profileKey() }
    val state by BingersPlatform.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var link by remember(key) { mutableStateOf("") }
    var relink by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { BingersPlatform.refresh() }
    Card(Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bingers • تجريبي", style = MaterialTheme.typography.titleLarge)
            Text("يسجّل الفيلم أو الحلقة كمشاهَدة عند اكتمال 80٪، دون تكرار عدد المشاهدات. لا يرسل تقدّم التشغيل المباشر.")
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            if (key == null) Text("اختر ملف مستخدم في نوفيو أولاً")
            if (state.linked) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("مزامنة المشاهدات تلقائياً", Modifier.weight(1f))
                    Switch(checked = state.enabled, enabled = !state.busy, onCheckedChange = BingersPlatform::setEnabled)
                }
                Text("بانتظار الإرسال: ${state.pending} • تعذرت مطابقتها: ${state.unmatched}")
                TextButton(onClick = BingersPlatform::retry, enabled = !state.busy && state.enabled) { Text("فحص الجلسة وإعادة محاولة المزامنة") }
                Row {
                    TextButton(onClick = { relink = true }, enabled = !state.busy) { Text("إعادة الربط") }
                    TextButton(onClick = { BingersPlatform.disconnect(); relink = false }, enabled = !state.busy) { Text("فصل الحساب") }
                }
            }
            if (!state.linked || relink) {
                Text("افتح صفحة Bingers، اطلب رابط الدخول، وانسخ الرابط من الإيميل بدون فتحه. الصقه هنا داخل التطبيق فقط.")
                TextButton(onClick = { uri.openUri("https://bingers.app/mobile-signin") }, enabled = !state.busy) { Text("فتح تسجيل دخول Bingers") }
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("رابط الدخول من الإيميل") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth())
                Button(onClick = { val value = link; link = ""; relink = false; BingersPlatform.link(value) },
                    enabled = key != null && link.isNotBlank() && !state.busy) { Text("ربط الحساب") }
                Text("إمكانية المزامنة بحساب مجاني تحتاج تجربة فعلية. إذا طلب Bingers اشتراكاً، يتوقف الإرسال وتظهر الرسالة.")
            }
        }
    }
}
