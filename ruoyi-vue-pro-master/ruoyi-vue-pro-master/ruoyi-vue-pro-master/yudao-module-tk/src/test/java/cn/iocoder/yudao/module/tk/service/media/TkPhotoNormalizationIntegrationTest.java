package cn.iocoder.yudao.module.tk.service.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Run with TK_PHOTO_TEST_EXECUTABLE=/usr/bin/convert on the deployment decoder environment. */
@EnabledIfEnvironmentVariable(named="TK_PHOTO_TEST_EXECUTABLE",matches=".+")
class TkPhotoNormalizationIntegrationTest {
    @TempDir Path dir;
    @Test void actualCodecConvertsAllSixFormatsAndFirstDisplayedGifCanvas() throws Exception {
        String binary=System.getenv("TK_PHOTO_TEST_EXECUTABLE");
        TkPhotoNormalizationService codec=new TkPhotoNormalizationService();ReflectionTestUtils.setField(codec,"executable",binary);
        Path png=dir.resolve("transparent.png"),jpeg=dir.resolve("source.jpg"),webp=dir.resolve("source.webp"),gif=dir.resolve("animated.gif");
        ImageIO.write(new BufferedImage(120,80,BufferedImage.TYPE_INT_ARGB),"png",png.toFile());
        ImageIO.write(new BufferedImage(2000,1000,BufferedImage.TYPE_INT_RGB),"jpeg",jpeg.toFile());
        command(binary,png.toString(),webp.toString());
        command(binary,"-size","12x8","xc:none","-size","12x8","xc:red","-loop","0",gif.toString());
        Path heic=dir.resolve("source.heic"),heif=dir.resolve("source.heif");
        try(java.io.InputStream in=getClass().getResourceAsStream("/photo-example.heic")){assertNotNull(in);Files.copy(in,heic);}
        Files.copy(heic,heif);
        for(Path source:new Path[]{jpeg,png,gif,webp,heic,heif}) {
            Path result=dir.resolve(source.getFileName()+".jpg");TkPhotoNormalizationService.Result r=codec.normalize(source,result);
            assertEquals("JPEG",TkPhotoNormalizationService.detectFormat(result));assertTrue(Math.max(r.getWidth(),r.getHeight())<=1920);
            assertTrue(Math.min(r.getWidth(),r.getHeight())<=1080);assertEquals(64,r.getSha256().length());
        }
        assertEquals(0xffffff,ImageIO.read(dir.resolve("transparent.png.jpg").toFile()).getRGB(0,0)&0xffffff);
        assertEquals(0xffffff,ImageIO.read(dir.resolve("animated.gif.jpg").toFile()).getRGB(0,0)&0xffffff);
        Path offset=dir.resolve("offset.gif");command(binary,"-size","3x3","xc:none","-fill","red","-draw","rectangle 1,1 2,2","-page","10x10+3+3",offset.toString());
        Path result=dir.resolve("offset.jpg");TkPhotoNormalizationService.Result r=codec.normalize(offset,result);
        assertEquals(10,r.getWidth());assertEquals(10,r.getHeight());
        int pixel=ImageIO.read(result.toFile()).getRGB(0,0);assertTrue(((pixel>>16)&255)>220 && ((pixel>>8)&255)>220 && (pixel&255)>220);
    }
    private void command(String... args) throws Exception {
        Process p=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(p.waitFor(45,TimeUnit.SECONDS),"fixture generation timed out");assertEquals(0,p.exitValue(),"fixture generation failed");
    }
}
