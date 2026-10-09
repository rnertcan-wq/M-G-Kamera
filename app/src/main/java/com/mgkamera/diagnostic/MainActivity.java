package com.mgkamera.diagnostic;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Size;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.*;

/** Independent Camera2 probe. No OEM code, models or libraries are included. */
public final class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private Handler cameraHandler;
    private CameraManager manager;
    private TextView output;
    private Button standard, hd;
    private JSONObject report = new JSONObject();
    private String backId;
    private CameraCharacteristics back;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private boolean busy;
    private volatile int generation;
    private Runnable timeout;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        worker = new HandlerThread("camera-probe"); worker.start();
        cameraHandler = new Handler(worker.getLooper());
        manager = (CameraManager)getSystemService(CAMERA_SERVICE);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad,pad,pad,pad);
        TextView title = new TextView(this); title.setText("M-G Kamera · Cihaz tanılama"); title.setTextSize(22); layout.addView(title);
        TextView note = new TextView(this);
        note.setText("Bu sürüm kalite geliştirmesi yapmaz. Kamera erişimini ve gerçek JPEG çıktı boyutunu sınar. Fotoğraf testleri önizlemesizdir; telefonu sabit tutup aydınlık bir sahneye yönelt. 64 MP denemesi sürücü tarafından reddedilebilir.");
        layout.addView(note);
        button(layout,"Özellikleri yeniden oku",v -> scan());
        standard = button(layout,"Standart fotoğraf testi",v -> startCapture(false));
        hd = button(layout,"64 MP erişim denemesi",v -> startCapture(true));
        button(layout,"JSON raporunu kaydet",v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/json"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE,"mg-kamera-cihaz-raporu.json"); startActivityForResult(intent,10);
        });
        output = new TextView(this); output.setTextIsSelectable(true); output.setTextSize(12);
        ScrollView scroll = new ScrollView(this); scroll.addView(output);
        layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(layout);
        scan();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA},1);
    }
    private Button button(LinearLayout parent,String label,View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(action); parent.addView(b); return b;
    }
    private static Object value(Object v) throws JSONException {
        if (v == null) return JSONObject.NULL;
        if (v.getClass().isArray()) {
            JSONArray a = new JSONArray(); for(int i=0;i<Array.getLength(v);i++) a.put(value(Array.get(v,i))); return a;
        }
        if (v instanceof Number || v instanceof Boolean || v instanceof String) return v;
        return v.toString();
    }
    private static JSONArray sizes(Size[] sizes) throws JSONException {
        JSONArray a = new JSONArray(); if(sizes!=null) for(Size s:sizes)
            a.put(new JSONObject().put("width",s.getWidth()).put("height",s.getHeight()).put("megapixels",(long)s.getWidth()*s.getHeight()/1e6));
        return a;
    }
    private void scan() {
        if(busy) { Toast.makeText(this,"Önce çekim testinin bitmesini bekle",0).show(); return; }
        JSONObject old = report;
        report = new JSONObject(); backId = null; back = null;
        try {
            report.put("schema",1).put("appVersion","0.1-diagnostic").put("model",Build.MODEL)
                .put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT)
                .put("cameraPermission",checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                .put("captureTests",old.optJSONArray("captureTests") == null ? new JSONArray() : old.optJSONArray("captureTests"));
            JSONArray cameras = new JSONArray(); report.put("cameras",cameras);
            long largest = -1;
            for(String id:manager.getCameraIdList()) {
                JSONObject c = new JSONObject().put("id",id); cameras.put(c);
                try {
                    CameraCharacteristics ch = manager.getCameraCharacteristics(id);
                    c.put("hardwareLevel",value(ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)))
                     .put("facing",value(ch.get(CameraCharacteristics.LENS_FACING)))
                     .put("capabilities",value(ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)))
                     .put("activeArray",value(ch.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)))
                     .put("pixelArray",value(ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)))
                     .put("isoRange",value(ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)))
                     .put("exposureNsRange",value(ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)))
                     .put("fpsRanges",value(ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)))
                     .put("videoStabilizationModes",value(ch.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)));
                    StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    c.put("streams",describeMap(map));
                    c.put("maximumResolutionStreams",describeMap(ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)));
                    JSONObject vendor = new JSONObject(); c.put("vendorCharacteristics",vendor);
                    for(CameraCharacteristics.Key<?> key:ch.getKeys()) {
                        if(!key.getName().startsWith("android.")) {
                            try { vendor.put(key.getName(),value(ch.get(key))); }
                            catch(Exception e) { vendor.put(key.getName(),"ERROR: "+e); }
                        }
                    }
                    JSONArray requests = new JSONArray(); c.put("vendorRequestKeys",requests);
                    List<CaptureRequest.Key<?>> keys = ch.getAvailableCaptureRequestKeys();
                    if(keys!=null) for(CaptureRequest.Key<?> k:keys) if(!k.getName().startsWith("android.")) requests.put(k.getName());
                    Size max = maxSize(map == null ? null : map.getOutputSizes(ImageFormat.JPEG));
                    long area = max == null ? 0 : (long)max.getWidth()*max.getHeight();
                    if(Objects.equals(ch.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK) && area>largest) {
                        largest=area; backId=id; back=ch;
                    }
                } catch(Exception e) { c.put("error",e.toString()); }
            }
            report.put("selectedBackCamera",value(backId));
        } catch(Exception e) { try { report.put("scanError",e.toString()); } catch(Exception ignored) {} }
        refresh();
    }
    private JSONObject describeMap(StreamConfigurationMap map) throws JSONException {
        JSONObject o = new JSONObject(); if(map==null) return o.put("available",false);
        o.put("available",true);
        for(int format:new int[]{ImageFormat.JPEG,ImageFormat.YUV_420_888,ImageFormat.RAW_SENSOR}) {
            o.put("format_"+format,sizes(map.getOutputSizes(format)));
            o.put("highResolution_"+format,sizes(map.getHighResolutionOutputSizes(format)));
        }
        o.put("recorder",sizes(map.getOutputSizes(MediaRecorder.class)));
        return o;
    }
    private static Size maxSize(Size[] list) {
        Size best=null; if(list!=null) for(Size s:list) if(best==null || (long)s.getWidth()*s.getHeight()>(long)best.getWidth()*best.getHeight()) best=s; return best;
    }
    private Size vendorSize() {
        if(back==null) return null;
        for(CameraCharacteristics.Key<?> key:back.getKeys()) if(key.getName().equals("com.transsion.availableHDStreamConfigurations")) {
            Object v=back.get(key); if(!(v instanceof int[])) return null;
            int[] a=(int[])v; Size best=null;
            for(int i=0;i+3<a.length;i+=4) if(a[i]==ImageFormat.JPEG && a[i+3]==0 && a[i+1]>0 && a[i+2]>0) {
                Size s=new Size(a[i+1],a[i+2]);
                if(best==null || (long)s.getWidth()*s.getHeight()>(long)best.getWidth()*best.getHeight()) best=s;
            }
            return best;
        }
        return null;
    }
    private void refresh() {
        try { output.setText(report.toString(2)); } catch(Exception e) { output.setText(e.toString()); }
        boolean ready=!busy && backId!=null && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;
        standard.setEnabled(ready); hd.setEnabled(ready);
    }
    private void startCapture(boolean high) {
        if(busy || back==null) return;
        Size size;
        try {
            size= high ? vendorSize() : maxSize(back.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG));
        } catch(Exception e) { Toast.makeText(this,e.toString(),1).show(); return; }
        if(size==null) { Toast.makeText(this,"İstenen JPEG boyutu bu uygulamaya sunulmuyor",1).show(); return; }
        busy=true; refresh(); int token=++generation;
        JSONObject test=new JSONObject();
        try {
            test.put("mode",high?"vendor-HD-session-probe":"standard-JPEG").put("camera",backId)
                .put("requestedWidth",size.getWidth()).put("requestedHeight",size.getHeight())
                .put("vendorControlsApplied",false).put("status","running");
            report.getJSONArray("captureTests").put(test);
        } catch(JSONException e) { busy=false; refresh(); return; }
        // Only a session feasibility probe: do not guess proprietary mode values.
        timeout=()->finish(token,test,"timeout",null); ui.postDelayed(timeout,25000);
        final Size selected=size;
        cameraHandler.post(()-> {
            try {
                reader=ImageReader.newInstance(selected.getWidth(),selected.getHeight(),ImageFormat.JPEG,1);
                reader.setOnImageAvailableListener(r -> {
                    if(token!=generation) return;
                    try(Image image=r.acquireNextImage()) {
                        if(image==null) return;
                        ByteBuffer buffer=image.getPlanes()[0].getBuffer(); byte[] bytes=new byte[buffer.remaining()]; buffer.get(bytes);
                        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
                        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                        if(options.outWidth<=0 || options.outHeight<=0) throw new IOException("JPEG başlığı okunamadı");
                        Uri saved=saveJpeg(bytes);
                        ui.post(()-> {
                            if(token!=generation) return;
                            try {
                                test.put("jpegWidth",options.outWidth).put("jpegHeight",options.outHeight)
                                    .put("jpegMegapixels",(long)options.outWidth*options.outHeight/1e6)
                                    .put("bytes",bytes.length).put("savedUri",saved.toString())
                                    .put("requestedSizeMatched",options.outWidth==selected.getWidth() && options.outHeight==selected.getHeight())
                                    .put("note","JPEG boyutu ölçüldü; sensörün doğal çözünürlüğü ve görüntü kalitesi kanıtlanmadı.");
                            } catch(Exception ignored) {}
                            finish(token,test,"jpeg_received",null);
                        });
                    } catch(Exception e) { ui.post(()->finish(token,test,"image_error",e)); }
                },cameraHandler);
                manager.openCamera(backId,new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice cam) {
                        if(token!=generation) { cam.close(); return; }
                        device=cam;
                        try {
                            cam.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback() {
                                @Override public void onConfigured(CameraCaptureSession s) {
                                    if(token!=generation) {s.close(); return;} session=s;
                                    try {
                                        CaptureRequest.Builder b=cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                        b.addTarget(reader.getSurface()); b.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                                        b.set(CaptureRequest.JPEG_QUALITY,(byte)100);
                                        s.capture(b.build(),new CameraCaptureSession.CaptureCallback() {
                                            @Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest request,CaptureFailure failure) {
                                                ui.post(()->finish(token,test,"capture_failed",new IOException("reason="+failure.getReason())));
                                            }
                                        },cameraHandler);
                                    } catch(Exception e) {ui.post(()->finish(token,test,"capture_error",e));}
                                }
                                @Override public void onConfigureFailed(CameraCaptureSession s) {s.close(); ui.post(()->finish(token,test,"session_rejected",null));}
                            },cameraHandler);
                        } catch(Exception e) {ui.post(()->finish(token,test,"session_error",e));}
                    }
                    @Override public void onDisconnected(CameraDevice cam) {cam.close(); ui.post(()->finish(token,test,"disconnected",null));}
                    @Override public void onError(CameraDevice cam,int error) {cam.close(); ui.post(()->finish(token,test,"camera_error",new IOException("code="+error)));}
                },cameraHandler);
            } catch(Exception e) {ui.post(()->finish(token,test,"open_error",e));}
        });
    }
    private Uri saveJpeg(byte[] bytes) throws IOException {
        ContentValues values=new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME,"MG-probe-"+System.currentTimeMillis()+".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MGKamera");
        values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(uri==null) throw new IOException("MediaStore kaydı oluşturulamadı");
        try {
            try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null) throw new IOException("Fotoğraf dosyası açılamadı"); out.write(bytes);
            }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING,0); getContentResolver().update(uri,values,null,null); return uri;
        } catch(Exception e) {getContentResolver().delete(uri,null,null); throw new IOException(e);}
    }
    private void finish(int token,JSONObject test,String status,Exception error) {
        if(token!=generation || !busy) return;
        generation++; busy=false; if(timeout!=null) ui.removeCallbacks(timeout);
        try {test.put("status",status); if(error!=null) test.put("error",error.toString());} catch(Exception ignored) {}
        cameraHandler.post(this::closeCamera); refresh();
    }
    private void closeCamera() {
        if(session!=null) {session.close(); session=null;}
        if(device!=null) {device.close(); device=null;}
        if(reader!=null) {reader.close(); reader=null;}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results); scan();
    }
    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req==10 && result==RESULT_OK && data!=null && data.getData()!=null) {
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                if(out==null) throw new IOException("Rapor dosyası açılamadı");
                out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Toast.makeText(this,"Rapor kaydedildi",0).show();
            } catch(Exception e) {Toast.makeText(this,e.toString(),1).show();}
        }
    }
    @Override protected void onStop() {
        super.onStop();
        if(busy) {
            try { JSONArray tests=report.getJSONArray("captureTests"); finish(generation,tests.getJSONObject(tests.length()-1),"interrupted",null); }
            catch(Exception ignored) {}
        }
    }
    @Override protected void onDestroy() {
        if(timeout!=null) ui.removeCallbacks(timeout);
        cameraHandler.post(()-> {closeCamera(); worker.quitSafely();}); super.onDestroy();
    }
}
