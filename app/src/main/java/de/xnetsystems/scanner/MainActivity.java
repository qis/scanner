package de.xnetsystems.scanner;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.os.Handler;
import android.os.ParcelFileDescriptor;

import com.datecs.barcode.Barcode;
import com.datecs.barcode.Intermec;
import com.datecs.linea.LineaPro;
import com.datecs.linea.LineaProInformation;

public class MainActivity extends AppCompatActivity implements Runnable {
  private static final String TAG = "scanner.xnetsystems.de";
  private static final String ACTION_USB_PERMISSION = "de.xnetsystems.scanner.USB_PERMISSION";
  private static final byte[] INIT_DATA = new byte[] { 0x00, 0x00, 0x01, 0x01, 0x03, 0x01, 0x05 };

  private Handler handler = new Handler();
  private UsbManager usbManager;
  private boolean permissionRequestPending = false;
  private UsbAccessory accessory;
  private ParcelFileDescriptor fileDescriptor;
  private FileInputStream inputStream;
  private FileOutputStream outputStream;
  private LineaPro scanner;

  private TextView text;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    text = findViewById(R.id.text);
    text.setMovementMethod(new ScrollingMovementMethod());
    usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
    registerReceiver(usbReceiver, filter);
  }

  @Override
  protected void onDestroy() {
    unregisterReceiver(usbReceiver);
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    log("Activity resumed.");
    if (!permissionRequestPending) {
      connect();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    log("Activity paused.");
    if (!permissionRequestPending) {
      close();
    }
  }

  public void onOpen(View view) {
    log("Manual open.");
    connect();
  }

  public void onClose(View view) {
    log("Manual close.");
    close();
  }

  private void connect() {
    log("Searching for scanner...");
    if (inputStream != null && outputStream != null) {
      log("Scanner already connected.");
      return;
    }
    UsbAccessory[] accessories = usbManager.getAccessoryList();
    UsbAccessory accessory = (accessories == null ? null : accessories[0]);
    if (accessory == null) {
      log("Scanner not found.");
      return;
    }
    if (usbManager.hasPermission(accessory)) {
      open(accessory);
    } else synchronized (usbReceiver) {
      if (!permissionRequestPending) {
        log("Requesting permission...");
        permissionRequestPending = true;
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        usbManager.requestPermission(accessory, pendingIntent);
      }
    }
  }

  private void open(UsbAccessory accessory) {
    log("Opening scanner connection...");
    fileDescriptor = usbManager.openAccessory(accessory);
    if (fileDescriptor == null) {
      log("Could not open scanner connection.");
      return;
    }
    this.accessory = accessory;
    FileDescriptor fd = fileDescriptor.getFileDescriptor();
    inputStream = new FileInputStream(fd);
    outputStream = new FileOutputStream(fd);
    handler.removeCallbacksAndMessages(null);
    handler.postDelayed(this, 1500);
  }

  private void close() {
    log("Closing scanner connection...");
    handler.removeCallbacksAndMessages(null);
    if (scanner != null) try {
      scanner.close();
    } catch (IOException ignored) {
    } finally {
      scanner = null;
    }
    if (inputStream != null) try {
      inputStream.close();
    } catch (IOException ignored) {
    } finally {
      inputStream = null;
    }
    if (outputStream != null) try {
      outputStream.close();
    } catch (IOException ignored) {
    } finally {
      outputStream = null;
    }
    if (fileDescriptor != null) try {
      fileDescriptor.close();
    } catch (IOException ignored) {
    } finally {
      fileDescriptor = null;
    }
    accessory = null;
    log("Scanner connection closed.");
  }

  @Override
  public void run() {
    log("Initializing scanner...");
    try {
      outputStream.write(INIT_DATA);
      outputStream.flush();
    } catch (IOException ignored) {
      log("Scanner initialization failed.");
    }
    try {
      scanner = new LineaPro(inputStream, outputStream);
      LineaProInformation info = scanner.getInformation();
      log("Scanner initialized: " + info.getName());
      scanner.enableScanButton(true);
      scanner.enableBatteryCharge(true);
      if (info.hasExternalSpeaker()) {
        scanner.enableExternalSpeakerButton(true);
        scanner.enableExternalSpeakerButton(true);
        scanner.setAutoOffTime(true, 10 * 1000);
      }
      if (info.hasIntermecEngine()) {
        Intermec engine = (Intermec) scanner.bcGetEngine();
        if (engine != null) {
          engine.enableCode128(true);
          engine.saveSymbology();
        }
      }
      scanner.bcSetMode(LineaPro.BARCODE_MODE_SINGLE_SCAN);
      scanner.bcStopScan();
      scanner.bcStopBeep();
      scanner.setBarcodeListener(new LineaPro.BarcodeListener() {
        @Override
        public void onReadBarcode(final Barcode barcode) {
          try {
            log("Barcode: " + barcode.getDataString());
            scanner.beep(100, new int[] { 2730, 150, 65000, 20, 2730, 150 });
          }
          catch (IOException ignored) {
          }
        }
      });
    }
    catch (IOException e) {
      log("Scanner error: " + e.getMessage());
      close();
    }
  }

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        synchronized (this) {
          UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            log("Scanner permission granted.");
            open(accessory);
          } else {
            log("Scanner permission denied.");
          }
          permissionRequestPending = false;
        }
      } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
        UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        if (accessory != null && accessory.equals(MainActivity.this.accessory)) {
          log("Scanner disconnected.");
          close();
        }
      }
    }
  };

  public void log(final String message) {
    Log.d(TAG, message);
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        text.append(message + "\n");
        int distance = 0;
        final Layout layout = text.getLayout();
        if (layout != null) {
          distance = layout.getLineTop(text.getLineCount()) - text.getHeight();
        }
        text.scrollTo(0, distance > 0 ? distance : 0);
      }
    });
  }
}
