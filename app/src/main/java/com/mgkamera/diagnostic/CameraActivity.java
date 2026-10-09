package com.mgkamera.diagnostic;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Size;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Camera prototype: preview, measured JPEG dimensions, 3A wait, optional supported HQ ISP. */
public final class CameraActivity extends Activity implements TextureView.SurfaceTextureListener {
    private TextureView preview;
    private TextView status;
    private Button shutter, mode;
    private CheckBox hq;
    private HandlerThread thread;
    private Handler camera;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private CameraManager manager;
    private CameraCharacteristics characteristics;
    private CameraDevice device;
    private CameraCaptureSession session;
    private CaptureRequest.Builder previewRequest;
    private ImageReader reader;
    private android.view.Surface previewSurface;
    private String id;
    private Size outputSize, previewSize;
    private boolean high, active, busy;
    private volatile int generation;
    private int afMode=CaptureRequest.CONTROL_AF_MODE_OFF;
    private volatile boolean waiting, fired;
    private Runnable focusTimeout, captureTimeout;
    private JSONObject captureInfo;
    private Integer finalAf,finalAe,finalAwb;
    private boolean selectedHq;
    private int jpegOrientation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        thread=new HandlerThread("MG-Camera");thread.start();camera=new Handler(thread.getLooper());
        manager=(CameraManager)getSystemService(CAMERA_SERVICE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12,32,12,24);
        TextView title=new TextView(this);title.setText("M-G Kamera · 0.3 prototip");title.setTextSize(22);root.addView(title);
        status=new TextView(this);status.setText("Kamera hazırlanıyor…");root.addView(status);
        preview=new TextureView(this);preview.setSurfaceTextureListener(this);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        hq=new CheckBox(this);hq.setText("Desteklenen yüksek kaliteli ISP işleme");hq.setChecked(true);root.addView(hq);
        LinearLayout controls=new LinearLayout(this);
        mode=new Button(this);mode.setText("16 MP · 64 MP’ye geç");mode.setOnClickListener(v->{
            if(busy)return;high=!high;mode.setText(high?"64 MP · 16 MP’ye geç":"16 MP · 64 MP’ye geç");restart();
        });controls.addView(mode,new LinearLayout.LayoutParams(0,-2,1));
        shutter=new Button(this);shutter.setText("Fotoğraf çek");shutter.setEnabled(false);shutter.setOnClickListener(v->shoot());
        controls.addView(shutter,new LinearLayout.LayoutParams(0,-2,1));root.addView(controls);
        Button diagnostic=new Button(this);diagnostic.setText("Cihaz tanılama / test raporu");
        diagnostic.setOnClickListener(v->{if(!busy)startActivity(new Intent(this,MainActivity.class));});root.addView(diagnostic);
        setContentView(root);
    }
    @Override public void onResume() {
        super.onResume();active=true;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA},1);
        else if(preview.isAvailable()) restart();
    }
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] grants) {
        super.onRequestPermissionsResult(code,p,grants);
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED && preview.isAvailable())restart();
        else status.setText("Kamera izni gerekli. Uygulama ayarlarından izin verebilirsin.");
    }
    private static int[][] dimensions(Size[] sizes) {
        if(sizes==null)return null;
        int[][] result=new int[sizes.length][2];
        for(int i=0;i<sizes.length;i++) {result[i][0]=sizes[i].getWidth();result[i][1]=sizes[i].getHeight();}
        return result;
    }
    private static Size largest(Size[] a,Size[] b) {
        int[] best=CameraMath.largest(dimensions(a),dimensions(b));
        return best==null?null:new Size(best[0],best[1]);
    }
    private static Size hdSize(CameraCharacteristics ch) {
        for(CameraCharacteristics.Key<?> k:ch.getKeys())if(k.getName().equals("com.transsion.availableHDStreamConfigurations")) {
            Object value=ch.get(k);if(!(value instanceof int[]))return null;
            int[] best=CameraMath.vendorJpeg((int[])value);
            return best==null?null:new Size(best[0],best[1]);
        }return null;
    }
    private static boolean contains(int[] a,int n) {if(a!=null)for(int x:a)if(x==n)return true;return false;}
    private void restart() {
        if(!active||!preview.isAvailable()||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        int token=++generation;shutter.setEnabled(false);busy=false;waiting=false;fired=false;
        status.setText("Önizleme hazırlanıyor…");final boolean wantHigh=high;
        camera.post(()->{
            close();
            if(token!=generation)return;
            try {
                id=null;characteristics=null;long biggest=-1;
                for(String candidate:manager.getCameraIdList()) {
                    CameraCharacteristics ch=manager.getCameraCharacteristics(candidate);
                    if(!Objects.equals(ch.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK))continue;
                    StreamConfigurationMap m=ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(m==null)continue;
                    Size s=largest(m.getOutputSizes(ImageFormat.JPEG),m.getHighResolutionOutputSizes(ImageFormat.JPEG));
                    if(s!=null && (long)s.getWidth()*s.getHeight()>biggest) {
                        biggest=(long)s.getWidth()*s.getHeight();id=candidate;characteristics=ch;
                    }
                }
                if(id==null)throw new IOException("Arka kamera bulunamadı");
                StreamConfigurationMap map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                outputSize=wantHigh?hdSize(characteristics):largest(map.getOutputSizes(ImageFormat.JPEG),map.getHighResolutionOutputSizes(ImageFormat.JPEG));
                if(outputSize==null)throw new IOException("Bu uygulamaya yüksek çözünürlük boyutu sunulmuyor");
                previewSize=null;
                for(Size s:map.getOutputSizes(SurfaceTexture.class))
                    if(s.getWidth()<=1920 && s.getHeight()<=1440 && Math.abs((double)s.getWidth()/s.getHeight()-(double)outputSize.getWidth()/outputSize.getHeight())<0.05)
                        previewSize=largest(new Size[]{s},previewSize==null?null:new Size[]{previewSize});
                if(previewSize==null)previewSize=new Size(640,480);
                int[] modes=characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                afMode=contains(modes,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)?CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                    contains(modes,CaptureRequest.CONTROL_AF_MODE_AUTO)?CaptureRequest.CONTROL_AF_MODE_AUTO:CaptureRequest.CONTROL_AF_MODE_OFF;
                SurfaceTexture texture=preview.getSurfaceTexture();if(texture==null)return;
                texture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());previewSurface=new Surface(texture);
                reader=ImageReader.newInstance(outputSize.getWidth(),outputSize.getHeight(),ImageFormat.JPEG,1);
                reader.setOnImageAvailableListener(r->receive(r,token),camera);
                ui.post(()->{if(token==generation)transform();});
                manager.openCamera(id,new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice cam) {
                        if(token!=generation){cam.close();return;}device=cam;
                        try {
                            cam.createCaptureSession(Arrays.asList(previewSurface,reader.getSurface()),new CameraCaptureSession.StateCallback() {
                                @Override public void onConfigured(CameraCaptureSession s) {
                                    if(token!=generation){s.close();return;}session=s;
                                    try {
                                        previewRequest=cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                        previewRequest.addTarget(previewSurface);
                                        previewRequest.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                                        previewRequest.set(CaptureRequest.CONTROL_AF_MODE,afMode);
                                        s.setRepeatingRequest(previewRequest.build(),metering(token),camera);
                                        ui.post(()->{
                                            if(token!=generation)return;
                                            status.setText(String.format(Locale.US,"%d × %d · %.1f MP · canlı önizleme",outputSize.getWidth(),outputSize.getHeight(),(long)outputSize.getWidth()*outputSize.getHeight()/1e6));
                                            shutter.setEnabled(true);
                                        });
                                    }catch(Exception e){error(token,"Önizleme",e);}
                                }
                                @Override public void onConfigureFailed(CameraCaptureSession s){s.close();error(token,"Önizleme + fotoğraf oturumu sürücü tarafından reddedildi",null);}
                            },camera);
                        }catch(Exception e){error(token,"Oturum",e);}
                    }
                    @Override public void onDisconnected(CameraDevice cam){cam.close();error(token,"Kamera bağlantısı kesildi",null);}
                    @Override public void onError(CameraDevice cam,int e){cam.close();error(token,"Kamera hata kodu "+e,null);}
                },camera);
            }catch(Exception e){error(token,"Kamera açılamadı",e);}
        });
    }
    private CameraCaptureSession.CaptureCallback metering(final int token) {
        return new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest req,TotalCaptureResult result) {
                if(token!=generation||!waiting||fired)return;
                finalAf=result.get(CaptureResult.CONTROL_AF_STATE);finalAe=result.get(CaptureResult.CONTROL_AE_STATE);finalAwb=result.get(CaptureResult.CONTROL_AWB_STATE);
                boolean focus=afMode==CaptureRequest.CONTROL_AF_MODE_OFF || finalAf==null || finalAf==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || finalAf==CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED;
                boolean exposure=finalAe==null || finalAe==CaptureResult.CONTROL_AE_STATE_CONVERGED || finalAe==CaptureResult.CONTROL_AE_STATE_LOCKED;
                boolean white=finalAwb==null || finalAwb==CaptureResult.CONTROL_AWB_STATE_CONVERGED || finalAwb==CaptureResult.CONTROL_AWB_STATE_LOCKED;
                if(focus&&exposure&&white)still(token,true);
            }
        };
    }
    private void shoot() {
        if(busy||session==null||device==null)return;
        busy=true;selectedHq=hq.isChecked();shutter.setEnabled(false);mode.setEnabled(false);hq.setEnabled(false);
        status.setText("Odak ve pozlama bekleniyor…");
        int token=generation;
        int rotation=getWindowManager().getDefaultDisplay().getRotation();
        int degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;
        Integer sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        jpegOrientation=CameraMath.jpegOrientation(sensor==null?0:sensor,degrees);
        camera.post(()->{
            if(token!=generation)return;
            waiting=true;fired=false;finalAf=null;finalAe=null;finalAwb=null;
            captureInfo=new JSONObject();
            try {
                captureInfo.put("appVersion","0.3-prototype").put("cameraId",id)
                    .put("requestedWidth",outputSize.getWidth()).put("requestedHeight",outputSize.getHeight())
                    .put("jpegOrientation",jpegOrientation).put("hqRequested",selectedHq)
                    .put("vendorControlsApplied",false);
                if(afMode!=CaptureRequest.CONTROL_AF_MODE_OFF) {
                    previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);
                    session.capture(previewRequest.build(),metering(token),camera);
                    previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                }
                focusTimeout=()->still(token,false);camera.postDelayed(focusTimeout,3000);
                captureTimeout=()->error(token,"Çekim zaman aşımı",null);camera.postDelayed(captureTimeout,20000);
            }catch(Exception e){error(token,"Odak",e);}
        });
    }
    private void still(int token,boolean converged) {
        if(token!=generation||!waiting||fired)return;
        fired=true;waiting=false;if(focusTimeout!=null)camera.removeCallbacks(focusTimeout);
        try {
            captureInfo.put("threeAConverged",converged).put("afState",finalAf==null?JSONObject.NULL:finalAf)
                .put("aeState",finalAe==null?JSONObject.NULL:finalAe).put("awbState",finalAwb==null?JSONObject.NULL:finalAwb);
            CaptureRequest.Builder shot=device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            shot.addTarget(reader.getSurface());shot.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
            shot.set(CaptureRequest.CONTROL_AF_MODE,afMode);shot.set(CaptureRequest.JPEG_QUALITY,(byte)100);
            shot.set(CaptureRequest.JPEG_ORIENTATION,jpegOrientation);
            boolean noise=selectedHq&&contains(characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES),CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            boolean edge=selectedHq&&contains(characteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES),CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            if(noise)shot.set(CaptureRequest.NOISE_REDUCTION_MODE,CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            if(edge)shot.set(CaptureRequest.EDGE_MODE,CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            captureInfo.put("highQualityNoiseReduction",noise).put("highQualityEdge",edge);
            ui.post(()->{if(token==generation)status.setText("Fotoğraf çekiliyor…");});
            session.capture(shot.build(),new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest req,TotalCaptureResult r) {
                    if(token!=generation)return;
                    try {
                        captureInfo.put("iso",r.get(CaptureResult.SENSOR_SENSITIVITY)).put("exposureNs",r.get(CaptureResult.SENSOR_EXPOSURE_TIME))
                            .put("focusDistance",r.get(CaptureResult.LENS_FOCUS_DISTANCE));
                    }catch(Exception ignored){}
                }
                @Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest req,CaptureFailure f){error(token,"Çekim başarısız: "+f.getReason(),null);}
            },camera);
        }catch(Exception e){error(token,"Fotoğraf",e);}
    }
    private void receive(ImageReader source,int token) {
        if(token!=generation)return;
        try(Image image=source.acquireNextImage()) {
            if(image==null)return;
            ByteBuffer buffer=image.getPlanes()[0].getBuffer();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
            BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,dimensions);
            if(dimensions.outWidth<=0||dimensions.outHeight<=0)throw new IOException("JPEG başlığı okunamadı");
            String base="MG-camera-"+System.currentTimeMillis();
            save(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,base+".jpg","image/jpeg","Pictures/MGKamera",bytes);
            captureInfo.put("jpegWidth",dimensions.outWidth).put("jpegHeight",dimensions.outHeight).put("bytes",bytes.length)
                .put("sizeMatched",(dimensions.outWidth==outputSize.getWidth()&&dimensions.outHeight==outputSize.getHeight()) || (dimensions.outHeight==outputSize.getWidth()&&dimensions.outWidth==outputSize.getHeight()));
            save(MediaStore.Downloads.EXTERNAL_CONTENT_URI,base+".json","application/json","Download/MGKamera",captureInfo.toString(2).getBytes("UTF-8"));
            resetFocus();
            ui.post(()->{
                if(token!=generation)return;
                busy=false;shutter.setEnabled(true);mode.setEnabled(true);hq.setEnabled(true);
                status.setText("Kaydedildi: "+dimensions.outWidth+" × "+dimensions.outHeight+" · Pictures/MGKamera");
            });
        }catch(Exception e){error(token,"Kaydetme",e);}
    }
    private Uri save(Uri collection,String name,String mime,String path,byte[] bytes)throws IOException {
        ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,path);values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=getContentResolver().insert(collection,values);if(uri==null)throw new IOException("Dosya kaydı oluşturulamadı");
        try {
            try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Dosya açılamadı");out.write(bytes);}
            values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,values,null,null);return uri;
        }catch(Exception e){getContentResolver().delete(uri,null,null);throw new IOException(e);}
    }
    private void resetFocus() {
        if(focusTimeout!=null)camera.removeCallbacks(focusTimeout);if(captureTimeout!=null)camera.removeCallbacks(captureTimeout);
        waiting=false;fired=false;
        try {
            if(session!=null&&previewRequest!=null&&afMode!=CaptureRequest.CONTROL_AF_MODE_OFF) {
                previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                session.capture(previewRequest.build(),null,camera);previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            }
        }catch(Exception ignored){}
    }
    private void error(int token,String message,Exception e) {
        if(token!=generation)return;
        ui.post(()->{
            if(token!=generation)return;
            busy=false;shutter.setEnabled(false);mode.setEnabled(true);hq.setEnabled(true);
            status.setText(message+(e==null?"":" · "+e.getMessage())+". Modu değiştirerek yeniden deneyebilirsin.");
        });
        camera.post(()->{if(token==generation){resetFocus();close();}});
    }
    private void transform() {
        if(previewSize==null||characteristics==null||preview.getWidth()==0)return;
        int rotation=getWindowManager().getDefaultDisplay().getRotation();
        int degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;
        Integer sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int relative=((sensor==null?0:sensor)-degrees+360)%360;
        float vw=preview.getWidth(),vh=preview.getHeight();float bw=previewSize.getWidth(),bh=previewSize.getHeight();
        Matrix m=new Matrix();
        // Undo TextureView's independent x/y stretch, rotate camera buffer, then fit the view.
        m.setScale(bw/vw,bh/vh);m.postTranslate(-bw/2,-bh/2);m.postRotate(relative);
        float fit=(relative%180==0)?Math.min(vw/bw,vh/bh):Math.min(vw/bh,vh/bw);
        m.postScale(fit,fit);m.postTranslate(vw/2,vh/2);preview.setTransform(m);
    }
    private void close() {
        if(focusTimeout!=null)camera.removeCallbacks(focusTimeout);if(captureTimeout!=null)camera.removeCallbacks(captureTimeout);
        waiting=false;fired=false;
        if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}
        if(reader!=null){reader.close();reader=null;}if(previewSurface!=null){previewSurface.release();previewSurface=null;}
        previewRequest=null;
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){restart();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){generation++;camera.post(this::close);return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
    @Override protected void onPause(){active=false;generation++;busy=false;shutter.setEnabled(false);mode.setEnabled(true);hq.setEnabled(true);camera.post(this::close);super.onPause();}
    @Override protected void onDestroy(){camera.post(()->{close();thread.quitSafely();});super.onDestroy();}
}
