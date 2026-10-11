package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.example.ui.theme.MyApplicationTheme
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLConnection

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MVDirectorAppScreen()
      }
    }
  }
}

class LocalPublicAssetPathHandler(
  private val context: Context,
  private val subfolder: String = "public"
) : WebViewAssetLoader.PathHandler {

  override fun handle(path: String): WebResourceResponse? {
    try {
      var cleanPath = path.trimStart('/')
      if (cleanPath.isEmpty() || cleanPath == "/") {
        cleanPath = "index.html"
      }
      val assetPath = if (subfolder.isEmpty()) cleanPath else "$subfolder/$cleanPath"

      var inputStream: InputStream? = null
      var resolvedPath = assetPath

      try {
        inputStream = context.assets.open(resolvedPath)
      } catch (e: Exception) {
        // Fallback for root files or html routing
        if (cleanPath.endsWith(".html") || !cleanPath.contains(".")) {
          try {
            resolvedPath = "$subfolder/index.html"
            inputStream = context.assets.open(resolvedPath)
          } catch (e2: Exception) {
            // ignore
          }
        }
      }

      if (inputStream == null) {
        return null
      }

      val mimeType = when {
        resolvedPath.endsWith(".html") -> "text/html"
        resolvedPath.endsWith(".js") || resolvedPath.endsWith(".mjs") -> "application/javascript"
        resolvedPath.endsWith(".css") -> "text/css"
        resolvedPath.endsWith(".json") || resolvedPath.endsWith(".webmanifest") -> "application/json"
        resolvedPath.endsWith(".png") -> "image/png"
        resolvedPath.endsWith(".jpg") || resolvedPath.endsWith(".jpeg") -> "image/jpeg"
        resolvedPath.endsWith(".svg") -> "image/svg+xml"
        resolvedPath.endsWith(".ico") -> "image/x-icon"
        resolvedPath.endsWith(".webp") -> "image/webp"
        resolvedPath.endsWith(".mp4") -> "video/mp4"
        resolvedPath.endsWith(".mp3") -> "audio/mpeg"
        resolvedPath.endsWith(".wav") -> "audio/wav"
        resolvedPath.endsWith(".woff2") -> "font/woff2"
        resolvedPath.endsWith(".woff") -> "font/woff"
        resolvedPath.endsWith(".ttf") -> "font/ttf"
        else -> URLConnection.guessContentTypeFromName(resolvedPath) ?: "application/octet-stream"
      }

      val headers = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers" to "*",
        "Cache-Control" to "no-cache"
      )

      return WebResourceResponse(mimeType, "UTF-8", 200, "OK", headers, inputStream)
    } catch (e: Exception) {
      Log.e("MVDirector", "Asset loading error: ${e.message}")
      return null
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MVDirectorAppScreen() {
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var currentUrl by remember { mutableStateOf("https://localhost/index.html") }
  var isLoading by remember { mutableStateOf(true) }
  var loadProgress by remember { mutableIntStateOf(0) }
  var pageTitle by remember { mutableStateOf("MV Director AI") }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var showControlBar by remember { mutableStateOf(false) }
  var showConsoleDialog by remember { mutableStateOf(false) }
  var showInfoDialog by remember { mutableStateOf(false) }
  val consoleLogs = remember { mutableStateListOf<String>() }

  var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val callback = fileChooserCallback
    if (callback != null) {
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        val data = result.data
        val uris = when {
          data?.clipData != null -> {
            val clipData = data.clipData!!
            Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
          }
          data?.data != null -> {
            arrayOf(data.data!!)
          }
          else -> null
        }
        callback.onReceiveValue(uris)
      } else {
        callback.onReceiveValue(null)
      }
      fileChooserCallback = null
    }
  }

  // Handle hardware back press
  BackHandler(enabled = canGoBack) {
    webViewRef?.goBack()
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = Color.Black
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color.Black)
    ) {
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .testTag("mv_director_webview"),
        factory = { context ->
          val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("localhost")
            .addPathHandler("/", LocalPublicAssetPathHandler(context, "public"))
            .build()

          val appAssetsLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", LocalPublicAssetPathHandler(context, "public/assets"))
            .addPathHandler("/", LocalPublicAssetPathHandler(context, "public"))
            .build()

          WebView(context).apply {
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              mediaPlaybackRequiresUserGesture = false
              allowFileAccess = true
              allowContentAccess = true
              loadWithOverviewMode = true
              useWideViewPort = true
              mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
              cacheMode = WebSettings.LOAD_DEFAULT
              displayZoomControls = false
              builtInZoomControls = false
              setSupportMultipleWindows(false)
              userAgentString = "${settings.userAgentString} MVDirector/1.0 MobileTouch"
            }

            setBackgroundColor(android.graphics.Color.BLACK)

            webViewClient = object : WebViewClient() {
              override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
              ): WebResourceResponse? {
                val host = request.url.host
                if (host == "localhost") {
                  val res = assetLoader.shouldInterceptRequest(request.url)
                  if (res != null) return res
                } else if (host == "appassets.androidplatform.net") {
                  val res = appAssetsLoader.shouldInterceptRequest(request.url)
                  if (res != null) return res
                }
                return super.shouldInterceptRequest(view, request)
              }

              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                isLoading = true
                currentUrl = url ?: ""
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                isLoading = false
                currentUrl = url ?: ""
                pageTitle = view?.title ?: "MV Director AI"
                canGoBack = view?.canGoBack() ?: false
                canGoForward = view?.canGoForward() ?: false
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                val desc = error?.description ?: "Unknown error"
                consoleLogs.add("⚠️ [Error ${error?.errorCode}] $desc on ${request?.url}")
              }
            }

            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadProgress = newProgress
                if (newProgress >= 100) {
                  isLoading = false
                }
              }

              override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrEmpty()) {
                  pageTitle = title
                }
              }

              override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                  val level = consoleMessage.messageLevel()
                  val logEntry = "[$level] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                  consoleLogs.add(logEntry)
                }
                return true
              }

              override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
              }

              override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
              ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                  type = "*/*"
                  addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                  filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                  fileChooserCallback?.onReceiveValue(null)
                  fileChooserCallback = null
                  return false
                }
                return true
              }
            }

            loadUrl("https://localhost/index.html")
            webViewRef = this
          }
        },
        update = { webView ->
          webViewRef = webView
        }
      )

      // Top Loading Bar
      if (isLoading && loadProgress < 100) {
        LinearProgressIndicator(
          progress = { loadProgress / 100f },
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter),
          color = MaterialTheme.colorScheme.primary,
          trackColor = Color.Transparent
        )
      }

      // Compact floating quick-action pill at top right
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .statusBarsPadding()
          .padding(end = 12.dp, top = 8.dp)
      ) {
        Surface(
          shape = CircleShape,
          color = Color(0xCC18181B),
          tonalElevation = 6.dp,
          shadowElevation = 8.dp,
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { showControlBar = !showControlBar }
            .testTag("toggle_controls_button")
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (showControlBar) Icons.Default.Close else Icons.Default.Tune,
              contentDescription = "Studio Controls",
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      // Bottom / Overlay Studio Controls Sheet
      AnimatedVisibility(
        visible = showControlBar,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
      ) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          shape = RoundedCornerShape(20.dp),
          color = Color(0xF018181B),
          tonalElevation = 12.dp,
          shadowElevation = 16.dp
        ) {
          Column(
            modifier = Modifier.padding(16.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "🎬",
                  fontSize = 20.sp,
                  modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                  Text(
                    text = "MV Director AI Studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                  Text(
                    text = "Mobile Touch Engine Running",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA1A1AA)
                  )
                }
              }
              IconButton(
                onClick = { showControlBar = false }
              ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = { webViewRef?.goBack() },
                enabled = canGoBack,
                modifier = Modifier
                  .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                  .testTag("studio_back_button")
              ) {
                Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back",
                  tint = if (canGoBack) Color.White else Color.Gray
                )
              }

              IconButton(
                onClick = { webViewRef?.goForward() },
                enabled = canGoForward,
                modifier = Modifier
                  .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                  .testTag("studio_forward_button")
              ) {
                Icon(
                  Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = "Forward",
                  tint = if (canGoForward) Color.White else Color.Gray
                )
              }

              IconButton(
                onClick = { webViewRef?.reload() },
                modifier = Modifier
                  .background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                  .testTag("studio_reload_button")
              ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload Studio", tint = Color.White)
              }

              Button(
                onClick = { showConsoleDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF4F46E5)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("studio_logs_button")
              ) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Logs (${consoleLogs.size})", fontSize = 13.sp)
              }

              OutlinedButton(
                onClick = { showInfoDialog = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.testTag("studio_info_button")
              ) {
                Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(16.dp))
              }
            }
          }
        }
      }
    }
  }

  // Console Logs Modal Dialog
  if (showConsoleDialog) {
    AlertDialog(
      onDismissRequest = { showConsoleDialog = false },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Studio Console Logs", fontWeight = FontWeight.Bold)
          if (consoleLogs.isNotEmpty()) {
            TextButton(onClick = { consoleLogs.clear() }) {
              Text("Clear")
            }
          }
        }
      },
      text = {
        if (consoleLogs.isEmpty()) {
          Text("No console logs captured yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 350.dp)
              .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
              .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            items(consoleLogs) { log ->
              Text(
                text = log,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontSize = 11.sp
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showConsoleDialog = false }) {
          Text("Close")
        }
      }
    )
  }

  // App Info & Export Dialog
  if (showInfoDialog) {
    AlertDialog(
      onDismissRequest = { showInfoDialog = false },
      title = {
        Text("MV Director AI - Installed", fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "🎬 MV Director AI Mobile Cinematic Studio is fully packaged and running natively.",
            style = MaterialTheme.typography.bodyMedium
          )
          Text(
            text = "• Offline-first asset runtime via secure Localhost bridge\n" +
                "• Touch & gesture support for storyboarding, timeline & shot planning\n" +
                "• Full audio/video media decoding & hardware acceleration\n" +
                "• Native file chooser for importing audio tracks and assets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      confirmButton = {
        Button(onClick = { showInfoDialog = false }) {
          Text("OK")
        }
      }
    )
  }
}
