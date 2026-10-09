import com.mgkamera.diagnostic.CameraMath;
import java.util.Arrays;

public final class CameraMathTest {
    private static int checks;
    private static void expected(int[] actual,int... wanted) {
        checks++;
        if(!Arrays.equals(actual,wanted))throw new AssertionError(Arrays.toString(actual)+" != "+Arrays.toString(wanted));
    }
    public static void main(String[] args) {
        // CK7n 0.2 report: regular map tops out at a square ~12MP; high-resolution map exposes 16MP.
        expected(CameraMath.largest(new int[][]{{3840,2160},{3456,3456},{3264,2448}},
            new int[][]{{4608,3456},{4608,2592},{4480,2016}}),4608,3456);
        // The real OEM list uses HAL BLOB 33, not public JPEG 256; 0 marks output.
        expected(CameraMath.vendorJpeg(new int[]{33,9216,6912,0,33,8960,4032,0,33,9216,5184,0,33,6912,6912,0}),9216,6912);
        expected(CameraMath.vendorJpeg(new int[]{35,12000,9000,0,33,12000,9000,1,256,4608,3456,0}),4608,3456);
        expected(CameraMath.vendorJpeg(new int[]{33,0,0,0,33,100}), (int[])null);
        expected(CameraMath.largest(null,null),(int[])null);
        expected(CameraMath.largest(new int[][]{{-1,3000},{3000,2000}},null),3000,2000);
        int[] orientations={90,0,270,180};
        for(int i=0;i<4;i++) {
            checks++;
            if(CameraMath.jpegOrientation(90,i*90)!=orientations[i])throw new AssertionError("Orientation "+i);
        }
        int[] frontOrientations={270,0,90,180};
        for(int i=0;i<4;i++) {
            checks++;
            if(CameraMath.jpegOrientation(270,i*90,true)!=frontOrientations[i])throw new AssertionError("Front orientation "+i);
        }
        System.out.println(checks+" regression checks passed");
    }
}
