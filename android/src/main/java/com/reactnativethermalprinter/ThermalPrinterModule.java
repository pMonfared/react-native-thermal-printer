package com.reactnativethermalprinter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.bumptech.glide.Glide;
import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.EscPosPrinterCommands;
import com.dantsu.escposprinter.connection.DeviceConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.escposprinter.connection.tcp.TcpConnection;
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.dantsu.escposprinter.exceptions.EscPosEncodingException;
import com.dantsu.escposprinter.exceptions.EscPosParserException;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.text.TextUtils;

import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;

import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import android.util.Log;
import android.util.DisplayMetrics;

@ReactModule(name = ThermalPrinterModule.NAME)
public class ThermalPrinterModule extends ReactContextBaseJavaModule {
  private static final String LOG_TAG = "RN_Thermal_Printer";
  public static final String NAME = "ThermalPrinterModule";
  private Promise jsPromise;
  private ArrayList<BluetoothConnection> btDevicesList = new ArrayList();

  public ThermalPrinterModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void printTcp(String ipAddress, double port, String payload, boolean autoCut, boolean openCashbox, double mmFeedPaper, double printerDpi, double printerWidthMM, double printerNbrCharactersPerLine, double timeout, Promise promise) {
//
//        05-05-2021
//        https://reactnative.dev/docs/native-modules-android
//        The following types are currently supported but will not be supported in TurboModules. Please avoid using them:
//
//        Integer -> ?number
//        int -> number
//        Float -> ?number
//        float -> number
//
    this.jsPromise = promise;
    try {
        TcpConnection connection = new TcpConnection(ipAddress, (int) port, (int) timeout);
        this.printImgs(connection, payload, autoCut, openCashbox, mmFeedPaper, printerDpi, printerWidthMM, printerNbrCharactersPerLine);
      } catch (Exception e) {
        this.jsPromise.reject("Connection Error", e.getMessage());
      }

    try {
      TcpConnection connection = new TcpConnection(ipAddress, (int) port, (int) timeout);
      this.printIt(connection, payload, autoCut, openCashbox, mmFeedPaper, printerDpi, printerWidthMM, printerNbrCharactersPerLine);
    } catch (Exception e) {
      this.jsPromise.reject("Connection Error", e.getMessage());
    }
  }

