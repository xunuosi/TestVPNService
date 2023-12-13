package com.ns.testvpnservice

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ns.testvpnservice.monitor.LocalService
import com.ns.testvpnservice.service.MyVPNService
import com.ns.testvpnservice.ui.theme.TestVPNServiceTheme
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var someActivityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestVPNServiceTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }

//        thread(start=true) {
//            LocalService(39399).start()
//        }



        someActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
            // Handle the result in onActivityResult
            if (result.resultCode == RESULT_OK) {
                startService(getServiceIntent().setAction(MyVPNService.ACTION_CONNECT));
            }
        }

        val intent: Intent? = VpnService.prepare(this)
        intent?.let {
            someActivityResultLauncher.launch(it)
        } ?: run {
            startService(getServiceIntent().setAction(MyVPNService.ACTION_CONNECT));
        }
    }

    private fun getServiceIntent(): Intent {
        return Intent(this, MyVPNService::class.java)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TestVPNServiceTheme {
        Greeting("MyVPN")
    }
}