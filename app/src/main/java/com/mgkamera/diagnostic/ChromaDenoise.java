package com.mgkamera.diagnostic;

/** Bounded-row chroma smoothing. Preserves center luma; attenuates near luma edges. */
public final class ChromaDenoise {
    public interface Rows {
        int width(); int height();
        void read(int y,int[] pixels);
        void write(int y,int[] pixels);
    }
    private ChromaDenoise() {}
    private static int y(int p) {return (77*((p>>>16)&255)+150*((p>>>8)&255)+29*(p&255)+128)>>8;}
    private static int clamp(int v) {return Math.max(0,Math.min(255,v));}
    private static void accumulate(int[] row,int sign,int[] sy,int[] sy2,int[] sr,int[] sb) {
        for(int x=0;x<row.length;x++) {
            int p=row[x],l=y(p);sy[x]+=sign*l;sy2[x]+=sign*l*l;
            sr[x]+=sign*(((p>>>16)&255)-l);sb[x]+=sign*((p&255)-l);
        }
    }
    public static void apply(Rows image) {
        int w=image.width(),h=image.height();if(w<=0||h<=0)throw new IllegalArgumentException("Empty image");
        int[][] ring=new int[5][w];int[] sy=new int[w],sy2=new int[w],sr=new int[w],sb=new int[w],out=new int[w];
        for(int j=0;j<=Math.min(2,h-1);j++){image.read(j,ring[j%5]);accumulate(ring[j%5],1,sy,sy2,sr,sb);}
        for(int row=0;row<h;row++) {
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Processing interrupted");
            if(row>0) {
                int remove=row-3;if(remove>=0)accumulate(ring[remove%5],-1,sy,sy2,sr,sb);
                int add=row+2;if(add<h){image.read(add,ring[add%5]);accumulate(ring[add%5],1,sy,sy2,sr,sb);}
            }
            int vertical=Math.min(h-1,row+2)-Math.max(0,row-2)+1;
            int sumY=0,sumY2=0,sumR=0,sumB=0;
            for(int j=0;j<=Math.min(2,w-1);j++){sumY+=sy[j];sumY2+=sy2[j];sumR+=sr[j];sumB+=sb[j];}
            for(int x=0;x<w;x++) {
                if(x>0) {
                    int remove=x-3;if(remove>=0){sumY-=sy[remove];sumY2-=sy2[remove];sumR-=sr[remove];sumB-=sb[remove];}
                    int add=x+2;if(add<w){sumY+=sy[add];sumY2+=sy2[add];sumR+=sr[add];sumB+=sb[add];}
                }
                int count=vertical*(Math.min(w-1,x+2)-Math.max(0,x-2)+1);
                int p=ring[row%5][x],l=y(p),r=(p>>>16)&255,b=p&255;
                double mean=(double)sumY/count;
                double variance=Math.max(0,(double)sumY2/count-mean*mean);
                double blend=0.85/(1+variance/160.0);
                int rr=clamp((int)Math.round(r+blend*(l+(double)sumR/count-r)));
                int bb=clamp((int)Math.round(b+blend*(l+(double)sumB/count-b)));
                int gg=clamp((int)Math.round((256.0*l-77.0*rr-29.0*bb)/150.0));
                out[x]=(p&0xff000000)|(rr<<16)|(gg<<8)|bb;
            }
            image.write(row,out);
        }
    }
}
