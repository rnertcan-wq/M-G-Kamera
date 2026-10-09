import com.mgkamera.diagnostic.ChromaDenoise;
import java.util.*;
public final class ChromaDenoiseTest {
    private static final class Image implements ChromaDenoise.Rows {
        final int w,h; final int[][] rows;
        Image(int w,int h){this.w=w;this.h=h;rows=new int[h][w];}
        public int width(){return w;}public int height(){return h;}
        public void read(int y,int[] out){System.arraycopy(rows[y],0,out,0,w);}
        public void write(int y,int[] in){System.arraycopy(in,0,rows[y],0,w);}
    }
    private static int luma(int p){return (77*((p>>>16)&255)+150*((p>>>8)&255)+29*(p&255)+128)>>8;}
    private static double chroma(Image im){double sum=0;for(int[] row:im.rows)for(int p:row){double r=((p>>>16)&255)-luma(p),b=(p&255)-luma(p);sum+=r*r+b*b;}return sum/(im.w*im.h);}
    public static void main(String[] args) {
        Image im=new Image(79,53);Random random=new Random(17);int[][] before=new int[53][79];
        for(int y=0;y<53;y++)for(int x=0;x<79;x++) {
            int r=100+random.nextInt(41)-20,b=100+random.nextInt(41)-20;
            int g=(int)Math.round((25600.0-77*r-29*b)/150);
            im.rows[y][x]=0xff000000|(r<<16)|(g<<8)|b;before[y][x]=im.rows[y][x];
        }
        double original=chroma(im);ChromaDenoise.apply(im);double result=chroma(im);
        if(result>=original*0.3)throw new AssertionError("Chroma not reduced: "+result/original);
        for(int y=0;y<53;y++)for(int x=0;x<79;x++)if(Math.abs(luma(im.rows[y][x])-luma(before[y][x]))>1)throw new AssertionError("Luma changed");
        Image edge=new Image(31,9);
        for(int[] row:edge.rows)for(int x=0;x<31;x++) {int v=x<15?30:220;row[x]=0xff000000|(v<<16)|(v<<8)|v;}
        ChromaDenoise.apply(edge);
        if(luma(edge.rows[4][14])!=30||luma(edge.rows[4][15])!=220)throw new AssertionError("Luma edge blurred");
        Image one=new Image(1,1);one.rows[0][0]=0xff808080;ChromaDenoise.apply(one);
        if(one.rows[0][0]!=0xff808080)throw new AssertionError("Single pixel changed");
        System.out.println("Chroma tests passed: residual="+result/original+", luma error <= 1, luma edge retained, small image handled");
    }
}
