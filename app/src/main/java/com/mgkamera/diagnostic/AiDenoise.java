package com.mgkamera.diagnostic;

import android.content.Context;
import android.graphics.*;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.*;

/** Offline, native-size, overlapping-tile DnCNN inference. Never scales the JPEG up. */
public final class AiDenoise {
    public interface Progress {boolean cancelled();void update(int done,int total);}
    private static final String HASH="11fed3af8c0de2e7e6ac90d946220191c75707467f6a05849be2f01429414aaf";
    private AiDenoise(){}
    private static File modelFile(Context context)throws Exception {
        File file=new File(context.getFilesDir(),"dncnn-color-blind.onnx");
        if(!file.exists()) {
            File temp=new File(context.getFilesDir(),"dncnn-color-blind.tmp");
            try(InputStream in=context.getAssets().open("dncnn-color-blind.onnx");OutputStream out=new FileOutputStream(temp)){
                byte[] block=new byte[32768];int n;while((n=in.read(block))!=-1)out.write(block,0,n);
            }
            if(!temp.renameTo(file))throw new IOException("Model dosyası kaydedilemedi");
        }
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] block=new byte[32768];int n;while((n=in.read(block))!=-1)digest.update(block,0,n);}
        StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.US,"%02x",b&255));
        if(!HASH.equals(actual.toString()))throw new IOException("AI model bütünlüğü doğrulanamadı");
        return file;
    }
    public static Bitmap apply(Context context,byte[] jpeg,Progress progress)throws Exception {
        File model=modelFile(context);OrtEnvironment environment=OrtEnvironment.getEnvironment();
        BitmapRegionDecoder decoder=BitmapRegionDecoder.newInstance(jpeg,0,jpeg.length,false);
        if(decoder==null)throw new IOException("AI JPEG açılamadı");
        Bitmap output=null;
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            try(OrtSession session=environment.createSession(model.getAbsolutePath(),options)) {
                int width=decoder.getWidth(),height=decoder.getHeight(),tile=256,halo=20;
                output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                int total=((width+tile-1)/tile)*((height+tile-1)/tile),done=0;
                FloatBuffer inputBuffer=ByteBuffer.allocateDirect(3*(tile+2*halo)*(tile+2*halo)*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                BitmapFactory.Options decode=new BitmapFactory.Options();decode.inPreferredConfig=Bitmap.Config.ARGB_8888;
                for(int y=0;y<height;y+=tile)for(int x=0;x<width;x+=tile) {
                    if(progress.cancelled())throw new InterruptedIOException("AI işlemi iptal edildi");
                    int cw=Math.min(tile,width-x),ch=Math.min(tile,height-y);
                    Rect region=new Rect(Math.max(0,x-halo),Math.max(0,y-halo),Math.min(width,x+cw+halo),Math.min(height,y+ch+halo));
                    Bitmap patch=decoder.decodeRegion(region,decode);if(patch==null)throw new IOException("AI parçası açılamadı");
                    int pw=patch.getWidth(),ph=patch.getHeight(),plane=pw*ph;
                    int[] pixels=new int[plane];patch.getPixels(pixels,0,pw,0,0,pw,ph);patch.recycle();
                    inputBuffer.clear();inputBuffer.limit(3*plane);
                    for(int i=0;i<plane;i++){inputBuffer.put(i,((pixels[i]>>>16)&255)/255f);inputBuffer.put(plane+i,((pixels[i]>>>8)&255)/255f);inputBuffer.put(2*plane+i,(pixels[i]&255)/255f);}
                    try(OnnxTensor tensor=OnnxTensor.createTensor(environment,inputBuffer,new long[]{1,3,ph,pw});
                        OrtSession.Result result=session.run(Collections.singletonMap("image",tensor))) {
                        float[][][][] prediction=(float[][][][])result.get(0).getValue();
                        int[] center=new int[cw*ch];int ox=x-region.left,oy=y-region.top;
                        for(int ry=0;ry<ch;ry++)for(int rx=0;rx<cw;rx++) {
                            int at=(oy+ry)*pw+ox+rx,p=pixels[at];
                            // Conservative 65% neural output blend to retain original texture.
                            int r=channel(prediction[0][0][oy+ry][ox+rx],(p>>>16)&255);
                            int g=channel(prediction[0][1][oy+ry][ox+rx],(p>>>8)&255);
                            int b=channel(prediction[0][2][oy+ry][ox+rx],p&255);
                            center[ry*cw+rx]=0xff000000|(r<<16)|(g<<8)|b;
                        }
                        output.setPixels(center,0,cw,x,y,cw,ch);
                    }
                    progress.update(++done,total);
                }
                Bitmap completed=output;output=null;return completed;
            }
        }finally{decoder.recycle();if(output!=null)output.recycle();}
    }
    private static int channel(float prediction,int original)throws IOException {
        if(Float.isNaN(prediction)||Float.isInfinite(prediction))throw new IOException("AI geçersiz çıktı üretti");
        return Math.max(0,Math.min(255,Math.round(.35f*original+.65f*prediction*255)));
    }
}
