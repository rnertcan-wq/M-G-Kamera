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
import android.util.Range;
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
    private Button shutter, mode, lens, video, batch;
    private volatile boolean batchActive;
    private boolean batchAi,batchAudio;
    private List<String> batchPlan;
    private int batchStage;
    private JSONArray batchResults;
    private CheckBox audio;
    private SeekBar zoomSlider, evSlider;
    private TextView zoomLabel,evLabel;
    private Spinner timer;
    private boolean front,countdown;
    private volatile boolean recording;
    private float zoom=1f;
    private int compensation;
    private MediaRecorder recorder;
    private android.os.ParcelFileDescriptor videoFd;
    private Uri videoUri;
    private long videoStarted;
    private Size videoSize;
    private JSONObject videoInfo;
    private Runnable countdownTask;
    private int requestedTimer;
    private long timerStarted;
    private boolean captureResultReceived;
    private byte[] pendingJpeg;
    private CheckBox hq, chroma, ai;
    private boolean selectedAi;
    private volatile boolean processing, processingCancelled;
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
    private boolean selectedHq, selectedChroma;
    private int jpegOrientation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        thread=new HandlerThread("MG-Camera");thread.start();camera=new Handler(thread.getLooper());
        manager=(CameraManager)getSystemService(CAMERA_SERVICE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12,32,12,24);
        TextView title=new TextView(this);title.setText("M-G Kamera · 0.6 AI prototip");title.setTextSize(22);root.addView(title);
        status=new TextView(this);status.setText("Kamera hazırlanıyor…");root.addView(status);
        preview=new TextureView(this);preview.setSurfaceTextureListener(this);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        ScrollView settingsScroll=new ScrollView(this);settingsScroll.addView(panel);
        int panelHeight=(int)Math.min(getResources().getDisplayMetrics().heightPixels*0.43f,330*getResources().getDisplayMetrics().density);
        root.addView(settingsScroll,new LinearLayout.LayoutParams(-1,panelHeight));
        hq=new CheckBox(this);hq.setText("Desteklenen yüksek kaliteli ISP işleme");hq.setChecked(true);panel.addView(hq);
        chroma=new CheckBox(this);chroma.setText("Renkli gürültüyü azalt · orijinal de saklanır");chroma.setChecked(false);panel.addView(chroma);
        ai=new CheckBox(this);ai.setText("AI denoise · tam çözünürlük, yavaş");ai.setChecked(false);panel.addView(ai);
        LinearLayout controls=new LinearLayout(this);
        mode=new Button(this);mode.setText("Yüksek çözünürlüğe geç");mode.setOnClickListener(v->{
            if(busy||countdown)return;high=!high;mode.setText(high?"Standart çözünürlüğe geç":"Yüksek çözünürlüğe geç");restart();
        });controls.addView(mode,new LinearLayout.LayoutParams(0,-2,1));
        shutter=new Button(this);shutter.setText("Fotoğraf çek");shutter.setEnabled(false);shutter.setOnClickListener(v->{if(processing){processingCancelled=true;status.setText("İşlem iptal ediliyor; orijinal korunuyor…");}else requestPhoto();});
        controls.addView(shutter,new LinearLayout.LayoutParams(0,-2,1));panel.addView(controls);
        LinearLayout extras=new LinearLayout(this);
        lens=new Button(this);lens.setText("Ön kamera");lens.setOnClickListener(v->{
            if(busy||countdown)return;front=!front;high=false;zoom=1;compensation=0;
            lens.setText(front?"Arka kamera":"Ön kamera");mode.setText("Yüksek çözünürlüğe geç");restart();
        });extras.addView(lens,new LinearLayout.LayoutParams(0,-2,1));
        video=new Button(this);video.setText("Video kaydet");video.setOnClickListener(v->{
            if(recording)camera.post(()->stopVideo(true));else if(!busy&&!countdown)startVideo();
        });extras.addView(video,new LinearLayout.LayoutParams(0,-2,1));panel.addView(extras);
        LinearLayout settings=new LinearLayout(this);
        audio=new CheckBox(this);audio.setText("Video sesi");audio.setChecked(false);
        audio.setOnCheckedChangeListener((button,on)->{
            if(on&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},2);
        });settings.addView(audio,new LinearLayout.LayoutParams(0,-2,1));
        timer=new Spinner(this);timer.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Zamanlayıcı kapalı","2 saniye","5 saniye","10 saniye"}));
        settings.addView(timer,new LinearLayout.LayoutParams(0,-2,1));panel.addView(settings);
        zoomLabel=new TextView(this);panel.addView(zoomLabel);zoomSlider=new SeekBar(this);zoomSlider.setMax(70);panel.addView(zoomSlider);
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int n,boolean user){if(user){zoom=1+n/10f;updatePreviewControls();}zoomLabel.setText(String.format(Locale.US,"Dijital zoom: %.1f×",zoom));}
        });
        evLabel=new TextView(this);panel.addView(evLabel);evSlider=new SeekBar(this);panel.addView(evSlider);
        evSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int n,boolean user){
                Range<Integer> range=characteristics==null?null:characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                if(user&&range!=null){compensation=n+range.getLower();updatePreviewControls();}
                android.util.Rational step=characteristics==null?null:characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                evLabel.setText(String.format(Locale.US,"Pozlama: %+.2f EV",step==null?0:compensation*step.doubleValue()));
            }
        });
        batch=new Button(this);batch.setEnabled(false);batch.setText("Toplu test · kamera / kontroller / video / AI");batch.setOnClickListener(v->{
            if(batchActive){processingCancelled=true;finishBatch("cancelled");}
            else if(!busy&&!countdown)startBatch();
        });panel.addView(batch);
        Button diagnostic=new Button(this);diagnostic.setText("Cihaz tanılama / test raporu");
        diagnostic.setOnClickListener(v->{if(!busy&&!countdown&&!batchActive)startActivity(new Intent(this,MainActivity.class));});panel.addView(diagnostic);
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
        if(code==2){
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)audio.setChecked(false);
            return;
        }
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
        status.setText("Önizleme hazırlanıyor…");final boolean wantHigh=high;final boolean wantFront=front;
        camera.post(()->{
            close();
            if(token!=generation)return;
            try {
                id=null;characteristics=null;long biggest=-1;
                for(String candidate:manager.getCameraIdList()) {
                    CameraCharacteristics ch=manager.getCameraCharacteristics(candidate);
                    if(!Objects.equals(ch.get(CameraCharacteristics.LENS_FACING),wantFront?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK))continue;
                    StreamConfigurationMap m=ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if(m==null)continue;
                    Size s=largest(m.getOutputSizes(ImageFormat.JPEG),m.getHighResolutionOutputSizes(ImageFormat.JPEG));
                    if(s!=null && (long)s.getWidth()*s.getHeight()>biggest) {
                        biggest=(long)s.getWidth()*s.getHeight();id=candidate;characteristics=ch;
                    }
                }
                if(id==null)throw new IOException("İstenen kamera bulunamadı");
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
                                        previewRequest.set(CaptureRequest.CONTROL_AF_MODE,afMode);applyControls(previewRequest);
                                        s.setRepeatingRequest(previewRequest.build(),metering(token),camera);
                                        ui.post(()->{
                                            if(token!=generation)return;
                                            status.setText(String.format(Locale.US,"%d × %d · %.1f MP · canlı önizleme",outputSize.getWidth(),outputSize.getHeight(),(long)outputSize.getWidth()*outputSize.getHeight()/1e6));
                                            video.setText("Video kaydet");shutter.setText("Fotoğraf çek");shutter.setEnabled(true);enableControls(true);
                                            Float max=characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                                            zoom=Math.max(1f,Math.min(zoom,max==null?1f:max));zoomSlider.setMax((int)(((max==null?1f:max)-1)*10));zoomSlider.setProgress(Math.round((zoom-1)*10));
                                            Range<Integer> ev=characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                                            if(ev!=null){compensation=Math.max(ev.getLower(),Math.min(ev.getUpper(),compensation));evSlider.setMax(ev.getUpper()-ev.getLower());evSlider.setProgress(compensation-ev.getLower());}
                                            if(batchActive)ui.postDelayed(()->{if(token==generation&&batchActive&&!busy)runBatchStage();},1200);
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
    private void startBatch() {
        batchActive=true;batchAi=ai.isChecked();batchAudio=audio.isChecked()&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        batchPlan=new ArrayList<>(Arrays.asList("rear-standard-JPEG","rear-HD-JPEG","front-standard-JPEG","rear-video-5-seconds","rear-zoom-exposure","rear-timer","rear-chroma"));
        if(batchAudio)batchPlan.add("rear-video-audio");if(batchAi)batchPlan.add("rear-standard-AI");
        batchStage=0;batchResults=new JSONArray();
        high=false;front=false;zoom=1;compensation=0;ai.setChecked(false);chroma.setChecked(false);audio.setChecked(false);timer.setSelection(0);
        batch.setText("Toplu testi iptal et");mode.setText("Yüksek çözünürlüğe geç");lens.setText("Ön kamera");enableControls(false);restart();
    }
    private void runBatchStage() {
        if(!batchActive)return;
        status.setText("Toplu test "+(batchStage+1)+" / "+batchPlan.size());
        if(batchPlan.get(batchStage).contains("video"))startVideo();else requestPhoto();
    }
    private void advanceBatch(JSONObject result) {
        if(!batchActive)return;
        String name=batchPlan.get(batchStage);
        try {
            JSONObject item=new JSONObject(result.toString());item.put("stage",batchStage).put("test",name);
            if(!item.has("testPassed")) {
                boolean passed=item.optBoolean("sizeMatched");
                if(name.endsWith("AI"))passed=passed&&item.optBoolean("aiApplied");
                if(name.endsWith("chroma"))passed=passed&&item.optBoolean("chromaDenoiseApplied");
                if(name.endsWith("timer"))passed=passed&&item.optInt("timerRequestedSeconds")==2&&item.optLong("timerWaitMs")>=1900;
                if(name.endsWith("exposure"))passed=passed&&item.optInt("actualExposureCompensationSteps",Integer.MIN_VALUE)==item.optInt("exposureCompensationSteps")
                    &&Math.abs(item.optDouble("actualZoomRatio",0)-item.optDouble("zoomRatio",1))<.1;
                item.put("testPassed",passed);
            }
            batchResults.put(item);
        }catch(Exception ignored){}
        batchStage++;
        if(batchStage>=batchPlan.size()){finishBatch("completed");return;}
        name=batchPlan.get(batchStage);high=name.equals("rear-HD-JPEG");front=name.equals("front-standard-JPEG");
        ai.setChecked(name.endsWith("AI"));chroma.setChecked(name.endsWith("chroma"));audio.setChecked(name.equals("rear-video-audio"));
        timer.setSelection(name.endsWith("timer")?1:0);zoom=name.endsWith("exposure")?2f:1f;compensation=0;
        if(name.endsWith("exposure")) {
            android.util.Rational step=characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            if(step!=null&&step.doubleValue()>0)compensation=(int)Math.round(1/step.doubleValue());
        }
        mode.setText(high?"Standart çözünürlüğe geç":"Yüksek çözünürlüğe geç");lens.setText(front?"Arka kamera":"Ön kamera");restart();
    }
    private void finishBatch(String state) {
        if(!batchActive)return;
        batchActive=false;batch.setText("Toplu test · kamera / kontroller / video / AI");
        ai.setChecked(batchAi);audio.setChecked(batchAudio);enableControls(!busy);final JSONArray outcomes=batchResults;final int planned=batchPlan.size();
        final String name="MG-batch-"+System.currentTimeMillis()+".json";
        camera.post(()->{
            try {
                int passed=0,failed=0;for(int i=0;i<outcomes.length();i++){if(outcomes.getJSONObject(i).optBoolean("testPassed"))passed++;else failed++;}
                JSONObject summary=new JSONObject().put("appVersion","0.6-prototype").put("state",state).put("passed",passed).put("failed",failed)
                    .put("planned",planned).put("executed",outcomes.length()).put("results",outcomes);
                save(MediaStore.Downloads.EXTERNAL_CONTENT_URI,name,"application/json","Download/MGKamera",summary.toString(2).getBytes("UTF-8"));
                final int p=passed,f=failed;ui.post(()->{if(active){status.setText("Toplu test "+state+": "+p+" geçti, "+f+" başarısız · "+name);Toast.makeText(this,"Toplu test: "+p+" geçti, "+f+" başarısız; JSON kaydedildi",1).show();}});
            }catch(Exception e){ui.post(()->{if(active)status.setText("Toplu test raporu kaydedilemedi: "+e);});}
        });
        if("completed".equals(state)&&batchPlan.get(batchPlan.size()-1).contains("video")&&active)restart();
    }
    private void enableControls(boolean enabled) {
        batch.setEnabled(batchActive||enabled);
        enabled=enabled&&!batchActive;
        mode.setEnabled(enabled);lens.setEnabled(enabled);video.setEnabled(enabled);audio.setEnabled(enabled);
        hq.setEnabled(enabled);chroma.setEnabled(enabled);ai.setEnabled(enabled);timer.setEnabled(enabled);zoomSlider.setEnabled(enabled);evSlider.setEnabled(enabled);
    }
    private void applyControls(CaptureRequest.Builder b) {
        b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,compensation);
        Range<Float> ratio=characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        if(ratio!=null)b.set(CaptureRequest.CONTROL_ZOOM_RATIO,Math.max(ratio.getLower(),Math.min(ratio.getUpper(),zoom)));
        else {
            Rect active=characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if(active!=null){int w=Math.max(2,Math.round(active.width()/zoom)),h=Math.max(2,Math.round(active.height()/zoom));
                int x=active.centerX()-w/2,y=active.centerY()-h/2;b.set(CaptureRequest.SCALER_CROP_REGION,new Rect(x,y,x+w,y+h));}
        }
    }
    private void updatePreviewControls() {
        int token=generation;
        camera.post(()->{if(token!=generation||session==null||previewRequest==null)return;
            try {applyControls(previewRequest);session.setRepeatingRequest(previewRequest.build(),metering(token),camera);}
            catch(Exception e){error(token,"Çekim ayarı",e);}});
    }
    private void requestPhoto() {
        if(busy||countdown||session==null||recording)return;
        int seconds=new int[]{0,2,5,10}[timer.getSelectedItemPosition()];
        requestedTimer=seconds;timerStarted=SystemClock.elapsedRealtime();
        if(seconds==0){shoot();return;}
        countdown=true;shutter.setEnabled(false);enableControls(false);int token=generation;
        countdownTask=new Runnable(){int remaining=seconds;public void run(){
            if(token!=generation||!active){countdown=false;return;}
            if(remaining--<=0){countdown=false;shoot();return;}
            status.setText("Çekim için "+(remaining+1)+" saniye…");ui.postDelayed(this,1000);
        }};ui.post(countdownTask);
    }
    private void startVideo() {
        if(session==null||device==null)return;
        boolean sound=audio.isChecked();
        if(sound&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){audio.setChecked(false);status.setText("Mikrofon izni yok; sessiz video seçebilirsin.");return;}
        busy=true;enableControls(false);shutter.setEnabled(false);status.setText("Video hazırlanıyor…");int token=generation;
        camera.post(()->{
            if(token!=generation)return;
            try {
                videoSize=null;
                StreamConfigurationMap map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                for(Size s:map.getOutputSizes(MediaRecorder.class))if(s.getWidth()<=1920&&s.getHeight()<=1080&&Math.abs((double)s.getWidth()/s.getHeight()-16.0/9)<0.03)
                    videoSize=largest(new Size[]{s},videoSize==null?null:new Size[]{videoSize});
                if(videoSize==null)throw new IOException("16:9 video boyutu sunulmuyor");
                ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,"MG-video-"+System.currentTimeMillis()+".mp4");
                values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/MGKamera");values.put(MediaStore.Video.Media.IS_PENDING,1);
                videoUri=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
                if(videoUri==null)throw new IOException("Video dosyası oluşturulamadı");
                videoFd=getContentResolver().openFileDescriptor(videoUri,"rw");if(videoFd==null)throw new IOException("Video dosyası açılamadı");
                recorder=new MediaRecorder(this);if(sound)recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setOutputFile(videoFd.getFileDescriptor());recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                recorder.setVideoSize(videoSize.getWidth(),videoSize.getHeight());recorder.setVideoFrameRate(30);
                recorder.setVideoEncodingBitRate(videoSize.getWidth()>=1920?20000000:10000000);
                if(sound){recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioSamplingRate(48000);recorder.setAudioEncodingBitRate(128000);}
                int rotation=getWindowManager().getDefaultDisplay().getRotation();int degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;
                int sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                recorder.setOrientationHint(CameraMath.jpegOrientation(sensor,degrees,front));recorder.prepare();
                videoInfo=new JSONObject().put("appVersion","0.6-prototype").put("cameraId",id).put("requestedWidth",videoSize.getWidth()).put("requestedHeight",videoSize.getHeight())
                    .put("requestedFps",30).put("audio",sound).put("zoomRatio",zoom).put("exposureCompensationSteps",compensation);
                if(session!=null){session.close();session=null;}if(reader!=null){reader.close();reader=null;}
                previewSize=videoSize;preview.getSurfaceTexture().setDefaultBufferSize(videoSize.getWidth(),videoSize.getHeight());ui.post(()->{if(token==generation)transform();});
                device.createCaptureSession(Arrays.asList(previewSurface,recorder.getSurface()),new CameraCaptureSession.StateCallback(){
                    @Override public void onConfigured(CameraCaptureSession s){
                        if(token!=generation){s.close();return;}session=s;
                        try {
                            previewRequest=device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);previewRequest.addTarget(previewSurface);previewRequest.addTarget(recorder.getSurface());
                            int[] modes=characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                            previewRequest.set(CaptureRequest.CONTROL_AF_MODE,contains(modes,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)?CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO:afMode);
                            Range<Integer>[] ranges=characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                            if(ranges!=null)for(Range<Integer> range:ranges)if(range.getLower()==30&&range.getUpper()==30){previewRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,range);break;}
                            applyControls(previewRequest);s.setRepeatingRequest(previewRequest.build(),null,camera);recorder.start();recording=true;videoStarted=SystemClock.elapsedRealtime();
                            ui.post(()->{if(token==generation){status.setText("Video kaydı · "+videoSize+" · durdurmak için düğmeye bas");video.setText("Videoyu durdur");video.setEnabled(!batchActive);}
                                if(batchActive&&token==generation)camera.postDelayed(()->{if(recording&&token==generation)stopVideo(true);},5000);});
                        }catch(Exception e){error(token,"Video başlatılamadı",e);}
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession s){s.close();error(token,"Video oturumu reddedildi",null);}
                },camera);
            }catch(Exception e){error(token,"Video",e);}
        });
    }
    private void stopVideo(boolean restartAfter) {
        if(recorder==null)return;
        boolean complete=false;
        try {if(session!=null){session.stopRepeating();session.abortCaptures();}}catch(Exception ignored){}
        try {
            if(recording){recorder.stop();complete=true;}
        }catch(Exception e){if(videoInfo!=null)try{videoInfo.put("error",e.toString());}catch(Exception ignored){}}
        finally {
            recording=false;try{recorder.reset();recorder.release();}catch(Exception ignored){}recorder=null;
            try{if(videoFd!=null)videoFd.close();}catch(Exception ignored){}videoFd=null;
        }
        try {
            if(videoUri!=null) {
                if(complete) {
                    ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(videoUri,values,null,null);
                    videoInfo.put("status","saved").put("recordingMs",SystemClock.elapsedRealtime()-videoStarted).put("uri",videoUri.toString());
                    MediaMetadataRetriever metadata=new MediaMetadataRetriever();
                    try{metadata.setDataSource(this,videoUri);videoInfo.put("videoWidth",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                        .put("videoHeight",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)).put("durationMs",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                        .put("rotation",metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                        long duration=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                        Bitmap frame=metadata.getScaledFrameAtTime(duration*500,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,320,180);
                        videoInfo.put("decodedFrame",frame!=null);if(frame!=null)frame.recycle();
                        int actualW=Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                        int actualH=Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                        videoInfo.put("testPassed",duration>0 && videoInfo.optBoolean("decodedFrame") &&
                            ((actualW==videoSize.getWidth()&&actualH==videoSize.getHeight())||(actualH==videoSize.getWidth()&&actualW==videoSize.getHeight())));
                    }finally{metadata.release();}
                    save(MediaStore.Downloads.EXTERNAL_CONTENT_URI,"MG-video-report-"+System.currentTimeMillis()+".json","application/json","Download/MGKamera",videoInfo.toString(2).getBytes("UTF-8"));
                }else getContentResolver().delete(videoUri,null,null);
            }
        }catch(Exception e){ui.post(()->{if(active)Toast.makeText(this,"Video raporu: "+e.getMessage(),1).show();});}
        videoUri=null;busy=false;
        if(restartAfter){final boolean saved=complete;final JSONObject result=videoInfo;
            ui.post(()->{if(active){video.setText("Video kaydet");enableControls(true);
                if(batchActive){try{result.put("testPassed",saved&&result.optBoolean("testPassed"));}catch(Exception ignored){}advanceBatch(result);}else restart();}});
        }
    }
    private void shoot() {
        if(busy||session==null||device==null)return;
        busy=true;enableControls(false);selectedAi=ai.isChecked();processingCancelled=false;selectedHq=hq.isChecked();selectedChroma=chroma.isChecked();chroma.setEnabled(false);shutter.setEnabled(false);mode.setEnabled(false);hq.setEnabled(false);
        status.setText("Odak ve pozlama bekleniyor…");
        int token=generation;
        int rotation=getWindowManager().getDefaultDisplay().getRotation();
        int degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;
        Integer sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        jpegOrientation=CameraMath.jpegOrientation(sensor==null?0:sensor,degrees,front);
        camera.post(()->{
            if(token!=generation)return;
            waiting=true;fired=false;finalAf=null;finalAe=null;finalAwb=null;
            captureInfo=new JSONObject();captureResultReceived=false;pendingJpeg=null;
            try {
                captureInfo.put("appVersion","0.6-prototype").put("cameraId",id)
                    .put("requestedWidth",outputSize.getWidth()).put("requestedHeight",outputSize.getHeight())
                    .put("jpegOrientation",jpegOrientation).put("hqRequested",selectedHq)
                     .put("vendorControlsApplied",false).put("chromaDenoiseRequested",selectedChroma)
                     .put("zoomRatio",zoom).put("exposureCompensationSteps",compensation).put("frontCamera",front).put("aiRequested",selectedAi)
                    .put("timerRequestedSeconds",requestedTimer).put("timerWaitMs",SystemClock.elapsedRealtime()-timerStarted);
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
            shot.set(CaptureRequest.CONTROL_AF_MODE,afMode);applyControls(shot);shot.set(CaptureRequest.JPEG_QUALITY,(byte)100);
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
                            .put("focusDistance",r.get(CaptureResult.LENS_FOCUS_DISTANCE))
                            .put("actualExposureCompensationSteps",r.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION));
                        Float actualZoom=r.get(CaptureResult.CONTROL_ZOOM_RATIO);
                        if(actualZoom!=null)captureInfo.put("actualZoomRatio",actualZoom);
                        else {Rect crop=r.get(CaptureResult.SCALER_CROP_REGION),area=characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                            if(crop!=null&&area!=null&&crop.width()>0)captureInfo.put("actualZoomRatio",(double)area.width()/crop.width());}
                    }catch(Exception ignored){}
                    captureResultReceived=true;flushPending(token);
                }
                @Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest req,CaptureFailure f){error(token,"Çekim başarısız: "+f.getReason(),null);}
            },camera);
        }catch(Exception e){error(token,"Fotoğraf",e);}
    }
    private void receive(ImageReader source,int token) {
        if(token!=generation)return;
        try(Image image=source.acquireNextImage()) {
            if(image==null)return;
            ByteBuffer buffer=image.getPlanes()[0].getBuffer();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);pendingJpeg=bytes;
            if(captureResultReceived)flushPending(token);else camera.postDelayed(()->flushPending(token),1000);
        }catch(Exception e){error(token,"JPEG alma",e);}
    }
    private void flushPending(int token) {
        if(token!=generation||pendingJpeg==null)return;
        final byte[] bytes=pendingJpeg;pendingJpeg=null;
        try{captureInfo.put("captureMetadataReceived",captureResultReceived);}catch(Exception ignored){}
        camera.post(()->processJpeg(bytes,token));
    }
    private void processJpeg(byte[] bytes,int token) {
        if(token!=generation)return;
        try {
            BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,dimensions);
            if(dimensions.outWidth<=0||dimensions.outHeight<=0)throw new IOException("JPEG başlığı okunamadı");
            String base="MG-camera-"+System.currentTimeMillis();
            save(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,base+".jpg","image/jpeg","Pictures/MGKamera",bytes);
            captureInfo.put("jpegWidth",dimensions.outWidth).put("jpegHeight",dimensions.outHeight).put("bytes",bytes.length)
                .put("sizeMatched",(dimensions.outWidth==outputSize.getWidth()&&dimensions.outHeight==outputSize.getHeight()) || (dimensions.outHeight==outputSize.getWidth()&&dimensions.outWidth==outputSize.getHeight()));
            if(captureTimeout!=null)camera.removeCallbacks(captureTimeout);
            captureInfo.put("originalOutput",base+".jpg");
            if(selectedAi) {
                processing=true;ui.post(()->{if(token==generation){shutter.setText("AI işlemini iptal et");shutter.setEnabled(true);}});
                processAi(bytes,base,token);
                processing=false;ui.post(()->{if(token==generation){shutter.setText("Fotoğraf çek");shutter.setEnabled(false);}});
            }
            if(selectedChroma&&!processingCancelled) {
                ui.post(()->{if(token==generation)status.setText("Orijinal kaydedildi · renkli gürültü azaltılıyor…");});
                processChroma(bytes,base,token);
            }
            save(MediaStore.Downloads.EXTERNAL_CONTENT_URI,base+".json","application/json","Download/MGKamera",captureInfo.toString(2).getBytes("UTF-8"));
            resetFocus();
            ui.post(()->{
                if(token!=generation)return;
                busy=false;shutter.setEnabled(true);enableControls(true);
                status.setText("Kaydedildi: "+dimensions.outWidth+" × "+dimensions.outHeight+" · "+
                    ((captureInfo.optBoolean("chromaDenoiseApplied")||captureInfo.optBoolean("aiApplied"))?"orijinal + işlenmiş JPEG":"orijinal JPEG")+" · Pictures/MGKamera");
                if(batchActive)advanceBatch(captureInfo);
            });
        }catch(Exception e){error(token,"Kaydetme",e);}
    }
    private void processAi(byte[] bytes,String base,int token)throws Exception {
        Runtime memory=Runtime.getRuntime();
        long needed=(long)outputSize.getWidth()*outputSize.getHeight()*4+160L*1024*1024;
        long available=memory.maxMemory()-(memory.totalMemory()-memory.freeMemory());
        captureInfo.put("aiModel","KAIR DnCNN color blind").put("aiProvider","ONNX Runtime CPU · 2 threads").put("aiOriginalBlend",0.35);
        if(available<needed){captureInfo.put("aiApplied",false).put("aiSkipReason","insufficient_heap");return;}
        Bitmap result=null;long started=SystemClock.elapsedRealtime();
        try {
            result=AiDenoise.apply(this,bytes,new AiDenoise.Progress(){
                public boolean cancelled(){return token!=generation||processingCancelled;}
                public void update(int done,int total){
                    if(done==1||done%4==0||done==total)ui.post(()->{if(token==generation)status.setText("AI: "+done+" / "+total+" parça · orijinal kaydedildi");});
                }
            });
            if(token!=generation||processingCancelled)throw new InterruptedIOException("AI iptal edildi");
            saveProcessed(result,bytes,base+"-ai.jpg");
            captureInfo.put("aiApplied",true).put("aiOutput",base+"-ai.jpg").put("aiProcessingMs",SystemClock.elapsedRealtime()-started)
                .put("aiWidth",result.getWidth()).put("aiHeight",result.getHeight());
        }catch(OutOfMemoryError e){captureInfo.put("aiApplied",false).put("aiSkipReason","allocation_failed");}
        catch(LinkageError e){captureInfo.put("aiApplied",false).put("aiSkipReason","runtime_load_failed: "+e);}
        catch(Exception e){captureInfo.put("aiApplied",false).put("aiSkipReason",e.toString());}
        finally{if(result!=null)result.recycle();}
    }
    private void saveProcessed(Bitmap pixels,byte[] original,String name)throws Exception {
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MGKamera");values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("İşlenmiş fotoğraf oluşturulamadı");
        try {
            try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null||!pixels.compress(Bitmap.CompressFormat.JPEG,98,out))throw new IOException("JPEG kodlanamadı");}
            try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"rw")){
                if(fd==null)throw new IOException("EXIF açılamadı");
                android.media.ExifInterface source=new android.media.ExifInterface(new ByteArrayInputStream(original));
                android.media.ExifInterface target=new android.media.ExifInterface(fd.getFileDescriptor());
                for(String tag:new String[]{"Make","Model","DateTime","DateTimeOriginal","ExposureTime","FNumber","ISOSpeedRatings","FocalLength"}){
                    String value=source.getAttribute(tag);if(value!=null)target.setAttribute(tag,value);
                }target.setAttribute("Orientation","1");target.saveAttributes();
            }
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);
        }catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}
    }
    private void processChroma(byte[] bytes,String base,int token) throws Exception {
        Runtime memory=Runtime.getRuntime();
        long needed=(long)outputSize.getWidth()*outputSize.getHeight()*4+96L*1024*1024;
        long available=memory.maxMemory()-(memory.totalMemory()-memory.freeMemory());
        captureInfo.put("heapLimitBytes",memory.maxMemory());
        if(available<needed) {
            captureInfo.put("chromaDenoiseApplied",false).put("chromaSkipReason","insufficient_heap");return;
        }
        Bitmap bitmap=null;Uri uri=null;long started=SystemClock.elapsedRealtime();
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();options.inMutable=true;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(bitmap==null)throw new IOException("İşleme için JPEG açılamadı");
            final Bitmap pixels=bitmap;
            ChromaDenoise.apply(new ChromaDenoise.Rows() {
                public int width(){return pixels.getWidth();}public int height(){return pixels.getHeight();}
                public void read(int y,int[] out){if(token!=generation)throw new IllegalStateException("activity_interrupted");pixels.getPixels(out,0,pixels.getWidth(),0,y,pixels.getWidth(),1);}
                public void write(int y,int[] in){pixels.setPixels(in,0,pixels.getWidth(),0,y,pixels.getWidth(),1);}
            });
            if(token!=generation) {captureInfo.put("chromaDenoiseApplied",false).put("chromaSkipReason","activity_interrupted");return;}
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,base+"-chroma.jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/MGKamera");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IOException("İşlenmiş dosya oluşturulamadı");
            try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null||!pixels.compress(Bitmap.CompressFormat.JPEG,98,out))throw new IOException("JPEG kodlama başarısız");
            }
            try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"rw")) {
                if(fd==null)throw new IOException("EXIF dosyası açılamadı");
                android.media.ExifInterface source=new android.media.ExifInterface(new ByteArrayInputStream(bytes));
                android.media.ExifInterface target=new android.media.ExifInterface(fd.getFileDescriptor());
                for(String tag:new String[]{"Make","Model","DateTime","DateTimeOriginal","ExposureTime","FNumber","ISOSpeedRatings","FocalLength"}) {
                    String value=source.getAttribute(tag);if(value!=null)target.setAttribute(tag,value);
                }
                target.setAttribute("Orientation","1");target.saveAttributes();
            }
            BitmapFactory.Options verify=new BitmapFactory.Options();verify.inJustDecodeBounds=true;
            try(InputStream check=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(check,null,verify);}
            if(verify.outWidth!=pixels.getWidth()||verify.outHeight!=pixels.getHeight())throw new IOException("İşlenmiş JPEG boyutu eşleşmedi");
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);
            captureInfo.put("chromaDenoiseApplied",true).put("chromaOutput",base+"-chroma.jpg")
                .put("processingMs",SystemClock.elapsedRealtime()-started)
                .put("processedWidth",pixels.getWidth()).put("processedHeight",pixels.getHeight());
        }catch(OutOfMemoryError e) {
            if(uri!=null)getContentResolver().delete(uri,null,null);
            captureInfo.put("chromaDenoiseApplied",false).put("chromaSkipReason","allocation_failed");
        }catch(Exception e) {
            if(uri!=null)getContentResolver().delete(uri,null,null);
            captureInfo.put("chromaDenoiseApplied",false).put("chromaSkipReason",e.toString());
        }finally {if(bitmap!=null)bitmap.recycle();}
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
            busy=false;shutter.setEnabled(false);enableControls(true);
            status.setText(message+(e==null?"":" · "+e.getMessage())+". Modu değiştirerek yeniden deneyebilirsin.");
            if(batchActive){JSONObject failed=new JSONObject();try{failed.put("testPassed",false).put("error",message+(e==null?"":" · "+e));}catch(Exception ignored){}advanceBatch(failed);}
        });
        camera.post(()->{if(token==generation){resetFocus();close();}});
    }
    private void transform() {
        if(previewSize==null||characteristics==null||preview.getWidth()==0)return;
        int rotation=getWindowManager().getDefaultDisplay().getRotation();
        int degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;
        Integer sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int relative=CameraMath.jpegOrientation(sensor==null?0:sensor,degrees,front);
        float vw=preview.getWidth(),vh=preview.getHeight();float bw=previewSize.getWidth(),bh=previewSize.getHeight();
        Matrix m=new Matrix();
        // Undo TextureView's independent x/y stretch, rotate camera buffer, then fit the view.
        m.setScale(bw/vw,bh/vh);m.postTranslate(-bw/2,-bh/2);m.postRotate(relative);
        float fit=(relative%180==0)?Math.min(vw/bw,vh/bh):Math.min(vw/bh,vh/bw);
        m.postScale(fit,fit);m.postTranslate(vw/2,vh/2);if(front)m.postScale(-1,1,vw/2,vh/2);preview.setTransform(m);
    }
    private void close() {
        pendingJpeg=null;captureResultReceived=false;
        if(recorder!=null)stopVideo(false);
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
    @Override protected void onPause(){active=false;if(batchActive){processingCancelled=true;finishBatch("interrupted");}generation++;busy=false;shutter.setEnabled(false);enableControls(true);camera.post(this::close);super.onPause();}
    @Override protected void onDestroy(){camera.post(()->{close();thread.quitSafely();});super.onDestroy();}
}
