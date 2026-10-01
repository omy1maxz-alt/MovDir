package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        WebToAppScreen()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebToAppScreen() {
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var currentUrl by remember { mutableStateOf("about:blank") }
  var inputUrl by remember { mutableStateOf("https://example.com") }
  var isLoading by remember { mutableStateOf(false) }
  var pageTitle by remember { mutableStateOf("Web App Container") }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var showConfigSheet by remember { mutableStateOf(false) }
  var showConsoleDialog by remember { mutableStateOf(false) }
  val consoleLogs = remember { mutableStateListOf<String>() }
  var customHtmlContent by remember {
    mutableStateOf(
      """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <title>Mobile Web App</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
      color: #f8fafc;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
      text-align: center;
    }
    .card {
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 20px;
      padding: 28px 20px;
      backdrop-filter: blur(12px);
      max-width: 360px;
      width: 100%;
      box-shadow: 0 20px 40px rgba(0,0,0,0.4);
    }
    .badge {
      display: inline-block;
      background: #3b82f6;
      color: white;
      font-size: 12px;
      font-weight: 700;
      padding: 6px 14px;
      border-radius: 999px;
      margin-bottom: 16px;
      letter-spacing: 0.5px;
    }
    h1 { font-size: 24px; margin-bottom: 8px; font-weight: 800; }
    p { font-size: 14px; color: #94a3b8; line-height: 1.5; margin-bottom: 20px; }
    .btn-action {
      display: block;
      width: 100%;
      background: #6366f1;
      color: white;
      border: none;
      border-radius: 12px;
      padding: 14px;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }
    .btn-action:active { transform: scale(0.98); background: #4f46e5; }
    .counter {
      margin-top: 16px;
      font-size: 18px;
      font-weight: 700;
      color: #38bdf8;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">NATIVE APK READY</div>
    <h1>Web App Runtime</h1>
    <p>Upload your web app ZIP or paste code to test instant mobile functionality with full touch ergonomics.</p>
    <button class="btn-action" onclick="increment()">Tap Interactive Button</button>
    <div class="counter" id="counter">Taps: 0</div>
  </div>
  <script>
    let count = 0;
    function increment() {
      count++;
      document.getElementById('counter').innerText = 'Taps: ' + count;
      console.log('User tapped button in web app. Count = ' + count);
    }
  </script>
</body>
</html>"""
    )
  }

  // Handle hardware back press
  BackHandler(enabled = canGoBack) {
    webViewRef?.goBack()
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = pageTitle.ifEmpty { "Web App Container" },
              maxLines = 1,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = if (currentUrl.startsWith("data:")) "Custom HTML Source" else currentUrl,
              maxLines = 1,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          IconButton(
            onClick = { showConsoleDialog = true },
            modifier = Modifier.testTag("console_button")
          ) {
            Badge(
              containerColor = if (consoleLogs.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Transparent
            ) {
              Icon(Icons.Default.Terminal, contentDescription = "Console Logs")
            }
          }
          IconButton(
            onClick = { showConfigSheet = true },
            modifier = Modifier.testTag("settings_button")
          ) {
            Icon(Icons.Default.Tune, contentDescription = "App Settings & Source")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      )
    },
    bottomBar = {
      Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { webViewRef?.goBack() },
            enabled = canGoBack,
            modifier = Modifier.testTag("nav_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
          }
          IconButton(
            onClick = { webViewRef?.goForward() },
            enabled = canGoForward,
            modifier = Modifier.testTag("nav_forward_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go Forward")
          }
          IconButton(
            onClick = {
              if (currentUrl.startsWith("data:")) {
                webViewRef?.loadDataWithBaseURL(null, customHtmlContent, "text/html", "UTF-8", null)
              } else {
                webViewRef?.reload()
              }
            },
            modifier = Modifier.testTag("nav_reload_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reload Page")
          }
          FilledTonalButton(
            onClick = { showConfigSheet = true },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.testTag("source_button")
          ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Edit Source / URL", fontSize = 13.sp)
          }
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .testTag("web_view_container"),
        factory = { context ->
          WebView(context).apply {
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              loadWithOverviewMode = true
              useWideViewPort = true
              allowFileAccess = true
              allowContentAccess = true
              mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
              cacheMode = WebSettings.LOAD_DEFAULT
              userAgentString = "${settings.userAgentString} MobileApp/1.0"
            }

            webViewClient = object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                isLoading = true
                currentUrl = url ?: ""
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                isLoading = false
                currentUrl = url ?: ""
                pageTitle = view?.title ?: "Web App"
                canGoBack = view?.canGoBack() ?: false
                canGoForward = view?.canGoForward() ?: false
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                consoleLogs.add("⚠️ Error [${error?.errorCode}]: ${error?.description}")
              }
            }

            webChromeClient = object : WebChromeClient() {
              override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrEmpty()) {
                  pageTitle = title
                }
              }

              override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                  val logEntry = "[${consoleMessage.messageLevel()}] ${consoleMessage.message()} (line ${consoleMessage.lineNumber()})"
                  consoleLogs.add(logEntry)
                }
                return true
              }
            }

            loadDataWithBaseURL(null, customHtmlContent, "text/html", "UTF-8", null)
            webViewRef = this
          }
        },
        update = { webView ->
          webViewRef = webView
        }
      )

      if (isLoading) {
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
        )
      }
    }
  }

  // Config & Source Modal Sheet
  if (showConfigSheet) {
    ModalBottomSheet(
      onDismissRequest = { showConfigSheet = false },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .navigationBarsPadding()
          .imePadding()
      ) {
        Text(
          text = "Web App Source & URL",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Load an external web application URL, test your custom bundle, or convert your code into an APK.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        OutlinedTextField(
          value = inputUrl,
          onValueChange = { inputUrl = it },
          label = { Text("Web App URL (HTTPS/HTTP)") },
          placeholder = { Text("https://my-app.example.com") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("url_input_field"),
          shape = RoundedCornerShape(12.dp),
          leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Button(
            onClick = {
              if (inputUrl.isNotBlank()) {
                val formatted = if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
                  "https://$inputUrl"
                } else inputUrl
                webViewRef?.loadUrl(formatted)
                showConfigSheet = false
              }
            },
            modifier = Modifier
              .weight(1f)
              .testTag("load_url_button"),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Load Live URL")
          }

          OutlinedButton(
            onClick = {
              webViewRef?.loadDataWithBaseURL(null, customHtmlContent, "text/html", "UTF-8", null)
              showConfigSheet = false
            },
            modifier = Modifier
              .weight(1f)
              .testTag("load_html_button"),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.Html, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Load HTML")
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "📱 How to test & export APK:",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "1. You can upload or paste your web app files (HTML, CSS, JS, React/Vite build).\n" +
                  "2. Test features live directly on this emulator view.\n" +
                  "3. To download the standalone APK file for your Android phone, click the Settings / Export icon in AI Studio and select 'Download APK'.",
              style = MaterialTheme.typography.bodySmall,
              lineHeight = 18.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  // Console Logs Dialog
  if (showConsoleDialog) {
    AlertDialog(
      onDismissRequest = { showConsoleDialog = false },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Web Console Logs", fontWeight = FontWeight.Bold)
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
}
