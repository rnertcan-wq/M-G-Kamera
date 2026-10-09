package com.mgkamera.diagnostic;

/** Camera metadata selection without Android dependencies, for regression checks. */
public final class CameraMath {
    private CameraMath() {}
    public static int[] largest(int[][] normal,int[][] high) {
        int[] best=null;
        for(int[][] list:new int[][][]{normal,high})if(list!=null)for(int[] s:list) {
            if(s==null||s.length<2||s[0]<=0||s[1]<=0)continue;
            if(best==null || (long)s[0]*s[1]>(long)best[0]*best[1])best=new int[]{s[0],s[1]};
        }
        return best;
    }
    public static int[] vendorJpeg(int[] configs) {
        if(configs==null)return null;
        int[] best=null;
        for(int i=0;i+3<configs.length;i+=4) {
            // HAL_PIXEL_FORMAT_BLOB=33; public ImageFormat.JPEG=256.
            if((configs[i]==33||configs[i]==256)&&configs[i+3]==0)
                best=largest(best==null?null:new int[][]{best},new int[][]{{configs[i+1],configs[i+2]}});
        }
        return best;
    }
    public static int jpegOrientation(int sensorDegrees,int displayDegrees) {
        return jpegOrientation(sensorDegrees,displayDegrees,false);
    }
    public static int jpegOrientation(int sensorDegrees,int displayDegrees,boolean front) {
        return ((sensorDegrees+(front?displayDegrees:-displayDegrees))%360+360)%360;
    }
}
