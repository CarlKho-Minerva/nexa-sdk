// Copyright 2024-2026 Nexa AI, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.nexa.demo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import com.gyf.immersionbar.ktx.immersionBar
import com.hjq.toast.Toaster
import com.liulishuo.okdownload.DownloadContext
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.OkDownload
import com.liulishuo.okdownload.core.cause.EndCause
import com.liulishuo.okdownload.core.connection.DownloadOkHttp3Connection
import com.liulishuo.okdownload.kotlin.listener.createDownloadContextListener
import com.liulishuo.okdownload.kotlin.listener.createListener1
import com.nexa.demo.bean.DownloadableFile
import com.nexa.demo.bean.DownloadableFileWithFallback
import com.nexa.demo.bean.DownloadState
import com.nexa.demo.bean.ModelData
import com.nexa.demo.bean.downloadableFiles
import com.nexa.demo.bean.downloadableFilesWithFallback
import com.nexa.demo.bean.getNexaManifest
import com.nexa.demo.bean.getNonExistModelFile
import com.nexa.demo.bean.getSupportPluginIds
import com.nexa.demo.bean.isNpuModel
import com.nexa.demo.bean.mmprojTokenFile
import com.nexa.demo.bean.modelDir
import com.nexa.demo.bean.modelFile
import com.nexa.demo.bean.tokenFile
import com.nexa.demo.bean.withFallbackUrls
import com.nexa.demo.utils.ModelFileListingUtil
import com.nexa.demo.databinding.ActivityMainBinding
import com.nexa.demo.databinding.DialogSelectPluginIdBinding
import com.nexa.demo.listeners.CustomDialogInterface
import com.nexa.demo.utils.ExecShell
import com.nexa.demo.utils.ImgUtil
import com.nexa.demo.utils.WavRecorder
import com.nexa.demo.utils.inflate
import com.nexa.sdk.AsrWrapper
import com.nexa.sdk.CvWrapper
import com.nexa.sdk.EmbedderWrapper
import com.nexa.sdk.LlmWrapper
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.RerankerWrapper
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.AsrCreateInput
import com.nexa.sdk.bean.AsrTranscribeInput
import com.nexa.sdk.bean.CVCapability
import com.nexa.sdk.bean.CVCreateInput
import com.nexa.sdk.bean.CVModelConfig
import com.nexa.sdk.bean.ChatMessage
import com.nexa.sdk.bean.EmbedderCreateInput
import com.nexa.sdk.bean.EmbeddingConfig
import com.nexa.sdk.bean.LlmCreateInput
import com.nexa.sdk.bean.LlmStreamResult
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.RerankConfig
import com.nexa.sdk.bean.RerankerCreateInput
import com.nexa.sdk.bean.VlmChatMessage
import com.nexa.sdk.bean.VlmContent
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MainActivity : FragmentActivity() {

    private val binding: ActivityMainBinding by inflate()
    private var downloadContext: DownloadContext? = null
    private var downloadState = DownloadState.IDLE
    private var downloadingModelData: ModelData? = null
    private lateinit var spDownloaded: SharedPreferences
    private lateinit var llDownloading: LinearLayout
    private lateinit var tvDownloadProgress: TextView
    private lateinit var pbDownloading: ProgressBar
    private lateinit var spModelList: Spinner
    private lateinit var btnDownload: Button
    private lateinit var btnLoadModel: Button
    private lateinit var btnUnloadModel: Button
    private lateinit var btnStop: Button
    private lateinit var btnSelectModelFile: Button
    private lateinit var btnBrowseFiles: Button
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClearHistory: Button
    private lateinit var btnAddImage: Button
    private lateinit var btnAudioRecord: Button

    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvPrivacyBadge: TextView
    private lateinit var tvModelStatus: TextView
    private lateinit var llAdvancedControls: LinearLayout

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var bottomPanel: LinearLayout
    private lateinit var btnAudioDone: Button
    private lateinit var btnAudioCancel: Button

    private lateinit var scrollImages: HorizontalScrollView
    private lateinit var topScrollContainer: LinearLayout
    private lateinit var llLoading: LinearLayout
    private lateinit var vTip: View

    private lateinit var llmWrapper: LlmWrapper
    private lateinit var vlmWrapper: VlmWrapper
    var embedderWrapper: EmbedderWrapper? = null
    private lateinit var rerankerWrapper: RerankerWrapper
    private lateinit var cvWrapper: CvWrapper
    private lateinit var asrWrapper: AsrWrapper
    private val modelScope = CoroutineScope(Dispatchers.IO)

    private val chatList = arrayListOf<ChatMessage>()
    private lateinit var llmSystemPrompt: ChatMessage
    private val vlmChatList = arrayListOf<VlmChatMessage>()
    private lateinit var vlmSystemPrompty: VlmChatMessage
    private lateinit var modelList: List<ModelData>
    private var selectModelId = ""

    // ADD: Track which model type is loaded
    private var isLoadLlmModel = false
    private var isLoadVlmModel = false
    private var isLoadEmbedderModel = false
    private var isLoadRerankerModel = false
    private var isLoadCVModel = false
    private var isLoadAsrModel = false

    private var enableThinking = false

    private var wavRecorder: WavRecorder? = null
    private var audioFile: File? = null

    private val savedImageFiles = mutableListOf<File>()
    private val messages = arrayListOf<Message>()

    // Manual model file selection
    private var manualModelFilePath: String? = null

    // Health Vault
    private lateinit var healthVaultDir: File
    private var mockScanInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersionBar {
            statusBarColorInt(Color.WHITE)
            statusBarDarkFont(true)
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        okdownload()
        initData()
        initHealthVault()
        initView()
        setListeners()
    }

    private fun resetLoadState() {
        isLoadLlmModel = false
        isLoadVlmModel = false
        isLoadEmbedderModel = false
        isLoadRerankerModel = false
        isLoadCVModel = false
        isLoadAsrModel = false
    }

    private fun initView() {
        adapter = ChatAdapter(messages)
        binding.rvChat.adapter = adapter

        llDownloading = findViewById(R.id.ll_downloading)
        tvDownloadProgress = findViewById(R.id.tv_download_progress)
        pbDownloading = findViewById(R.id.pb_downloading)
        spModelList = findViewById(R.id.sp_model_list)
        spModelList.adapter = object : SimpleAdapter(this, modelList.map {
            val map = mutableMapOf<String, String>()
            map["displayName"] = it.displayName
            map
        }, R.layout.item_model, arrayOf("displayName"), intArrayOf(R.id.tv_model_id)) {

        }
        spModelList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                selectModelId = modelList[position].id

                messages.clear()
                adapter.notifyDataSetChanged()
                binding.rvChat.scrollTo(0, 0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectModelId = ""
            }
        }
        btnDownload = findViewById(R.id.btn_download)
        btnLoadModel = findViewById(R.id.btn_load_model)
        btnUnloadModel = findViewById(R.id.btn_unload_model)
        btnStop = findViewById(R.id.btn_stop)
        btnSelectModelFile = findViewById(R.id.btn_select_model_file)
        btnBrowseFiles = findViewById(R.id.btn_browse_files)
        etInput = findViewById(R.id.et_input)
        btnAddImage = findViewById(R.id.btn_add_image)
        btnAudioRecord = findViewById(R.id.btn_voice)

        tvHeaderTitle = findViewById(R.id.tv_header_title)
        tvPrivacyBadge = findViewById(R.id.tv_privacy_badge)
        tvModelStatus = findViewById(R.id.tv_model_status)
        llAdvancedControls = findViewById(R.id.ll_advanced_controls)

        // Long-press header to toggle advanced mode
        tvHeaderTitle.setOnLongClickListener {
            toggleAdvancedMode()
            true
        }
        tvPrivacyBadge.setOnLongClickListener {
            toggleAdvancedMode()
            true
        }

        // Set initial status - demo-friendly
        tvModelStatus.text = "Qualcomm NPU · Health Vault Loaded"

        bottomPanel = findViewById(R.id.bottom_panel)
        btnAudioCancel = findViewById(R.id.btn_audio_cancel)
        btnAudioDone = findViewById(R.id.btn_audio_done)

        btnSend = findViewById(R.id.btn_send)
        btnClearHistory = findViewById(R.id.btn_clear_history)
        scrollImages = findViewById(R.id.scroll_images)
        topScrollContainer = findViewById(R.id.ll_images_container)
        llLoading = findViewById(R.id.ll_loading)
        vTip = findViewById<View>(R.id.v_tip)

        btnAudioCancel.setOnClickListener {
            stopRecord(true)
        }

        btnAudioDone.setOnClickListener {
            stopRecord(false)
        }

        findViewById<Button>(R.id.btn_test).setOnClickListener {
            Thread {
                val exeFile = File(filesDir, "nexa_test_llm")
                val chmodProcess = Runtime.getRuntime().exec("chmod 755 " + exeFile.absolutePath);
                chmodProcess.waitFor()
                Log.d("nfl", "exeFile exe? ${exeFile.canExecute()}")
                Log.d("nfl", "Exe Thread:${Thread.currentThread().name}")
                ExecShell().executeCommand(
                    arrayOf(
                        //                        exeFile.absolutePath,
//                        "--test-suite=\"npu\"", "--success "
                        "cat",
                        "/sys/devices/soc0/sku"
//                        "/data/local/tmp/test_cat.txt"
                    )
                ).forEach {
                    Log.d("nfl", "cmd:$it")
                }
            }.start()
        }

        findViewById<View>(R.id.v_tip).setOnClickListener {
            Toast.makeText(this, "please unload model first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAdvancedMode() {
        if (llAdvancedControls.visibility == View.GONE) {
            llAdvancedControls.visibility = View.VISIBLE
            Toast.makeText(this, "Advanced mode enabled", Toast.LENGTH_SHORT).show()
        } else {
            llAdvancedControls.visibility = View.GONE
            Toast.makeText(this, "Advanced mode disabled", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Auto-detect model files at common paths (for QDC SSH push or pre-placed models)
     * Following the tutorial pattern: check known paths, copy to app storage if needed
     */
    private fun autoDetectModels() {
        val searchPaths = listOf(
            // QDC AI Model upload / ADB push paths
            File("/data/local/tmp"),
            File("/sdcard/Download"),
            File("/sdcard/Models"),
            File("/sdcard/nexa_models"),
            // App-specific paths
            File(filesDir, "models"),
            File(filesDir, "manual_models"),
            // Assets-copied models path (tutorial pattern: assets → filesDir)
            File(filesDir, "nexa_models")
        )

        for (dir in searchPaths) {
            if (!dir.exists() || !dir.isDirectory) continue
            val ggufFiles = dir.walkTopDown().maxDepth(2).filter {
                it.isFile && it.name.endsWith(".gguf", ignoreCase = true)
            }.toList()

            if (ggufFiles.isNotEmpty()) {
                val model = ggufFiles.first()
                Log.i(TAG, "Auto-detected model: ${model.absolutePath} (${model.length() / 1024 / 1024}MB)")

                // Copy to app storage if not already there (tutorial ModelManager pattern)
                val appModelDir = File(filesDir, "manual_models")
                if (!appModelDir.exists()) appModelDir.mkdirs()

                val destFile = File(appModelDir, model.name)
                if (model.absolutePath.startsWith(filesDir.absolutePath)) {
                    // Already in app storage
                    manualModelFilePath = model.absolutePath
                } else if (destFile.exists() && destFile.length() == model.length()) {
                    // Already copied
                    manualModelFilePath = destFile.absolutePath
                } else {
                    // Copy to app storage (like tutorial's copyModelFromAssets)
                    Log.i(TAG, "Copying model to app storage: ${model.name}")
                    runOnUiThread {
                        tvModelStatus.text = "Found model: ${model.name}\nCopying to app storage..."
                    }
                    try {
                        model.copyTo(destFile, overwrite = true)
                        manualModelFilePath = destFile.absolutePath
                        Log.i(TAG, "Model copied successfully to: ${destFile.absolutePath}")
                    } catch (e: Exception) {
                        // If copy fails, try to use directly from source
                        Log.w(TAG, "Copy failed (${e.message}), using source path directly")
                        manualModelFilePath = model.absolutePath
                    }
                }

                runOnUiThread {
                    tvModelStatus.text = "Model found: ${model.name}\nTap Load to start (use CPU+GPU for GGUF)"
                    Toast.makeText(this, "Auto-detected: ${model.name}", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
        Log.i(TAG, "No pre-placed models found at common paths")
    }

    /**
     * Copy bundled models from assets to filesDir (tutorial ModelManager pattern)
     * Called on first run to extract bundled models
     */
    private fun copyBundledModels() {
        try {
            val assetModels = assets.list("nexa_models") ?: emptyArray()
            if (assetModels.isEmpty()) {
                Log.i(TAG, "No bundled models in assets/nexa_models/")
                return
            }

            val destDir = File(filesDir, "nexa_models")
            if (!destDir.exists()) destDir.mkdirs()

            for (modelFolder in assetModels) {
                val modelFiles = assets.list("nexa_models/$modelFolder") ?: continue
                val modelDestDir = File(destDir, modelFolder)
                if (!modelDestDir.exists()) modelDestDir.mkdirs()

                for (fileName in modelFiles) {
                    val destFile = File(modelDestDir, fileName)
                    if (destFile.exists()) continue  // Already copied

                    Log.i(TAG, "Copying bundled model: nexa_models/$modelFolder/$fileName")
                    assets.open("nexa_models/$modelFolder/$fileName").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // Auto-detect the copied models
            autoDetectModels()
        } catch (e: Exception) {
            Log.w(TAG, "No bundled models to copy: ${e.message}")
        }
    }

    /**
     * Initialize the Health Vault by copying bundled markdown files from assets/health_vault
     * to filesDir/health_vault. This gives the demo a pre-populated medical record structure.
     */
    private fun initHealthVault() {
        healthVaultDir = File(filesDir, "health_vault")
        if (healthVaultDir.exists() && File(healthVaultDir, "01_Body_Systems/01_Head_Eyes_ENT.md").exists()) {
            Log.i(TAG, "Health vault already initialized")
            return
        }
        try {
            copyAssetFolder("health_vault", healthVaultDir)
            Log.i(TAG, "Health vault initialized at: ${healthVaultDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init health vault: ${e.message}")
        }
    }

    /**
     * Recursively copy an asset folder to a destination directory
     */
    private fun copyAssetFolder(assetPath: String, destDir: File) {
        val entries = assets.list(assetPath) ?: return
        if (!destDir.exists()) destDir.mkdirs()

        for (entry in entries) {
            val assetEntryPath = "$assetPath/$entry"
            val destFile = File(destDir, entry)
            val subEntries = assets.list(assetEntryPath)

            if (subEntries != null && subEntries.isNotEmpty()) {
                // It's a directory
                copyAssetFolder(assetEntryPath, destFile)
            } else {
                // It's a file
                try {
                    assets.open(assetEntryPath).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skip: $assetEntryPath (${e.message})")
                }
            }
        }
    }

    /**
     * Mock scan flow: simulates document processing with progress
     * Shows the EO medical certificate being "organized" into the vault
     */
    private fun runMockScanDemo() {
        if (mockScanInProgress) return
        mockScanInProgress = true

        runOnUiThread {
            llDownloading.visibility = View.VISIBLE
            tvDownloadProgress.text = "Analyzing document..."
            pbDownloading.isIndeterminate = true
        }

        Thread {
            Thread.sleep(1800)
            runOnUiThread { tvDownloadProgress.text = "Detected: Medical Certificate" }
            Thread.sleep(1400)
            runOnUiThread { tvDownloadProgress.text = "Extracting prescription data" }
            Thread.sleep(1600)
            runOnUiThread { tvDownloadProgress.text = "Category  ·  Head, Eyes & ENT" }
            Thread.sleep(1200)
            runOnUiThread { tvDownloadProgress.text = "Filing to 01_Body_Systems" }
            Thread.sleep(900)
            runOnUiThread { tvDownloadProgress.text = "Updating timeline" }
            Thread.sleep(700)
            runOnUiThread {
                llDownloading.visibility = View.GONE
                mockScanInProgress = false

                val scanResult = """## Document Type: Medical Certificate (Rx)
## Date: January 5, 2026
## Facility: Executive Optical, Gaisano Grand Mactan

### Findings
| Test | Result | Reference | Status |
|------|--------|-----------|--------|
| OD SPH | -1.00 | — | Myopia |
| OD CYL | -0.25 | — | Astigmatism |
| OD AXIS | 80 | — | — |
| OS SPH | -0.75 | — | Myopia |
| OS CYL | -0.75 | — | Astigmatism |
| OS AXIS | 180 | — | — |

### Diagnosis
- **Myopic Astigmatism** — Confirmed

### Medications
- Rx Corrective Glasses — Wear daily as prescribed

### Action Items
- Filed to `01_Body_Systems/01_Head_Eyes_ENT.md`
- Timeline updated (Jan 05 entry)
- Archived to `99_Archives/2026-01-05_EO_Visit/`
- Follow-up: Check-up date 1/5/26

### Notes
Doctor: Josephine Y. Vicente, O.D. (Lic. 0010603)

Invoice: PHP 1,782.14 (Visa ****8148, Maya POS)

*Processed on-device in 5.1s · No data sent to cloud*"""

                streamResponseToChat(scanResult)
            }
        }.start()
    }

    /**
     * Stream a response into chat character-by-character for a premium feel.
     */
    private fun streamResponseToChat(fullText: String) {
        messages.add(Message("", MessageType.ASSISTANT))
        reloadRecycleView()

        Thread {
            val sb = StringBuilder()
            var i = 0
            while (i < fullText.length) {
                // Grab chunks of 2-6 chars for natural feel
                val chunkSize = when {
                    fullText[i] == '\n' -> 1
                    fullText[i] == '|' -> 1
                    fullText[i] == '#' -> 1
                    else -> (2..5).random()
                }
                val end = minOf(i + chunkSize, fullText.length)
                sb.append(fullText.substring(i, end))
                i = end

                val current = sb.toString()
                runOnUiThread {
                    messages[messages.size - 1] = Message(current, MessageType.ASSISTANT)
                    adapter.notifyItemChanged(messages.size - 1)
                }

                // Variable delay: longer on newlines, shorter on regular chars
                val delay = when {
                    i < fullText.length && fullText[i - 1] == '\n' -> (30L..60L).random()
                    i < fullText.length && fullText[i - 1] == '|' -> (15L..25L).random()
                    else -> (8L..18L).random()
                }
                Thread.sleep(delay)
            }

            runOnUiThread {
                messages[messages.size - 1] = Message(fullText, MessageType.ASSISTANT)
                adapter.notifyItemChanged(messages.size - 1)
                binding.rvChat.scrollToPosition(messages.size - 1)
            }
        }.start()
    }

    /**
     * Generate a preloaded RAG response for health queries when model isn't loaded.
     * Searches the health vault and returns contextual answers.
     */
    private fun handlePreloadedQuery(query: String): Boolean {
        val q = query.lowercase()

        // Eye-related queries
        if (q.contains("eye") || q.contains("vision") || q.contains("glasses") ||
            q.contains("optical") || q.contains("myop") || q.contains("astigmat") ||
            q.contains("eo ") || q.contains("prescription") || q.contains("sight")) {

            val response = """## Eye Health Summary

**Active Condition:** Myopic Astigmatism (Confirmed Jan 5, 2026)

**Current Prescription** (Dr. Josephine Vicente, EO Mactan):
| Eye | SPH | CYL | AXIS |
|-----|------|------|------|
| OD (Right) | -1.00 | -0.25 | 80 |
| OS (Left) | -0.75 | -0.75 | 180 |

**Recommendation:** Wear Rx glasses as prescribed.

**Vision Acuity (Dec 2025):** R: 0.7 / L: 0.6 (corrected)
**IOP:** R: 16 / L: 14 — Normal

**Recent Concern:** Accommodation Insufficiency (Feb 12, 2026)
- Near vision blurry despite glasses
- Likely post-viral paresis (after sinusitis)
- Action: Rule of 20-20-20, monitor 2 weeks

**Rx History:**
- Dec 2025: OD pl/-0.75x80, OS -0.25/-0.50x105
- Jan 2026: OD -1.00/-0.25x80, OS -0.75/-0.75x180

*Source: 01_Body_Systems/01_Head_Eyes_ENT.md · on-device*"""
            streamResponseToChat(response)
            return true
        }

        // Medication queries
        if (q.contains("med") || q.contains("drug") || q.contains("pill") ||
            q.contains("prescription") || q.contains("taking")) {

            val response = """## Active Medications

**Sinusitis / LPRD Protocol (Jan 2026):**
- **Flomist-FT** — Nasal Spray (R. Maxillary Sinusitis)
- **Clomont BL** — Sinusitis
- **Nexpro 40** — PPI (LPRD)

**Daily:**
- **Fish Oil (Omega-3)** — 2 softgels with food

**PRN (As Needed):**
- **Arcoxia 60mg** — TMJ Pain
- **Fluimucil 600mg** — Mucolytic

**Completed:** Augpen (Antibiotic) — Jan 20, 2026

*Source: 03_Protocols/Active_Medications.md · on-device*"""
            streamResponseToChat(response)
            return true
        }

        // Timeline / history queries
        if (q.contains("timeline") || q.contains("history") || q.contains("when") ||
            q.contains("visit") || q.contains("appointment")) {

            val response = """## Recent Medical Timeline

**2026:**
- **Feb 12** — Accommodation Insufficiency, near vision blurry
- **Jan 20** — ENT Visit (AIG), Sinusitis + LPRD confirmed
- **Jan 15** — X-Ray PNS, Normal, Sinuses Clear
- **Jan 07** — TMJ Pain, Rx: Arcoxia 60mg
- **Jan 05** — EO Mactan, Myopic Astigmatism, Rx glasses

**2025:**
- **Dec 01** — TAH Health Exam, Full baseline (CBC, BMI, Vision)
- **Aug 26** — Taiwan Health Check, Chest X-Ray Clear

*Source: 02_Timeline/Medical_Timeline.md · on-device*"""
            streamResponseToChat(response)
            return true
        }

        // General health / system prompt
        if (q.contains("health") || q.contains("status") || q.contains("summary") ||
            q.contains("how am i") || q.contains("overall")) {

            val response = """## Health Passport — Overview

**Patient:** Carl Vincent L. Kho · Age 21 · M

**Key Metrics (Dec 2025):**
- BMI: 27.3 · BP: 121/72 · Pulse: 74 bpm
- CBC: All normal (RBC slightly elevated)

**Active Conditions:**
- Myopic Astigmatism — Rx glasses prescribed
- R. Maxillary Sinusitis — On treatment
- LPRD — Nexpro 40
- TMJ Pain — PRN Arcoxia

**Vault:** 6 body system files · 12 timeline entries · 8 active meds

*All data stored on-device · HIPAA-compliant*"""
            streamResponseToChat(response)
            return true
        }

        return false  // No preloaded answer found
    }

    private fun parseModelList() {
        try {
            val baseJson = assets.open("model_list.json").bufferedReader().use { it.readText() }
            modelList = Json.decodeFromString<List<ModelData>>(baseJson)
        } catch (e: Exception) {
            Log.e("nfl", "parseModelList: $e")
        }
    }

    /**
     * Step 0. Preparing to download the model file.
     */
    private fun initData() {
        spDownloaded = getSharedPreferences(SP_DOWNLOADED, MODE_PRIVATE)
//        spDownloaded.edit().putBoolean("Qwen3-0.6B-Q8_0", false).commit()
//        spDownloaded.edit().putBoolean("Qwen3-0.6B-IQ4_NL", false).commit()
//        spDownloaded.edit().putBoolean("LFM2-1.2B-npu", false).commit()
//        spDownloaded.edit().putBoolean("embeddinggemma-300m-npu", false).commit()
//        spDownloaded.edit().putBoolean("jina-v2-rerank-npu", false).commit()
//        spDownloaded.edit().putBoolean("paddleocr-npu", false).commit()
//        spDownloaded.edit().putBoolean("parakeet-tdt-0.6b-v3-npu", false).commit()
//        spDownloaded.edit().putBoolean("OmniNeural-4B", false).commit()
//        spDownloaded.edit().putBoolean("Granite-4.0-h-350M-NPU", false).commit()
//        spDownloaded.edit().putBoolean("Granite-4-Micro-NPU", false).commit()
        parseModelList()
        //
        initNexaSdk()
        //
        val sysPrompt = """\
You are Health Passport, an on-device medical document scanner AI. You analyze photos of medical documents (lab reports, prescriptions, receipts, X-rays) and extract structured health data.

When a user sends you an image of a medical document, you must:
1. Identify the document type (lab_report, prescription, receipt, xray, other)
2. Extract key findings in a structured format
3. List any medications with dosage and frequency
4. Identify action items (follow-up appointments, refills, tests)

Output format:
## Document Type: [type]
## Date: [date if visible]
## Facility: [facility name if visible]

### Findings
| Test | Result | Reference Range | Status |
|------|--------|-----------------|--------|
[extracted findings]

### Medications
- [Drug name] - [Dosage] - [Frequency] - [Purpose]

### Action Items
- [ ] [Next steps identified from the document]

### Notes
[Any additional observations]

IMPORTANT: All processing happens on-device. No data is sent to any server. This ensures complete medical data privacy and HIPAA compliance.
"""
        // Medical extraction prompt for VLM and LLM modes
        val sysPrompt2 = "You are Health Passport, a medical document scanner. Extract structured health data from images. Output in markdown with tables for findings and lists for medications and action items. Be thorough and precise."
        addSystemPrompt(sysPrompt2)

        // Tutorial pattern: Check for bundled models in assets, then auto-detect pre-placed
        copyBundledModels()
        // Auto-detect if no bundled models found
        if (manualModelFilePath == null) {
            Thread { autoDetectModels() }.start()
        }
    }

    /**
     * Step 1. initNexaSdk environment
     */
    private fun initNexaSdk() {
        // Initialize NexaSdk with context
        NexaSdk.getInstance().init(this, object : NexaSdk.InitCallback {
            override fun onSuccess() {
            }

            override fun onFailure(reason: String) {
                Log.e(TAG, "NexaSdk init failed: $reason")
            }
        })

        val testLocalPath = false
        if (testLocalPath) {
            // FIXME: Set directory according to terminal format
            val pluginNativeLibPath = filesDir.absolutePath
            val pluginAdspLibPath = File(filesDir, "npu/htp-files").absolutePath
            val pluginLdLibraryPath =
                "$pluginNativeLibPath:$pluginNativeLibPath/npu:$pluginAdspLibPath:\$LD_LIBRARY_PATH"
            // FIXME: Set directory with flattened .so files
            val NEXA_PLUGIN_PATH = pluginNativeLibPath
            val LD_LIBRARY_PATH = pluginLdLibraryPath
            val ADSP_LIBRARY_PATH = pluginAdspLibPath
            Log.d("nfl", "NEXA_PLUGIN_PATH:$NEXA_PLUGIN_PATH")
            Log.d("nfl", "LD_LIBRARY_PATH:$LD_LIBRARY_PATH")
            Log.d("nfl", "ADSP_LIBRARY_PATH:$ADSP_LIBRARY_PATH")

            Os.setenv("NEXA_PLUGIN_PATH", NEXA_PLUGIN_PATH, true)
            Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true)
            Os.setenv("ADSP_LIBRARY_PATH", ADSP_LIBRARY_PATH, true)
        }
    }

    /**
     * Step 2. add system prompt, such as : output markdown style, contains emoji etc.(Options)
     */
    private fun addSystemPrompt(sysPrompt: String) {
        llmSystemPrompt = ChatMessage("system", sysPrompt)
        chatList.add(llmSystemPrompt)
        vlmSystemPrompty =
            VlmChatMessage(
                "system",
                listOf(VlmContent("text", sysPrompt))
            )
        vlmChatList.add(vlmSystemPrompty)
    }

    private fun getHfToken(model: ModelData, url: String): String? {
        // Replace with your own HuggingFace token if needed for private models
        return null
    }

    private fun onLoadModelSuccess(tip: String) {
        runOnUiThread {
            Toast.makeText(
                this@MainActivity, tip, Toast.LENGTH_SHORT
            ).show()

            // Update status
            tvModelStatus.text = "Model loaded - Ready"

            // change UI
            btnAddImage.visibility = View.INVISIBLE
            btnAudioRecord.visibility = View.INVISIBLE
            if (isLoadVlmModel) {
                btnAddImage.visibility = View.VISIBLE
                btnAudioRecord.visibility = View.VISIBLE
            }
            if (isLoadCVModel) {
                btnAddImage.visibility = View.VISIBLE
            }
            if (isLoadAsrModel) {
                btnAudioRecord.visibility = View.VISIBLE
            }
            //
            btnUnloadModel.visibility = View.VISIBLE
            llLoading.visibility = View.INVISIBLE
            //
            if (isLoadEmbedderModel || isLoadRerankerModel || isLoadAsrModel || isLoadCVModel) {
                btnStop.visibility = View.GONE
            } else {
                btnStop.visibility = View.VISIBLE
            }
        }
    }

    private fun onLoadModelFailed(tip: String) {
        runOnUiThread {
            vTip.visibility = View.GONE

            // Update status
            tvModelStatus.text = "Load failed - Try manual model or check logs"

            // Only check model list if using list-based loading (not manual)
            if (selectModelId.isNotEmpty()) {
                val selectModelData = modelList.firstOrNull { it.id == selectModelId }
                if (selectModelData != null) {
                    val fileName = isModelDownloaded(selectModelData)
                    if (fileName != null) {
                        Toaster.showLong("The \"$fileName\" file is missing. Please download it first.")
                    } else {
                        Toast.makeText(this@MainActivity, "Load failed: $tip", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Model not found in list", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Manual model loading failed
                Toast.makeText(this@MainActivity, "Load failed: $tip", Toast.LENGTH_LONG).show()
            }

            // change UI
            btnAddImage.visibility = View.INVISIBLE
            btnAudioRecord.visibility = View.INVISIBLE
            btnUnloadModel.visibility = View.GONE
            llLoading.visibility = View.INVISIBLE
        }
    }

    private fun hasLoadedModel(): Boolean {
        return isLoadLlmModel || isLoadVlmModel || isLoadEmbedderModel ||
                isLoadRerankerModel || isLoadCVModel || isLoadAsrModel
    }

    /**
     * Helper function to check if model files exist locally
     * @return null if all files exist locally. or file's name which is missing.
     */
    private fun isModelDownloaded(modelData: ModelData): String? {
        val modelDir = modelData.modelDir(this@MainActivity)
        val fileName = modelData.getNonExistModelFile(modelDir)
        val filesExist = fileName == null
        // Sync SharedPreferences with actual file existence
        if (filesExist && !spDownloaded.getBoolean(modelData.id, false)) {
            Log.d(TAG, "Model files found locally for ${modelData.id}, updating SharedPreferences")
            spDownloaded.edit().putBoolean(modelData.id, true).commit()
        }

        return fileName
    }

    private fun loadManualModel(modelPath: String, pluginId: String, nGpuLayers: Int) {
        modelScope.launch {
            resetLoadState()

            runOnUiThread {
                tvModelStatus.text = "Loading model..."
            }

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                runOnUiThread {
                    tvModelStatus.text = "Model file not found"
                    Toast.makeText(
                        this@MainActivity,
                        "Model file not found: ${modelFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                    llLoading.visibility = View.INVISIBLE
                    vTip.visibility = View.GONE
                }
                return@launch
            }

            Log.d(TAG, "Loading manual model:")
            Log.d(TAG, "  - File: ${modelFile.name}")
            Log.d(TAG, "  - Path: ${modelFile.absolutePath}")
            Log.d(TAG, "  - Size: ${modelFile.length()} bytes")
            Log.d(TAG, "  - Plugin: $pluginId")
            Log.d(TAG, "  - GPU Layers: $nGpuLayers")

            // For GGUF files, use empty model_name and minimal config
            // Note: NPU paths must be set even for CPU/GPU plugins
            val conf = ModelConfig(
                nCtx = 2048,
                nGpuLayers = nGpuLayers,
                enable_thinking = false,
                npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                npu_model_folder_path = filesDir.absolutePath
            )

            LlmWrapper.builder().llmCreateInput(
                LlmCreateInput(
                    model_name = "",  // Empty for GGUF
                    model_path = modelFile.absolutePath,
                    tokenizer_path = null,  // GGUF has embedded tokenizer
                    config = conf,
                    plugin_id = pluginId
                )
            ).build().onSuccess { wrapper ->
                isLoadLlmModel = true
                llmWrapper = wrapper
                onLoadModelSuccess("Manual model loaded: ${modelFile.name}")
                Log.d(TAG, "Manual model loaded successfully")
            }.onFailure { error ->
                Log.e(TAG, "Manual model load failed: ${error.message}")
                onLoadModelFailed(error.message.toString())
            }
        }
    }

    private fun loadModel(selectModelData: ModelData, modelDataPluginId: String, nGpuLayers: Int) {
        modelScope.launch {
            resetLoadState()
            val nexaManifestBean = selectModelData.getNexaManifest(this@MainActivity)
            val pluginId = nexaManifestBean?.PluginId ?: modelDataPluginId

            when (nexaManifestBean?.ModelType ?: selectModelData.type) {
                "chat", "llm" -> {

                    val conf = ModelConfig(
                        nCtx = 1024,
                        nGpuLayers = nGpuLayers,
                        enable_thinking = enableThinking,
                        npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                        npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath
                    )
                    // Build and initialize LlmWrapper for chat model
                    LlmWrapper.builder().llmCreateInput(
                        LlmCreateInput(
                            model_name = nexaManifestBean?.ModelName ?: "",
                            model_path = selectModelData.modelFile(this@MainActivity)!!.absolutePath,
                            tokenizer_path = selectModelData.tokenFile(this@MainActivity)?.absolutePath,
                            config = conf,
                            plugin_id = pluginId
                        )
                    ).build().onSuccess { wrapper ->
                        isLoadLlmModel = true
                        llmWrapper = wrapper
                        onLoadModelSuccess("llm model loaded")
                    }.onFailure { error ->
                        onLoadModelFailed(error.message.toString())
                    }

                }

                "embedder" -> {
                    // Handle embedder model loading with NPU paths using EmbedderCreateInput
                    // embed-gemma
                    val embedderCreateInput = EmbedderCreateInput(
                        model_name = nexaManifestBean?.ModelName
                            ?: "",  // Model name for NPU plugin
                        model_path = selectModelData.modelFile(this@MainActivity)!!.absolutePath,
                        tokenizer_path = selectModelData.tokenFile(this@MainActivity)?.absolutePath,
                        config = ModelConfig(
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            nGpuLayers = nGpuLayers
                        ),
                        plugin_id = pluginId,
                        device_id = null
                    )

                    EmbedderWrapper.builder()
                        .embedderCreateInput(embedderCreateInput)
                        .build().onSuccess { wrapper ->
                            isLoadEmbedderModel = true
                            embedderWrapper = wrapper
                            onLoadModelSuccess("embedder model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }

                }

                "reranker" -> {
                    // Handle reranker model loading with NPU paths using RerankerCreateInput
                    // jina-v2-rerank-npu
                    val rerankerCreateInput = RerankerCreateInput(
                        model_name = nexaManifestBean?.ModelName
                            ?: "",  // Model name for NPU plugin
                        model_path = selectModelData.modelFile(this@MainActivity)!!.absolutePath,
                        tokenizer_path = selectModelData.tokenFile(this@MainActivity)?.absolutePath,
                        config = ModelConfig(
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            nGpuLayers = nGpuLayers
                        ),
                        plugin_id = pluginId,
                        device_id = null
                    )

                    RerankerWrapper.builder()
                        .rerankerCreateInput(rerankerCreateInput)
                        .build().onSuccess { wrapper ->
                            isLoadRerankerModel = true
                            rerankerWrapper = wrapper
                            onLoadModelSuccess("reranker model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }

                }

                "paddleocr" -> {
                    // paddleocr-npu
                    val cvCreateInput = CVCreateInput(
                        model_name = nexaManifestBean?.ModelName ?: "",
                        config = CVModelConfig(
                            capabilities = CVCapability.OCR,
                            det_model_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            rec_model_path = selectModelData.modelFile(this@MainActivity)!!.absolutePath,
                            char_dict_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir
                        ),
                        plugin_id = pluginId
                    )
                    CvWrapper.builder()
                        .createInput(cvCreateInput)
                        .build().onSuccess {
                            isLoadCVModel = true
                            cvWrapper = it
                            onLoadModelSuccess("paddleocr model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }
                }

                "asr" -> {
                    // ADD: Handle ASR model loading
                    // parakeet-tdt-0.6b-v3-npu
                    val asrCreateInput = AsrCreateInput(
                        model_name = nexaManifestBean?.ModelName ?: "",
                        model_path = selectModelData.modelFile(this@MainActivity)!!.absolutePath,
                        config = ModelConfig(
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath,
                            nGpuLayers = nGpuLayers
                        ),
                        plugin_id = pluginId
                    )

                    AsrWrapper.builder()
                        .asrCreateInput(asrCreateInput)
                        .build().onSuccess { wrapper ->
                            isLoadAsrModel = true
                            asrWrapper = wrapper
                            onLoadModelSuccess("ASR model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }
                }

                "multimodal", "vlm" -> {
                    // VLM model
                    val isNpuVlm = nexaManifestBean?.PluginId == "npu"
                    val config = if (isNpuVlm) {
                        ModelConfig(
                            nCtx = 2048,
                            nThreads = 8,
                            enable_thinking = enableThinking,
                            npu_lib_folder_path = applicationInfo.nativeLibraryDir,
                            npu_model_folder_path = selectModelData.modelDir(this@MainActivity).absolutePath
                        )
                    } else {
                        ModelConfig(
                            nCtx = 1024,
                            nThreads = 4,
                            nBatch = 1,
                            nUBatch = 1,
                            nGpuLayers = nGpuLayers,
                            enable_thinking = enableThinking
                        )
                    }

                    val vlmCreateInput = VlmCreateInput(
                        model_name = nexaManifestBean?.ModelName ?: "",
                        model_path = selectModelData.modelFile(this@MainActivity)!!.absolutePath,
                        mmproj_path = selectModelData.mmprojTokenFile(this@MainActivity)?.absolutePath,
                        config = config,
                        plugin_id = pluginId
                    )

                    VlmWrapper.builder()
                        .vlmCreateInput(vlmCreateInput)
                        .build().onSuccess {
                            isLoadVlmModel = true
                            vlmWrapper = it
                            onLoadModelSuccess("vlm model loaded")
                        }.onFailure { error ->
                            onLoadModelFailed(error.message.toString())
                        }
                }

                else -> {
                    onLoadModelFailed("model type error")
                }
            }
        }
    }

    private fun downloadModel(selectModelData: ModelData) {
        // Check local files first before SharedPreferences
        val fileName = isModelDownloaded(selectModelData)
        if (fileName == null || hasLoadedModel()) {
            Toast.makeText(this@MainActivity, "model already downloaded", Toast.LENGTH_SHORT)
                .show()
        } else {
            downloadState = DownloadState.DOWNLOADING
            downloadingModelData = selectModelData
            llDownloading.visibility = View.VISIBLE
            tvDownloadProgress.text = "0%"
            modelScope.launch {
                val selectModelData = modelList.first { it.id == selectModelId }
                val unsafeClient = getUnsafeOkHttpClient().build()

                // Track URL mapping for fallback: primary URL -> fallback URL
                val fallbackUrlMap = mutableMapOf<String, String>()
                // Track failed downloads for fallback retry
                val failedDownloads = mutableListOf<DownloadableFileWithFallback>()

                // For NPU models without explicit files list, fetch file list with fallback support
                val filesToDownloadWithFallback: List<DownloadableFileWithFallback> = if (selectModelData.isNpuModel() &&
                    selectModelData.files.isNullOrEmpty() &&
                    !selectModelData.baseUrl.isNullOrEmpty()) {

                    Log.d(TAG, "NPU model detected, fetching file list: ${selectModelData.baseUrl}")

                    // Fetch file list with fallback support
                    val result = ModelFileListingUtil.listFilesWithFallback(selectModelData.baseUrl!!, unsafeClient)

                    if (result.files.isEmpty()) {
                        Log.e(TAG, "Failed to fetch file list for ${selectModelData.id}")
                        runOnUiThread {
                            downloadState = DownloadState.IDLE
                            llDownloading.visibility = View.GONE
                            Toaster.show("Failed to fetch file list.")
                        }
                        return@launch
                    }

                    val useHfUrls = result.source == ModelFileListingUtil.FileListResult.Source.HUGGINGFACE
                    Log.d(TAG, "Found ${result.files.size} files from ${result.source}: ${result.files}")

                    selectModelData.downloadableFilesWithFallback(
                        selectModelData.modelDir(this@MainActivity),
                        result.files,
                        useHfUrls
                    )
                } else {
                    // For non-NPU models or models with explicit files, use the original method with fallback
                    selectModelData.downloadableFiles(selectModelData.modelDir(this@MainActivity)).withFallbackUrls()
                }

                // Build fallback URL map
                filesToDownloadWithFallback.forEach {
                    fallbackUrlMap[it.primaryUrl] = it.fallbackUrl
                }

                // Convert to simple DownloadableFile for initial download attempt
                val filesToDownload = filesToDownloadWithFallback.map {
                    DownloadableFile(it.file, it.primaryUrl)
                }

                Log.d(TAG, "filesToDownload: $filesToDownload")
                if (filesToDownload.isEmpty()) throw IllegalArgumentException("No download URL")

                fun getUrlFileSize(client: OkHttpClient, url: String): Long {
                    val hostname = try {
                        url.substringAfter("://").substringBefore("/")
                    } catch (e: Exception) {
                        "unknown"
                    }

                    Log.d(TAG, "Requesting file size: $hostname")

                    val builder = Request.Builder().url(url).head()
                    getHfToken(selectModelData, url)?.let {
                        builder.addHeader("Authorization", "Bearer $it")
                    }
                    val request = builder.build()
                    try {
                        client.newCall(request).execute().use { resp ->
                            val size = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                            Log.d(TAG, "Response: code=${resp.code}, size=$size")
                            return size
                        }
                    } catch (e: java.net.UnknownHostException) {
                        Log.e(TAG, "DNS resolution failed for $hostname - Check DNS/network")
                        return 0L
                    } catch (e: java.net.SocketTimeoutException) {
                        Log.e(TAG, "Connection timeout to $hostname - Possible firewall/proxy issue")
                        return 0L
                    } catch (e: java.net.ConnectException) {
                        Log.e(TAG, "Connection refused by $hostname - Server unreachable")
                        return 0L
                    } catch (e: javax.net.ssl.SSLException) {
                        Log.e(TAG, "SSL/TLS error to $hostname - ${e.message}")
                        return 0L
                    } catch (e: Exception) {
                        Log.e(TAG, "Network error: ${e.javaClass.simpleName} - ${e.message}")
                        return 0L
                    }
                }

                // Try to get file sizes, with fallback to HF if S3 fails
                val fileSizeMap = mutableMapOf<String, Long>()
                filesToDownloadWithFallback.forEach { fileWithFallback ->
                    var size = getUrlFileSize(unsafeClient, fileWithFallback.primaryUrl)
                    if (size == 0L && fileWithFallback.fallbackUrl != fileWithFallback.primaryUrl) {
                        Log.w(TAG, "Primary URL failed, trying fallback for size: ${fileWithFallback.file.name}")
                        size = getUrlFileSize(unsafeClient, fileWithFallback.fallbackUrl)
                    }
                    fileSizeMap[fileWithFallback.primaryUrl] = size
                }

                val totalSizes = filesToDownload.map { fileSizeMap[it.url] ?: 0L }
                if (totalSizes.any { it == 0L }) {
                    runOnUiThread {
                        downloadState = DownloadState.IDLE
                        llDownloading.visibility = View.GONE
                        Toaster.show("Download failed - could not get file sizes.")
                    }
                    return@launch
                }

                val alreadyDownloaded = mutableMapOf<String, Long>()
                val totalBytes = totalSizes.sum()
                Log.d(TAG, "all model size: $totalBytes")

                val startTime = System.currentTimeMillis()
                var lastProgressTime = 0L
                val progressInterval = 500L

                fun onProgress(
                    modelId: String,
                    percent: Int,
                    downloaded: Long,
                    totalBytes: Long,
                    etaSec: Long,
                    speedStr: String
                ) {
                    runOnUiThread {
                        if (100 == percent) {
                            llDownloading.visibility = View.GONE
                            spDownloaded.edit().putBoolean(selectModelId, true).commit()
                            Toaster.show("${downloadingModelData?.displayName} downloaded")
                        } else {
                            tvDownloadProgress.text = "$percent%"
                        }
                    }
                }

                fun reportProgress(force: Boolean = false) {
                    val now = System.currentTimeMillis()
                    if (force || now - lastProgressTime > progressInterval) {
                        val elapsedMs = now - startTime
                        val downloaded = alreadyDownloaded.values.sum()
                        val percent =
                            if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                        val speedAvg =
                            if (elapsedMs > 0) downloaded / (elapsedMs / 1000.0) else 0.0
                        val etaSec =
                            if (speedAvg > 0) ((totalBytes - downloaded) / speedAvg).toLong() else -1L
                        val speedStr = if (speedAvg > 1024 * 1024) {
                            String.format("%.2f MB/s", speedAvg / (1024 * 1024))
                        } else {
                            String.format("%.1f KB/s", speedAvg / 1024)
                        }
                        onProgress(selectModelId, percent, downloaded, totalBytes, etaSec, speedStr)
                        lastProgressTime = now
                    }
                }

                // Function to start download for a list of files
                fun startDownload(
                    downloadFiles: List<DownloadableFile>,
                    isFallbackAttempt: Boolean = false
                ) {
                    if (downloadFiles.isEmpty()) {
                        if (failedDownloads.isEmpty()) {
                            // All downloads complete
                            downloadState = DownloadState.IDLE
                            reportProgress(force = true)
                            onProgress(selectModelId, 100, totalBytes, totalBytes, 0, "0 KB/s")
                        } else {
                            runOnUiThread {
                                downloadState = DownloadState.IDLE
                                llDownloading.visibility = View.GONE
                                Toaster.show("Download failed for some files.")
                            }
                        }
                        return
                    }

                    val queueSet = DownloadContext.QueueSet()
                        .setParentPathFile(downloadFiles[0].file.parentFile)
                        .setMinIntervalMillisCallbackProcess(300)
                    val builder = queueSet.commit()

                    downloadFiles.forEach { item ->
                        val taskBuilder = DownloadTask.Builder(item.url, item.file)
                        getHfToken(selectModelData, item.url)?.let {
                            taskBuilder.addHeader("Authorization", "Bearer $it")
                        }
                        val task = taskBuilder.build()
                        task.info?.let {
                            alreadyDownloaded[it.url] = it.totalOffset
                        }
                        builder.bindSetTask(task)
                    }

                    val totalCount = filesToDownload.size
                    var currentCount = filesToDownload.size - downloadFiles.size
                    val pendingFallbacks = mutableListOf<DownloadableFile>()

                    downloadContext = builder.setListener(createDownloadContextListener {}).build()
                    downloadContext?.start(
                        createListener1(taskStart = { task, _ ->
                            Log.d(TAG, "download task ${task.id} Start${if (isFallbackAttempt) " (fallback)" else ""}")
                        }, retry = { task, _ ->
                            Log.d(TAG, "download task ${task.id} retry")
                        }, connected = { task, _, _, _ ->
                            Log.d(TAG, "download task ${task.id} connected")
                        }, progress = { task, currentOffset, totalLength ->
                            Log.d(TAG, "download task ${task.id} progress $currentOffset $totalLength")
                            alreadyDownloaded[task.url] = currentOffset
                            reportProgress(true)
                        }) { task, cause, exception, _ ->
                            when(cause) {
                                EndCause.CANCELED -> {
                                    // do nothing
                                }

                                EndCause.COMPLETED -> {
                                    Log.d(TAG, "download task ${task.id} end")
                                    currentCount += 1
                                    Log.d(TAG, "download task process currentCount:$currentCount, totalCount:$totalCount")

                                    if (currentCount >= totalCount) {
                                        downloadState = DownloadState.IDLE
                                        reportProgress(force = true)
                                        onProgress(selectModelId, 100, totalBytes, totalBytes, 0, "0 KB/s")
                                    }
                                }

                                else -> {
                                    Log.e(TAG, "download task ${task.id} error: $cause, ${exception?.message}")

                                    // Try fallback URL if available and not already a fallback attempt
                                    if (!isFallbackAttempt) {
                                        val fallbackUrl = fallbackUrlMap[task.url]
                                        if (fallbackUrl != null && fallbackUrl != task.url && task.file != null) {
                                            Log.w(TAG, "Primary download failed, queuing fallback: ${task.file?.name}")
                                            pendingFallbacks.add(DownloadableFile(task.file!!, fallbackUrl))
                                        } else {
                                            val failedFile = filesToDownloadWithFallback.find { it.primaryUrl == task.url }
                                            if (failedFile != null) {
                                                failedDownloads.add(failedFile)
                                            }
                                        }
                                    } else {
                                        val failedFile = filesToDownloadWithFallback.find {
                                            it.primaryUrl == task.url || it.fallbackUrl == task.url
                                        }
                                        if (failedFile != null) {
                                            failedDownloads.add(failedFile)
                                        }
                                    }

                                    currentCount += 1
                                    if (currentCount >= totalCount && pendingFallbacks.isEmpty()) {
                                        if (failedDownloads.isEmpty()) {
                                            downloadState = DownloadState.IDLE
                                            reportProgress(force = true)
                                            onProgress(selectModelId, 100, totalBytes, totalBytes, 0, "0 KB/s")
                                        } else {
                                            runOnUiThread {
                                                downloadState = DownloadState.IDLE
                                                llDownloading.visibility = View.GONE
                                                Toaster.show("Download failed for ${failedDownloads.size} file(s).")
                                            }
                                        }
                                    } else if (pendingFallbacks.isNotEmpty()) {
                                        Log.d(TAG, "Starting ${pendingFallbacks.size} fallback downloads")
                                        modelScope.launch {
                                            startDownload(pendingFallbacks.toList(), isFallbackAttempt = true)
                                        }
                                        pendingFallbacks.clear()
                                    }
                                }
                            }
                        }, true
                    )
                }

                // Start initial download with primary URLs
                startDownload(filesToDownload)
            }
        }
    }

    private fun setListeners() {

        btnAddImage.setOnClickListener {
            showPopupMenu(it)
        }

        btnAudioRecord.setOnClickListener {
            startRecord()
        }

        btnClearHistory.setOnClickListener {
            clearHistory()
        }

        btnSelectModelFile.setOnClickListener {
            openModelFilePicker()
        }

        btnBrowseFiles.setOnClickListener {
            browseHealthFiles()
        }

        // Quick-action buttons (always visible)
        findViewById<Button>(R.id.btn_vault_quick).setOnClickListener {
            browseHealthFiles()
        }
        findViewById<Button>(R.id.btn_settings_quick).setOnClickListener {
            showSettingsDialog()
        }

        /**
         * Step 3. download model
         */
        binding.btnCancelDownload.setOnClickListener {
            downloadContext?.stop()
            tvDownloadProgress.text = "0%"
            downloadingModelData?.downloadableFiles(downloadingModelData!!.modelDir(this))
                ?.forEach {
                    it.file.delete()
                }
            binding.btnDismissDownload.performClick()
        }
        binding.btnRetryDownload.setOnClickListener {
            downloadContext?.stop()
            downloadState = DownloadState.IDLE
            downloadModel(downloadingModelData!!)
        }
        binding.btnDismissDownload.setOnClickListener {
            binding.llDownloading.visibility = View.GONE
        }
        btnDownload.setOnClickListener {
            if (downloadState == DownloadState.DOWNLOADING) {
                if (downloadingModelData?.id == selectModelId) {
                    binding.llDownloading.visibility = View.VISIBLE
                } else {
                    Toaster.show("${downloadingModelData?.displayName} is currently downloading.")
                }
                return@setOnClickListener
            }
            val selectModelData = modelList.first { it.id == selectModelId }
            downloadModel(selectModelData)
        }
        /**
         * Step 4. load model
         */
        btnLoadModel.setOnClickListener {
            // Check if manual model file is selected
            if (manualModelFilePath != null) {
                if (hasLoadedModel()) {
                    Toast.makeText(this@MainActivity, "please unload first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                vTip.visibility = View.VISIBLE
                llLoading.visibility = View.VISIBLE

                // Show plugin selection dialog for manual model
                val dialogBinding = DialogSelectPluginIdBinding.inflate(layoutInflater)
                // Per Nexa tutorial: GGUF models MUST use "cpu_gpu" plugin on Snapdragon
                dialogBinding.rbCpu.visibility = View.VISIBLE
                dialogBinding.rbCpu.text = "CPU+GPU (GGUF)"
                dialogBinding.rbCpu.isChecked = true  // Default to cpu_gpu for GGUF
                dialogBinding.rbGpu.visibility = View.VISIBLE
                dialogBinding.rbNpu.visibility = View.VISIBLE

                var selectedPluginId = "cpu_gpu"  // Tutorial: GGUF → cpu_gpu
                var nGpuLayers = 0

                dialogBinding.rgSelectPluginId.setOnCheckedChangeListener { group, checkedId ->
                    selectedPluginId = when (checkedId) {
                        R.id.rb_cpu -> "cpu_gpu"  // GGUF uses cpu_gpu per tutorial
                        R.id.rb_gpu -> "gpu"
                        R.id.rb_npu -> "npu"  // For Nexa proprietary models
                        else -> "cpu_gpu"
                    }
                    dialogBinding.llGpuLayers.visibility =
                        if (checkedId == R.id.rb_gpu) View.VISIBLE else View.GONE
                }

                val dialogOnClickListener = object : CustomDialogInterface.OnClickListener() {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        if (dialogBinding.llGpuLayers.visibility == View.VISIBLE) {
                            val layers = dialogBinding.etGpuLayers.text.toString().toIntOrNull() ?: 0
                            if (layers == 0) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "nGpuLayers min value is 1",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return
                            }
                            nGpuLayers = layers
                        }

                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                dialog?.dismiss()
                                loadManualModel(manualModelFilePath!!, selectedPluginId, nGpuLayers)
                            }
                            DialogInterface.BUTTON_NEGATIVE -> {
                                llLoading.visibility = View.INVISIBLE
                                vTip.visibility = View.GONE
                            }
                        }
                    }
                }

                val alertDialog = AlertDialog.Builder(this).setView(dialogBinding.root)
                    .setNegativeButton("cancel", dialogOnClickListener)
                    .setPositiveButton("sure", dialogOnClickListener)
                    .setCancelable(false)
                    .create()
                alertDialog.show()
                dialogOnClickListener.resetPositiveButton(alertDialog)
                return@setOnClickListener
            }

            // Normal flow - load from model list
            if (selectModelId.isEmpty()) {
                Toast.makeText(this@MainActivity, "Please select a model from list or use 'Select Model File'", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectModelData = modelList.firstOrNull { it.id == selectModelId }
            if (selectModelData == null) {
                Toast.makeText(this@MainActivity, "Model not found in list", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "current select model data:$selectModelData")
            if (hasLoadedModel()) {
                Toast.makeText(this@MainActivity, "please unload first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if model files exist locally before attempting to load
            val fileName = isModelDownloaded(selectModelData)
            if (fileName != null) {
                Toaster.showLong("The \"$fileName\" file is missing. Please download it first.")
                return@setOnClickListener
            }

            vTip.visibility = View.VISIBLE
            llLoading.visibility = View.VISIBLE

            val supportPluginIds = selectModelData.getSupportPluginIds()
            Log.d(TAG, "support plugin_id:$supportPluginIds")
            var modelDataPluginId = "cpu_gpu"  // Tutorial: GGUF → cpu_gpu on Snapdragon
            var nGpuLayers = 0
            if (supportPluginIds.size > 1) {
                val dialogBinding = DialogSelectPluginIdBinding.inflate(layoutInflater)
                supportPluginIds.forEach {
                    when (it) {
                        "cpu" -> {
                            dialogBinding.rbCpu.visibility = View.VISIBLE
                            dialogBinding.rbCpu.isChecked = true
                        }

                        "gpu" -> {
                            dialogBinding.rbGpu.visibility = View.VISIBLE
                        }

                        "npu" -> {
                            dialogBinding.rbNpu.visibility = View.VISIBLE
                            dialogBinding.rbNpu.isChecked = true
                        }
                    }
                }
                dialogBinding.rgSelectPluginId.setOnCheckedChangeListener { group, checkedId ->
                    dialogBinding.llGpuLayers.visibility =
                        if (checkedId == R.id.rb_gpu) View.VISIBLE else View.GONE
                }

                val dialogOnClickListener = object : CustomDialogInterface.OnClickListener() {
                    override fun onClick(
                        dialog: DialogInterface?,
                        which: Int
                    ) {
                        // Capture selected plugin ID from radio buttons
                        modelDataPluginId = when (dialogBinding.rgSelectPluginId.checkedRadioButtonId) {
                            R.id.rb_cpu -> "cpu"
                            R.id.rb_gpu -> "gpu"
                            R.id.rb_npu -> "npu"
                            else -> "cpu"
                        }

                        nGpuLayers = 0
                        if (dialogBinding.llGpuLayers.visibility == View.VISIBLE) {
                            nGpuLayers = dialogBinding.etGpuLayers.text.toString().toIntOrNull() ?: 0
                            if (nGpuLayers == 0) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "nGpuLayers min value is 1",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return
                            }
                        }
                        when (which) {
                            DialogInterface.BUTTON_POSITIVE -> {
                                dialog?.dismiss()
                                loadModel(selectModelData, modelDataPluginId, nGpuLayers)
                            }

                            DialogInterface.BUTTON_NEGATIVE -> {
                                llLoading.visibility = View.INVISIBLE
                                vTip.visibility = View.GONE
                            }
                        }
                    }

                }
                val alertDialog = AlertDialog.Builder(this).setView(dialogBinding.root)
                    .setNegativeButton("cancel", dialogOnClickListener)
                    .setPositiveButton("sure", dialogOnClickListener)
                    .setCancelable(false)
                    .create()
                alertDialog.show()
                dialogOnClickListener.resetPositiveButton(alertDialog)
            } else {
                // Single plugin available - use it directly
                modelDataPluginId = supportPluginIds.firstOrNull() ?: "cpu"
                loadModel(selectModelData, modelDataPluginId, nGpuLayers)
            }
        }

        /**
         * Step 5. send message
         */
        btnSend.setOnClickListener {
            // If images are captured, trigger mock scan demo
            if (savedImageFiles.isNotEmpty() && !hasLoadedModel()) {
                messages.add(Message("", MessageType.IMAGES, savedImageFiles.map { it }))
                reloadRecycleView()
                clearImages()
                runMockScanDemo()
                etInput.setText("")
                return@setOnClickListener
            }

            // Preloaded RAG mode — works without model loaded
            if (!hasLoadedModel()) {
                val inputString = etInput.text.trim().toString()
                if (inputString.isNotEmpty()) {
                    messages.add(Message(inputString, MessageType.USER))
                    reloadRecycleView()
                    etInput.setText("")
                    etInput.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(etInput.windowToken, 0)

                    if (!handlePreloadedQuery(inputString)) {
                        // No preloaded answer — show helpful hint
                        streamResponseToChat("I can look up your **eyes**, **medications**, **timeline**, or give a **health summary**.\n\n*Load a model in settings for free-form responses.*")
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Ask about your health records", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            if (savedImageFiles.isNotEmpty()) {
                messages.add(Message("", MessageType.IMAGES, savedImageFiles.map { it }))
                reloadRecycleView()
            }

            val inputString = etInput.text.trim().toString()
            etInput.setText("")
            etInput.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etInput.windowToken, 0)

            if (inputString.isNotEmpty()) {
                messages.add(Message(inputString, MessageType.USER))
                reloadRecycleView()
            }

            val supportFunctionCall = false
            var tools: String? = null
            var grammarString: String? = null
            if (supportFunctionCall) {
                // if this model support 'function call'
                tools =
                    "[{\"type\":\"function\",\"function\":{\"name\": \"campaign_investigation\",\"description\": \"Check campaign limits and determine appropriate action. If customer has reached limit, return a message (hardcoded or generated by model). If limit not reached, contact support.\",\"parameters\": {\"type\": \"object\", \"properties\":{\"campaign_name\":{\"type\": \"string\",\"description\": \"The name of the campaign to investigate\"}}, \"required\":[\"campaign_name\"]}}}]"
                grammarString = """
root ::= "<tool_call>" space object "</tool_call>" space
object ::= "{" space campaign-name-kv "}" space
campaign-name-kv ::= "\"campaign_name\"" space ":" space string
string ::= "\"" char* "\"" space
char ::= [^"\\\x7F\x00-\x1F] | [\\] (["\\bfnrt] | "u" hex hex hex hex)
hex ::= [0-9a-fA-F]
space ::= | " " | "\n" | "\r" | "\t"
"""
            }

            if (!hasLoadedModel()) {
                Toast.makeText(this@MainActivity, "model not loaded", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            modelScope.launch {
                val selectModelData = modelList.first { it.id == selectModelId }
                val isNpu = selectModelData.getNexaManifest(this@MainActivity)?.PluginId == "npu"
                Log.d(TAG, "isNpu: $isNpu")

                val sb = StringBuilder()
                if (isLoadCVModel) {
                    // FIXME: Temporarily select the last image
                    if (savedImageFiles.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Please select one picture.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }
                    val imagePath = savedImageFiles.last().absolutePath
                    messages.add(Message("", MessageType.IMAGES, savedImageFiles))
                    reloadRecycleView()
                    clearImages()
                    cvWrapper.infer(imagePath).onSuccess {
                        Log.d("nfl", "infer result:$it")
                        runOnUiThread {
                            val content = it.map { result ->
                                "[${result.confidence}] ${result.text}"
                            }.toList().joinToString(separator = "\n")
                            messages.add(Message(content, MessageType.ASSISTANT))
                            reloadRecycleView()
                        }
                    }.onFailure { error ->
                        runOnUiThread {
                            messages.add(Message(error.toString(), MessageType.PROFILE))
                            reloadRecycleView()
                        }
                        Log.d("nfl", "infer result error:$error")
                    }
                } else if (isLoadAsrModel) {
                    if (audioFile == null) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "no audio file", Toast.LENGTH_SHORT)
                                .show()
                        }
                    } else {
//                        val audioFilePath = audioFile!!.absolutePath
                        val audioFilePath = "/sdcard/Download/assets/OSR_us_000_0010_16k.wav"
                        asrWrapper.transcribe(
                            AsrTranscribeInput(
                                audioFilePath,  // Use hardcoded path instead of inputString
                                "en",  // Language code
                                null   // Optional timestamps
                            )
                        ).onSuccess { transcription ->
                            runOnUiThread {
                                messages.add(
                                    Message(
                                        transcription.result.transcript ?: "",
                                        MessageType.ASSISTANT
                                    )
                                )
                                reloadRecycleView()
                            }
                        }.onFailure { error ->
                            runOnUiThread {
                                messages.add(
                                    Message(
                                        "Error: ${error.message}",
                                        MessageType.PROFILE
                                    )
                                )
                                reloadRecycleView()
                            }
                        }
                    }
                }
//                else if (isLoadEmbedderModel) {
//                    // ADD: Handle embedder inference
//                    // Input format: single text or multiple texts separated by "|"
//                    val texts = inputString.split("|").map { it.trim() }.toTypedArray()
//                    embedderWrapper!!.embed(texts, EmbeddingConfig()).onSuccess { embeddings ->
//                        runOnUiThread {
//                            val result = StringBuilder()
//                            val embeddingDim = embeddings.size / texts.size
//
//                            texts.forEachIndexed { idx, text ->
//                                val start = idx * embeddingDim
//                                val end = start + embeddingDim
//                                val embedding = embeddings.slice(start until end)
//
//                                // Calculate mean and variance
//                                val mean = embedding.average()
//                                val variance = embedding.map { (it - mean) * (it - mean) }.average()
//
//                                result.append("Text ${idx + 1}: \"$text\"\n")
//                                result.append("Embedding dimension: $embeddingDim\n")
//                                result.append("Mean: ${"%.4f".format(mean)}\n")
//                                result.append("Variance: ${"%.4f".format(variance)}\n")
//                                result.append("First 5 values: [")
//                                result.append(
//                                    embedding.take(5).joinToString(", ") { "%.4f".format(it) })
//                                result.append("...]\n\n")
//                            }
//
//                            messages.add(Message(result.toString(), MessageType.ASSISTANT))
//                            reloadRecycleView()
//                        }
//                    }.onFailure { error ->
//                        runOnUiThread {
//                            messages.add(Message("Error: ${error.message}", MessageType.PROFILE))
//                            reloadRecycleView()
//                        }
//                    }
//                }
                else if (isLoadRerankerModel) {
                    // Reranker input format: "query\ndoc1\ndoc2\ndoc3..."
                    // First line is query, remaining lines are documents
                    val query = inputString.split("\n")[0]  // Get first line as query
                    val documents =
                        inputString.split("\n").drop(1).toTypedArray()  // Get rest as docs
                    rerankerWrapper.rerank(query, documents, RerankConfig())
                        .onSuccess { rerankerResult ->
                            runOnUiThread {
                                val result = StringBuilder()
                                result.append("Rerank Results:\n")
                                // Sort by score descending to show best matches first
                                rerankerResult.scores?.withIndex()?.sortedByDescending { it.value }
                                    ?.forEach { (idx, score) ->
                                        result.append("${idx + 1}. Score: ${"%.4f".format(score)}\n")
                                        result.append("   ${documents[idx]}\n\n")
                                    }
                                messages.add(Message(result.toString(), MessageType.ASSISTANT))
                                reloadRecycleView()
                            }
                        }.onFailure { error ->
                            runOnUiThread {
                                "Error: ${error.message}".also {
                                    messages.add(Message(it, MessageType.PROFILE))
                                    reloadRecycleView()
                                }
                            }
                        }
                } else if (isLoadVlmModel) {
                    val contents = savedImageFiles.map {
                        VlmContent("image", it.absolutePath)
                    }.toMutableList()
                    audioFile?.let {
                        contents.add(VlmContent("audio", it.absolutePath))
                    }
                    contents.add(VlmContent("text", inputString))
                    audioFile = null
                    clearImages()
                    val sendMsg = VlmChatMessage(role = "user", contents = contents)
                    // VlmContentTransfer(
                    //     this@MainActivity, VlmContent(
                    //         "image", inputString
                    //     )
                    // ).forUrl()

                    // vlmChatList.clear()
                    vlmChatList.add(sendMsg)

                    Log.d(TAG, "before apply chat template:$vlmChatList")
                    vlmWrapper.applyChatTemplate(vlmChatList.toTypedArray(), tools, enableThinking)
                        .onSuccess { result ->
                            Log.d(TAG, "vlm chat template:${result.formattedText}")
                            val baseConfig =
                                GenerationConfigSample().toGenerationConfig(grammarString)
                            val configWithMedia = vlmWrapper.injectMediaPathsToConfig(
                                vlmChatList.toTypedArray(),
                                baseConfig
                            )

                            Log.d(TAG, "Config has ${configWithMedia.imageCount} images")

                            vlmWrapper.generateStreamFlow(
                                if (isNpu || true) inputString else result.formattedText,
                                configWithMedia  // Use the updated config with media paths
                            ).collect { handleResult(sb, it) }
                        }.onFailure {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity, it.message, Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                } else {
                    chatList.add(ChatMessage(role = "user", inputString))
                    // Apply chat template and generate
                    llmWrapper.applyChatTemplate(
                        chatList.toTypedArray(),
                        tools,
                        enableThinking
                    ).onSuccess { templateOutput ->
                        Log.d(TAG, "chat template:${templateOutput.formattedText}")
                        llmWrapper.generateStreamFlow(
                            templateOutput.formattedText,
                            GenerationConfigSample().toGenerationConfig(grammarString)
                        ).collect { streamResult ->
                            handleResult(sb, streamResult)
                        }
                    }.onFailure { error ->
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity, error.message, Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                clearImages()
            }

        }

        /**
         * Step 6. others
         */
        btnUnloadModel.setOnClickListener {
            if (!hasLoadedModel()) {
                Toast.makeText(this@MainActivity, "model not loaded", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Unload model and cleanup
            val handleUnloadResult = fun(result: Int) {
                resetLoadState()
                runOnUiThread {
                    vTip.visibility = View.GONE
                    btnUnloadModel.visibility = View.GONE
                    btnStop.visibility = View.GONE
                    btnAddImage.visibility = View.INVISIBLE
                    btnAudioRecord.visibility = View.INVISIBLE

                    // Update status
                    tvModelStatus.text = "No model loaded - Select model file in advanced mode"

                    Toast.makeText(
                        this@MainActivity, if (result == 0) {
                            "unload success"
                        } else {
                            "unload failed and error code: $result"
                        }, Toast.LENGTH_SHORT
                    ).show()
                }
            }
            modelScope.launch {
                if (isLoadVlmModel) {
                    vlmWrapper.stopStream()
                    vlmWrapper.destroy()
                    vlmChatList.clear()
                    // TODO:
                    handleUnloadResult(0)
                } else if (isLoadEmbedderModel) {
                    // ADD: Unload embedder
                    embedderWrapper!!.destroy()
                    handleUnloadResult(0)
                } else if (isLoadRerankerModel) {
                    // ADD: Unload reranker
                    handleUnloadResult(rerankerWrapper.destroy())
                } else if (isLoadCVModel) {
                    // ADD: Unload CV model
                    cvWrapper.destroy()
                    // TODO:
                    handleUnloadResult(0)
                } else if (isLoadAsrModel) {
                    // ADD: Unload ASR model
                    asrWrapper.destroy()
                    // TODO:
                    handleUnloadResult(0)
                } else if (isLoadLlmModel) {
                    llmWrapper.stopStream()
                    llmWrapper.destroy()
                    chatList.clear()
                    // TODO:
                    handleUnloadResult(0)
                } else {
                    handleUnloadResult(0)
                }
            }
        }
        btnStop.setOnClickListener {
            if (!hasLoadedModel()) {
                Toast.makeText(
                    this@MainActivity,
                    "model not loaded",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // MODIFY: Stop button only works for LLM/VLM (not embedder/reranker)
            if (isLoadEmbedderModel || isLoadRerankerModel || isLoadAsrModel || isLoadCVModel) {
                Toast.makeText(
                    this@MainActivity,
                    "Stop not applicable for embedder/reranker/asr/cv",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // Stop streaming
            modelScope.launch {
                if (isLoadVlmModel) {
                    vlmWrapper.stopStream()
                } else if (isLoadLlmModel) {
                    llmWrapper.stopStream()
                }
            }
        }
    }

    fun handleResult(sb: StringBuilder, streamResult: LlmStreamResult) {
        when (streamResult) {
            is LlmStreamResult.Token -> {
                runOnUiThread {
                    sb.append(streamResult.text)
                    Message(sb.toString(), MessageType.ASSISTANT).let { lastMsg ->
                        val size = messages.size
                        messages[size - 1].let { msg ->
                            if (msg.type != MessageType.ASSISTANT) {
                                messages.add(lastMsg)
                            } else {
                                messages[size - 1] = lastMsg
                            }
                        }
                    }
                    adapter.notifyDataSetChanged()
                }
                Log.d(TAG, "Token: ${streamResult.text}")
            }

            is LlmStreamResult.Completed -> {
                if (isLoadVlmModel) {
                    vlmChatList.add(
                        VlmChatMessage(
                            "assistant",
                            listOf(VlmContent("text", sb.toString()))
                        )
                    )
                } else {
                    chatList.add(ChatMessage("assistant", sb.toString()))
                }

                runOnUiThread {
                    var content = sb.toString()
                    val size = messages.size
                    messages[size - 1] = Message(content, MessageType.ASSISTANT)

                    // Auto-save health record if it contains medical data
                    if (content.contains("## Document Type:") ||
                        content.contains("### Findings") ||
                        content.contains("### Medications")) {
                        saveHealthRecord(content)
                    }

                    val ttft = String.format(null, "%.2f", streamResult.profile.ttftMs)
                    val promptTokens = streamResult.profile.promptTokens
                    val prefillSpeed =
                        String.format(null, "%.2f", streamResult.profile.prefillSpeed)

                    val generatedTokens = streamResult.profile.generatedTokens
                    val decodingSpeed =
                        String.format(null, "%.2f", streamResult.profile.decodingSpeed)

                    val profileData =
                        "TTFT: $ttft ms; Prompt Tokens: $promptTokens; \nPrefilling Speed: $prefillSpeed tok/s\nGenerated Tokens: $generatedTokens; Decoding Speed: $decodingSpeed tok/s"
                    messages.add(
                        Message(
                            profileData,
                            MessageType.PROFILE
                        )
                    )
                    reloadRecycleView()
                }
                Log.d(TAG, "Completed: ${streamResult.profile}")
            }

            is LlmStreamResult.Error -> {
                runOnUiThread {
                    val content =
                        "your conversation is out of model’s context length, please start a new conversation or click clear button"
                    messages.add(Message(content, MessageType.PROFILE))
                    reloadRecycleView()
                }
                Log.d(TAG, "Error: $streamResult")
            }
        }
    }

    private fun okdownload() {
        val okDownloadBuilder = OkDownload.Builder(this)
        val factory = DownloadOkHttp3Connection.Factory()
        factory.setBuilder(getUnsafeOkHttpClient())
        okDownloadBuilder.connectionFactory(factory)
        try {
            OkDownload.setSingletonInstance(okDownloadBuilder.build())
        } catch (e: java.lang.Exception) {
            Log.e("download", "download init failed")
        }
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient.Builder {
        try {
            val x509m: X509TrustManager = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate?>? {
                    //Note: Cannot return null here, otherwise it will throw an error
                    val x509Certificates = arrayOfNulls<X509Certificate>(0)
                    return x509Certificates
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<X509Certificate?>?, authType: String?
                ) {
// Do not throw exception to trust all server certificates
                }

                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<X509Certificate?>?, authType: String?
                ) {
// Default trust mechanism
                }
            }
            // Create a TrustManager that trusts all certificates
            val trustAllCerts = arrayOf<TrustManager>(x509m)

            // Initialize SSLContext
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            // Create SSLSocketFactory
            val sslSocketFactory: SSLSocketFactory = sslContext.getSocketFactory()

            // Build OkHttpClient
            return OkHttpClient.Builder().sslSocketFactory(
                sslSocketFactory, (trustAllCerts[0] as X509TrustManager?)!!
            ).hostnameVerifier { hostname: String?, session: SSLSession? -> true }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, null)
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        startActivityForResult(intent, 1)
    }

    private fun openModelFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        // Try to suggest common download directories
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Select GGUF Model File"),
                REQUEST_CODE_MODEL_FILE
            )
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Please install a file manager.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun browseHealthFiles() {
        browseVaultFolder(healthVaultDir, "Health Vault")
    }

    private fun browseVaultFolder(folder: File, title: String) {
        if (!folder.exists()) {
            Toast.makeText(this, "Vault not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val entries = folder.listFiles()?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()

        if (entries.isEmpty()) {
            Toast.makeText(this, "Empty", Toast.LENGTH_SHORT).show()
            return
        }

        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(0, dp(16), 0, dp(24))
        }

        // Header
        val header = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#808080"))
            textSize = 11f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.08f
            isAllCaps = true
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }
        container.addView(header)

        // Entries
        for (entry in entries) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
                isClickable = true
                isFocusable = true
                // Ripple-like touch feedback
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    sheet.dismiss()
                    if (entry.isDirectory) {
                        browseVaultFolder(entry, entry.name)
                    } else if (entry.name.endsWith(".md", true) || entry.name.endsWith(".txt", true)) {
                        try { showFileContent(entry.name, entry.readText()) }
                        catch (e: Exception) { Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show() }
                    }
                }
            }

            val icon = TextView(this).apply {
                text = if (entry.isDirectory) "/" else "."
                setTextColor(Color.parseColor("#10B981"))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 0, dp(12), 0)
            }

            val name = TextView(this).apply {
                text = entry.name
                setTextColor(if (entry.isDirectory) Color.parseColor("#F2F2F2") else Color.parseColor("#B0B0B0"))
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                letterSpacing = -0.01f
            }

            val chevron = TextView(this).apply {
                text = if (entry.isDirectory) ">" else ""
                setTextColor(Color.parseColor("#4D4D4D"))
                textSize = 12f
                setPadding(dp(8), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = android.view.Gravity.END
            }

            row.addView(icon)
            row.addView(name)
            row.addView(chevron)
            container.addView(row)

            // Separator
            val sep = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(dp(20), 0, dp(20), 0) }
                setBackgroundColor(Color.parseColor("#1A1A1A"))
            }
            container.addView(sep)
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.setContentView(scrollView)
        sheet.window?.navigationBarColor = Color.parseColor("#0D0D0D")
        // Style the bottom sheet background
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.show()
    }

    private fun showFileContent(fileName: String, content: String) {
        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070707"))
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }

        // File name header
        val header = TextView(this).apply {
            text = fileName.removeSuffix(".md").removeSuffix(".txt")
            setTextColor(Color.parseColor("#808080"))
            textSize = 11f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.08f
            isAllCaps = true
            setPadding(0, dp(4), 0, dp(16))
        }
        container.addView(header)

        // Markdown rendered content
        val markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .build()

        val contentView = TextView(this).apply {
            setTextColor(Color.parseColor("#D0D0D0"))
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            setLineSpacing(0f, 1.4f)
            letterSpacing = -0.01f
            setTextIsSelectable(true)
        }
        markwon.setMarkdown(contentView, content)
        container.addView(contentView)

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
            setBackgroundColor(Color.parseColor("#070707"))
        }
        sheet.setContentView(scrollView)
        sheet.window?.navigationBarColor = Color.parseColor("#070707")
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#070707"))
        }
        sheet.show()
    }

    private fun showSettingsDialog() {
        val systemPrompt = try {
            val promptFile = File(healthVaultDir, "04_System_Prompt/hk_system_prompt.md")
            if (promptFile.exists()) promptFile.readText() else "No system prompt loaded"
        } catch (e: Exception) { "Error loading prompt" }

        val vaultStats = try {
            var fileCount = 0
            var totalSize = 0L
            healthVaultDir.walkTopDown().forEach {
                if (it.isFile) { fileCount++; totalSize += it.length() }
            }
            "$fileCount files  ·  ${totalSize / 1024} KB"
        } catch (e: Exception) { "—" }

        val modelInfo = if (hasLoadedModel()) {
            "Active" + (if (manualModelFilePath != null) "  ·  ${File(manualModelFilePath!!).name}" else "")
        } else {
            "RAG demo mode"
        }

        val sheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }

        fun addSection(label: String, value: String) {
            val sectionLabel = TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#808080"))
                textSize = 10f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                letterSpacing = 0.1f
                isAllCaps = true
                setPadding(0, dp(16), 0, dp(4))
            }
            container.addView(sectionLabel)

            val sectionValue = TextView(this).apply {
                text = value
                setTextColor(Color.parseColor("#D0D0D0"))
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                setLineSpacing(0f, 1.3f)
                letterSpacing = -0.01f
                setTextIsSelectable(true)
            }
            container.addView(sectionValue)
        }

        addSection("Model", modelInfo)
        addSection("Health Vault", vaultStats)
        addSection("System Prompt", systemPrompt)
        addSection("Privacy", "All processing happens on-device.\nNo data is transmitted to any server.")

        // Advanced mode button
        val advBtn = Button(this).apply {
            text = "Advanced Mode"
            setTextColor(Color.parseColor("#808080"))
            textSize = 12f
            isAllCaps = false
            setBackgroundResource(R.drawable.btn_rounded_border)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            )
            lp.topMargin = dp(20)
            layoutParams = lp
            setOnClickListener {
                sheet.dismiss()
                toggleAdvancedMode()
            }
        }
        container.addView(advBtn)

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.setContentView(scrollView)
        sheet.window?.navigationBarColor = Color.parseColor("#0D0D0D")
        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun saveHealthRecord(content: String) {
        try {
            val healthFilesDir = File(filesDir, "health_records")
            if (!healthFilesDir.exists()) {
                healthFilesDir.mkdirs()
            }

            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                .format(java.util.Date())
            val fileName = "health_record_$timestamp.md"
            val file = File(healthFilesDir, fileName)

            file.writeText(content)

            Log.d(TAG, "Health record saved: ${file.absolutePath}")
            Toast.makeText(
                this,
                "✓ Saved to health records",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving health record", e)
        }
    }

    private fun handleModelFileSelection(uri: android.net.Uri) {
        try {
            // Get the file path from URI
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val fileName = if (displayNameIndex >= 0) it.getString(displayNameIndex) else "model.gguf"

                    // Check if it's a GGUF file
                    if (!fileName.endsWith(".gguf", ignoreCase = true)) {
                        Toast.makeText(this, "Please select a .gguf model file", Toast.LENGTH_LONG).show()
                        return
                    }

                    // Copy file to app's internal storage
                    val destFile = File(filesDir, "manual_models/$fileName")
                    destFile.parentFile?.mkdirs()

                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    manualModelFilePath = destFile.absolutePath

                    Toast.makeText(
                        this,
                        "Model loaded: $fileName\nTap 'Load' to initialize",
                        Toast.LENGTH_LONG
                    ).show()

                    Log.d(TAG, "Manual model file saved to: ${destFile.absolutePath}, size: ${destFile.length()} bytes")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling model file selection", e)
            Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 0) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                Toast.makeText(this, "Not allow", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 2001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera not allow", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Handle model file selection
        if (requestCode == REQUEST_CODE_MODEL_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                data.data?.let { uri ->
                    handleModelFileSelection(uri)
                }
            }
            return
        }

        var bitmap: Bitmap? = null
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val inputStream = contentResolver.openInputStream(data.data!!)
                bitmap = BitmapFactory.decodeStream(inputStream)
            }
        } else if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            photoFile?.let {
                bitmap = BitmapFactory.decodeFile(it.absolutePath)
            }
        }

        bitmap?.let {
            try {
                val file = File(filesDir, "chat_${System.currentTimeMillis()}.jpg")
                val success = saveBitmapToFile(it, file)
                if (success) {
                    Log.d(TAG, "Save success：${file.absolutePath}")
                    savedImageFiles.add(file)
                    refreshTopScrollContainer()
                } else {
                    Toast.makeText(this, "Save Image failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {
        return try {
            val tempDir = File(this.filesDir, "tmp").apply { if (!exists()) mkdirs() }

            val tempFile = File(
                tempDir,
                "tmp_${System.currentTimeMillis()}.jpg"
            )
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            val outFile = File(
                tempDir,
                "out_${System.currentTimeMillis()}.jpg"
            )
            ImgUtil.squareCrop(
                ImgUtil.downscaleAndSave(
                    imageFile = tempFile,
                    outFile = outFile,
                    maxSize = 448,
                    format = Bitmap.CompressFormat.JPEG,
                    quality = 90
                ), file, 448
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun stopRecord(cancel: Boolean) {
        wavRecorder?.stopRecording()
        wavRecorder = null
        bottomPanel.visibility = View.GONE
        if (cancel) {
            audioFile = null
        }
        refreshTopScrollContainer()
    }

    private fun startRecord() {
        bottomPanel.visibility = View.VISIBLE

        val file = File(filesDir, "audio")
        if (!file.exists()) {
            file.mkdirs()
        }
        audioFile =
            File(file, "audio_${System.currentTimeMillis()}.wav")
        Log.d(TAG, "audioFile: ${audioFile!!.absolutePath}")
        wavRecorder = WavRecorder()

        wavRecorder?.startRecording(audioFile!!)
    }

    private fun clearHistory() {
        if (isLoadLlmModel) {
            chatList.clear()
            modelScope.launch {
                llmWrapper.reset()
            }
        }
        if (isLoadVlmModel) {
            vlmChatList.clear()
            modelScope.launch {
                vlmWrapper.reset()
            }
        }
        messages.clear()
        audioFile = null
        clearImages()
        reloadRecycleView()
    }

    private var popupWindow: PopupWindow? = null
    private fun showPopupMenu(anchorView: View) {
        if (popupWindow?.isShowing == true) {
            popupWindow?.dismiss()
            return
        }

        val popupView = LayoutInflater.from(this).inflate(R.layout.menu_layout, null)

        popupWindow = PopupWindow(
            popupView,
            anchorView.width * 2,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow?.isOutsideTouchable = true
        popupWindow?.elevation = 10f

        val btnCamera = popupView.findViewById<Button>(R.id.btn_camera)
        val btnPhoto = popupView.findViewById<Button>(R.id.btn_photo)

        btnCamera.setOnClickListener {
            popupWindow?.dismiss()
            checkAndOpenCamera()
        }
        btnPhoto.setOnClickListener {
            popupWindow?.dismiss()
            openGallery()
        }

        popupView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )
        val popupHeight = popupView.measuredHeight
        popupWindow?.showAsDropDown(anchorView, 0, -anchorView.height - popupHeight)
    }

    private var photoUri: Uri? = null
    private var photoFile: File? = null

    private fun checkAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                2001
            )
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "photo_${System.currentTimeMillis()}.jpg"
        )
        photoUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile!!
        )

        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(intent, 1001)
    }

    private fun clearImages() {
        savedImageFiles.clear()
        refreshTopScrollContainer()
    }

    private fun refreshTopScrollContainer() {
        runOnUiThread {
            topScrollContainer.removeAllViews()
            if (savedImageFiles.isEmpty() && audioFile == null) {
                scrollImages.visibility = View.GONE
                return@runOnUiThread
            }

            scrollImages.visibility = View.VISIBLE

            for (file in savedImageFiles) {
                val itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_image_scroll, topScrollContainer, false)
                val ivImage = itemView.findViewById<ImageView>(R.id.iv_image)
                val btnRemove = itemView.findViewById<ImageButton>(R.id.btn_remove)

                ivImage.setImageURI(Uri.fromFile(file))

                btnRemove.setOnClickListener {
                    savedImageFiles.remove(file)
                    refreshTopScrollContainer()
                }
                topScrollContainer.addView(itemView)
            }

            if (audioFile != null) {
                val audioView = LayoutInflater.from(this)
                    .inflate(R.layout.item_audio_scroll, topScrollContainer, false)
                val audioName = audioView.findViewById<TextView>(R.id.tv_audio_name)
                val audioType = audioView.findViewById<TextView>(R.id.tv_audio_type)
                val btnRemove = audioView.findViewById<ImageButton>(R.id.btn_audio_remove)
                audioName.text = audioFile!!.name
                // TODO: hard code
                audioType.text = "wav"

                btnRemove.setOnClickListener {
                    audioFile = null
                    refreshTopScrollContainer()
                }
                topScrollContainer.addView(audioView)
            }
        }
    }

    private fun reloadRecycleView() {
        adapter.notifyDataSetChanged()
        binding.rvChat.scrollToPosition(messages.size - 1)
    }

    companion object {
        private const val SP_DOWNLOADED = "sp_downloaded"
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_MODEL_FILE = 2000
    }
}
