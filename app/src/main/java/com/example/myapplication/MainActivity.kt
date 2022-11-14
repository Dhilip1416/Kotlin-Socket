package com.example.myapplication

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import java.io.*
import java.lang.Exception
import java.lang.NumberFormatException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var clientThread: ClientThread? = null
    private var thread: Thread? = null
    private var msgList: LinearLayout? = null
    private var handler: Handler? = null
    private var clientTextColor = 0
    private var edMessage: EditText? = null
    private var etIP: EditText? = null
    private var etPort: EditText? = null
    private var scrollView: ScrollView? = null
    lateinit var m_usbManager: UsbManager
    var m_device: UsbDevice? = null
    var m_serial: UsbSerialDevice? = null
    var m_connection: UsbDeviceConnection? = null
    val ACTION_USB_PERMISSION = "permission"


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        m_usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(broadcastReceiver, filter)


        title = "Client"
        threadPool = Executors.newFixedThreadPool(2)
        clientTextColor = ContextCompat.getColor(this, R.color.black)
        handler = Handler()
        msgList = findViewById(R.id.msgList)
        edMessage = findViewById(R.id.edMessage)
        etIP = findViewById(R.id.etIP)
        etPort = findViewById(R.id.etPort)
        scrollView = findViewById(R.id.scrll)
    }

    fun textView(message: String?, color: Int): TextView {
        if (null == message || message.trim { it <= ' ' }.isEmpty()) {
        }
        val tv = TextView(this)
        tv.setTextColor(color)
        tv.text = "$message [$time]"
        tv.textSize = 20f
        tv.setPadding(0, 5, 0, 0)
        return tv
    }

    fun showMessage(message: String?, color: Int) {
        handler!!.post { msgList!!.addView(textView(message, color)) }
    }

    override fun onClick(view: View) {
        try {
            if (view.id == R.id.connect_server) {
                Toast.makeText(this, "check your wifi connection", Toast.LENGTH_SHORT).show()
                SERVER_IP = etIP!!.text.toString().trim { it <= ' ' }
                SERVER_PORT = etPort!!.text.toString().trim { it <= ' ' }.toInt()
                msgList!!.removeAllViews()
                showMessage("Connecting...", clientTextColor)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        clientThread = ClientThread()
                        thread = Thread(clientThread)
                        thread!!.start()
                    } else {
                        val intent = Intent()
                        intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        val uri = Uri.fromParts("package", this@MainActivity.packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }
                }
                clientThread = ClientThread()
                thread = Thread(clientThread)
                thread!!.start()
                return
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            Toast.makeText(this, "Please Enter IP address and Port Number", Toast.LENGTH_SHORT)
                .show()
        }
    }

    internal inner class ClientThread : Runnable {
        private var socket: Socket? = null
        private var input: BufferedReader? = null
        override fun run() {
            try {
                val serverAddr = InetAddress.getByName(SERVER_IP)
                socket = Socket(serverAddr, SERVER_PORT)
                while (!Thread.currentThread().isInterrupted) {
                    if (socket!!.isConnected) {
                        startUsbConnecting()
                        showMessage("Connected to Server...", clientTextColor)
                        input = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                        val message = input!!.readLine()
                        //                        char value_char = (char) message;
                        msg = message.toString()
                        threadPool!!.execute(TextThread())
                        showMessage("Server: " + msg, clientTextColor)
                        scrollView!!.fullScroll(ScrollView.FOCUS_DOWN)
                        Log.d("messsage:", msg!!)
                        val clientMessage = msg
                        sendData(msg!!)
                        showMessage(clientMessage, Color.BLUE)
                        if (null != clientThread) {
                            clientThread!!.sendMessage(clientMessage)
                        }
                        if (message.equals(-1) ) {
                            disconnect()
                            Thread.currentThread().interrupt()
                            showMessage("Server Disconnected..........!", Color.RED)
                            break
                        }
                    } else {
                        showMessage("Check your network...", clientTextColor)
                    }
                }
            } catch (e1: UnknownHostException) {
                e1.printStackTrace()
            } catch (e1: IOException) {
                e1.printStackTrace()
            }
        }

        fun sendMessage(message: String?) {
            Thread {
                try {
                    if (null != socket) {
                        val out = PrintWriter(
                            BufferedWriter(
                                OutputStreamWriter(socket!!.getOutputStream())
                            ),
                            true
                        )
                        out.println(message)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    val time: String
        get() {
            @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("HH:mm:ss")
            return sdf.format(Date())
        }

    override fun onDestroy() {
        super.onDestroy()
        if (null != clientThread) {
            clientThread!!.sendMessage("Disconnect")
            clientThread = null
        }
    }

    internal class TextThread : Runnable {
        override fun run() {
            val fileName = "Control" + ".txt"
            try {
                val root = File(
                    Environment.getExternalStorageDirectory().toString() + File.separator + "Socket"
                )
                root.mkdirs()
                val text_file = File(root, fileName)
                val writer = FileWriter(text_file, true)
                Log.d("File created", writer.toString())
                writer.append(msg).append("\n")
                writer.flush()
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        var SERVER_PORT = 0
        var SERVER_IP: String? = null
        var threadPool: ExecutorService? = null
        var msg: String? = null
    }

    //USB

    private fun startUsbConnecting() {
        val usbDevices: HashMap<String, UsbDevice>? = m_usbManager.deviceList
        if (!usbDevices?.isEmpty()!!) {
            var keep = true
            usbDevices.forEach{ entry ->
                m_device = entry.value
                val deviceVendorId: Int? = m_device?.vendorId
                Log.i("serial", "vendorId: "+deviceVendorId)
                //if (deviceVendorId == 6790) {
                if (m_usbManager.hasPermission(m_device)){
                    val intent: PendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION),
                        PendingIntent.FLAG_MUTABLE
                    )
                    m_usbManager.requestPermission(m_device, intent)
                    keep = false
                    Log.i("serial", "connection successful")
                } else {
                    m_connection = null
                    m_device = null
                    Log.i("serial", "unable to connect")
                }
                if (!keep) {
                    return
                }
            }
        } else {
            Log.i("serial", "no usb device connected")
        }
    }
    private fun sendData(input: String) {
        m_serial?.write(input.toByteArray())
        Log.i("serial", "sending data: "+input.toByteArray())

    }

    private fun disconnect() {
        m_serial?.close()
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action!! == ACTION_USB_PERMISSION) {
                val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted) {
                    m_connection = m_usbManager.openDevice(m_device)
                    m_serial = UsbSerialDevice.createUsbSerialDevice(m_device, m_connection)
                    if (m_serial != null) {
                        if (m_serial!!.open()) {
                            m_serial!!.setBaudRate(115200)
                            m_serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                            m_serial!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                            m_serial!!.setParity(UsbSerialInterface.PARITY_NONE)
                            m_serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                        } else {
                            Log.i("Serial", "port not open")
                        }
                    } else {
                        Log.i("Serial", "port is null")
                    }
                } else {
                    Log.i("Serial","permission not granted")
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                startUsbConnecting()
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                disconnect()
            }
        }
    }
}