  @ReactMethod
  public void printBluetooth(String macAddress, String payload, boolean autoCut, boolean openCashbox, double mmFeedPaper, double printerDpi, double printerWidthMM, double printerNbrCharactersPerLine, Promise promise) {
    this.jsPromise = promise;
    BluetoothConnection btPrinter;

    if (TextUtils.isEmpty(macAddress)) {
      btPrinter = BluetoothPrintersConnections.selectFirstPaired();
    } else {
      btPrinter = getBluetoothConnectionWithMacAddress(macAddress);
    }

    if (btPrinter == null) {
      this.jsPromise.reject("Connection Error", "Bluetooth Device Not Found");
    }

    if (ContextCompat.checkSelfPermission(getCurrentActivity(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(getCurrentActivity(), new String[]{Manifest.permission.BLUETOOTH}, 1);
    } else {
      try {
        this.printImgs(btPrinter.connect(), payload, autoCut, openCashbox, mmFeedPaper, printerDpi, printerWidthMM, printerNbrCharactersPerLine);
      } catch (Exception e) {
        this.jsPromise.reject("Connection Error", e.getMessage());
      }
      try {
        this.printIt(btPrinter.connect(), payload, autoCut, openCashbox, mmFeedPaper, printerDpi, printerWidthMM, printerNbrCharactersPerLine);
      } catch (Exception e) {
        this.jsPromise.reject("Connection Error", e.getMessage());
      }
    }
  }

  @ReactMethod
  public void getBluetoothDeviceList(Promise promise) {
    this.jsPromise = promise;
    if (ContextCompat.checkSelfPermission(getCurrentActivity(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(getCurrentActivity(), new String[]{Manifest.permission.BLUETOOTH}, 1);
    } else {
      try {
        Set<BluetoothDevice> pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
        WritableArray rnArray = new WritableNativeArray();
        if (pairedDevices.size() > 0) {
          int index = 0;
          for (BluetoothDevice device : pairedDevices) {
            btDevicesList.add(new BluetoothConnection(device));
            JSONObject jsonObj = new JSONObject();

            String deviceName = device.getName();
            String macAddress = device.getAddress();

            jsonObj.put("deviceName", deviceName);
            jsonObj.put("macAddress", macAddress);
            WritableMap wmap = convertJsonToMap(jsonObj);
            rnArray.pushMap(wmap);
          }
        }
        jsPromise.resolve(rnArray);


      } catch (Exception e) {
        this.jsPromise.reject("Bluetooth Error", e.getMessage());
      }
    }
  }

  private Bitmap getBitmapFromUrl(String url) {
    try {
      Bitmap bitmap = Glide
        .with(getCurrentActivity())
        .asBitmap()
        .load(url)
        .submit()
        .get();
      return bitmap;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Synchronous printing
   */

   public Bitmap printBitmap(String encodedString, int pixelWidth) {
    final String pureBase64Encoded = encodedString.substring(encodedString.indexOf(",")  + 1);
    final byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);
    Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    int w = decodedBitmap.getWidth();
    Integer h = decodedBitmap.getHeight();
    return Bitmap.createScaledBitmap(decodedBitmap, pixelWidth, (pixelWidth / w) * h, false);
  }

  private String preprocessTags(EscPosPrinter printer, String text) {

    Pattern p = Pattern.compile("(?<=\\<img\\>)(.*)(?=\\<\\/img\\>)");
    Matcher m = p.matcher(text);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String firstGroup = m.group(1);
      m.appendReplacement(sb, "");
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private void printImgs(DeviceConnection printerConnection, String payload, boolean autoCut, boolean openCashbox, double mmFeedPaper, double printerDpi, double printerWidthMM, double printerNbrCharactersPerLine) {
    try {
      EscPosPrinterCommands printerCommands = new EscPosPrinterCommands(printerConnection);

      Pattern p = Pattern.compile("(?<=\\<img\\>)(.*)(?=\\<\\/img\\>)");
      Matcher m = p.matcher(payload);

      while (m.find()) {
        String firstGroup = m.group(1);

        final String pureBase64Encoded = firstGroup.substring(firstGroup.indexOf(",")  + 1);
        final byte[] decodedString = Base64.decode(pureBase64Encoded, Base64.DEFAULT);
        Bitmap originalBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        int targetWidth = 383; // 48mm printing zone with 203dpi => 383px
        Bitmap rescaledBitmap = Bitmap.createScaledBitmap(
           originalBitmap, 
           targetWidth, 
           Math.round(((float) originalBitmap.getHeight()) * ((float) targetWidth) / ((float) originalBitmap.getWidth())), 
           true
        );

        printerCommands.connect();
        printerCommands.reset();
        printerCommands.printImage(EscPosPrinterCommands.bitmapToBytes(rescaledBitmap));
        printerCommands.feedPaper(50);
        printerCommands.cutPaper();
      }
      printerCommands.disconnect();
      this.jsPromise.resolve(true);
    } catch (EscPosConnectionException e) {
      this.jsPromise.reject("Broken connection", e.getMessage());
    } catch (Exception e) {
      this.jsPromise.reject("ERROR", e.getMessage());
    }
  }

  private void printIt(DeviceConnection printerConnection, String payload, boolean autoCut, boolean openCashbox, double mmFeedPaper, double printerDpi, double printerWidthMM, double printerNbrCharactersPerLine) {
    try {
      EscPosPrinter printer = new EscPosPrinter(printerConnection,(int) printerDpi, (float) printerWidthMM, (int) printerNbrCharactersPerLine );
      String processedPayload = preprocessTags(printer, payload);
      
    

      if (openCashbox) {
        printer.printFormattedTextAndOpenCashBox(processedPayload, (float) mmFeedPaper);
      } else if (autoCut) {
        printer.printFormattedTextAndCut(processedPayload, (float) mmFeedPaper);
      } else {
        printer.printFormattedText(processedPayload, (float) mmFeedPaper);
      }

      printer.disconnectPrinter();
      this.jsPromise.resolve(true);
    } catch (EscPosConnectionException e) {
      this.jsPromise.reject("Broken connection", e.getMessage());
    } catch (EscPosParserException e) {
      this.jsPromise.reject("Invalid formatted text", e.getMessage());
    } catch (EscPosEncodingException e) {
      this.jsPromise.reject("Bad selected encoding", e.getMessage());
    } catch (EscPosBarcodeException e) {
      this.jsPromise.reject("Invalid barcode", e.getMessage());
    } catch (Exception e) {
      this.jsPromise.reject("ERROR", e.getMessage());
    }
  }

  private BluetoothConnection getBluetoothConnectionWithMacAddress(String macAddress) {
    for (BluetoothConnection device : btDevicesList) {
      if (device.getDevice().getAddress().contentEquals(macAddress))
        return device;
    }
    return null;
  }

  private static WritableMap convertJsonToMap(JSONObject jsonObject) throws JSONException {
    WritableMap map = new WritableNativeMap();

    Iterator<String> iterator = jsonObject.keys();
    while (iterator.hasNext()) {
      String key = iterator.next();
      Object value = jsonObject.get(key);
      if (value instanceof JSONObject) {
        map.putMap(key, convertJsonToMap((JSONObject) value));
      } else if (value instanceof Boolean) {
        map.putBoolean(key, (Boolean) value);
      } else if (value instanceof Integer) {
        map.putInt(key, (Integer) value);
      } else if (value instanceof Double) {
        map.putDouble(key, (Double) value);
      } else if (value instanceof String) {
        map.putString(key, (String) value);
      } else {
        map.putString(key, value.toString());
      }
    }
    return map;
  }
}
