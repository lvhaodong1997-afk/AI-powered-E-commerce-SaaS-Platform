package cn.iocoder.yudao.module.tk.service.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TkPhotoNormalizationServiceTest {
    @TempDir Path dir;

    @Test void detectsActualPngAndGifEvenWithUntrustedNames() throws Exception {
        BufferedImage image = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        Path png = dir.resolve("payload.dat"), gif = dir.resolve("another.bin");
        ImageIO.write(image, "png", png.toFile()); ImageIO.write(new BufferedImage(8,4,BufferedImage.TYPE_BYTE_INDEXED), "gif", gif.toFile());
        assertEquals("PNG", TkPhotoNormalizationService.detectFormat(png));
        assertEquals("GIF", TkPhotoNormalizationService.detectFormat(gif));
    }

    @Test void rejectsSvgDisguisedAsJpeg() throws Exception {
        Path source = dir.resolve("photo.jpg"); Files.writeString(source, "<svg xmlns='http://www.w3.org/2000/svg'/>");
        assertThrows(IllegalArgumentException.class, () -> TkPhotoNormalizationService.detectFormat(source));
    }

    @Test void acceptsOnlyHeifCompatibleIsoBrands() throws Exception {
        Path source = dir.resolve("image.heic");
        Files.write(source, new byte[]{0,0,0,24,'f','t','y','p','h','e','i','c',0,0,0,0,'m','i','f','1',0,0,0,0});
        assertEquals("HEIC", TkPhotoNormalizationService.detectFormat(source));
        Files.write(source, new byte[]{0,0,0,24,'f','t','y','p','a','v','i','f',0,0,0,0,'m','i','f','1',0,0,0,0});
        assertThrows(IllegalArgumentException.class, () -> TkPhotoNormalizationService.detectFormat(source));
    }

    @Test void enforcesBothEdgesWithoutUpscaling() {
        assertArrayEquals(new int[]{1920,1080}, TkPhotoNormalizationService.fit(4000,2250));
        assertArrayEquals(new int[]{1080,1920}, TkPhotoNormalizationService.fit(2250,4000));
        assertArrayEquals(new int[]{1080,1080}, TkPhotoNormalizationService.fit(2000,2000));
        assertArrayEquals(new int[]{80,40}, TkPhotoNormalizationService.fit(80,40));
        assertThrows(IllegalArgumentException.class, () -> TkPhotoNormalizationService.fit(30000,30000));
    }

    @Test void commandUsesFirstFrameAndArgumentSeparatedLocalPaths() {
        TkPhotoNormalizationService service = new TkPhotoNormalizationService();
        List<String> command = service.conversionCommand(dir.resolve("in.png"), dir.resolve("out.jpg"), "PNG",800,600);
        assertTrue(command.contains("PNG:" + dir.resolve("in.png").toAbsolutePath() + "[0]"));
        assertTrue(command.contains("-auto-orient")); assertTrue(command.contains("-background"));
        assertTrue(command.contains("white")); assertTrue(command.contains("sRGB"));
        assertEquals("JPEG:"+dir.resolve("out.jpg").toAbsolutePath(), command.get(command.size()-1));
    }

    @Test void validatesFinalJpegBytesDimensionsAndHash() throws Exception {
        Path jpeg=dir.resolve("final.jpg"); ImageIO.write(new BufferedImage(10,5,BufferedImage.TYPE_INT_RGB),"jpeg",jpeg.toFile());
        TkPhotoNormalizationService.Result result=TkPhotoNormalizationService.validateResult(jpeg);
        assertEquals(10,result.getWidth()); assertEquals(5,result.getHeight());
        assertEquals(Files.size(jpeg),result.getBytes()); assertEquals(64,result.getSha256().length());
        Path png=dir.resolve("invalid.jpg"); ImageIO.write(new BufferedImage(10,5,BufferedImage.TYPE_INT_RGB),"png",png.toFile());
        assertThrows(IllegalArgumentException.class,()->TkPhotoNormalizationService.validateResult(png));
    }

    @Test void killsConverterAtWallClockDeadline() throws Exception {
        TkPhotoNormalizationService service=new TkPhotoNormalizationService();
        org.springframework.test.util.ReflectionTestUtils.setField(service,"timeoutSeconds",1);
        String javaBin=Path.of(System.getProperty("java.home"),"bin","java").toString();
        long start=System.nanoTime();
        IllegalStateException ex=assertThrows(IllegalStateException.class,()->service.run(
                List.of(javaBin,"-cp",System.getProperty("java.class.path"),Sleeper.class.getName()),dir));
        assertTrue(ex.getMessage().contains("超时")); assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)<10);
    }
    public static class Sleeper { public static void main(String[] args) throws Exception {Thread.sleep(30000);} }
}
