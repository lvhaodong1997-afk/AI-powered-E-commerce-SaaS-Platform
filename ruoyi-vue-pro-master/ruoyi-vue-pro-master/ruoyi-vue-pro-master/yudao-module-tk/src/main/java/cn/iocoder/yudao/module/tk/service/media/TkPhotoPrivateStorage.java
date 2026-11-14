package cn.iocoder.yudao.module.tk.service.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;

/** Durable server-only scratch, deliberately outside the public upload tree. */
@Service
public class TkPhotoPrivateStorage {
    @Value("${tk.photo.private-root:${java.io.tmpdir}/tk-photo-private}")
    private String rootDir=System.getProperty("java.io.tmpdir")+"/tk-photo-private";
    public Path root(){return Paths.get(rootDir).toAbsolutePath().normalize();}
    public Path session(String id) {
        if(id==null || !id.matches("(?:upload|photo)_[0-9a-f]{32}")) throw new IllegalArgumentException("图片会话标识无效");
        return root().resolve(id);
    }
    public Path createSession(String id) throws java.io.IOException {
        Path folder=session(id);Files.createDirectories(root());
        secure(root());Files.createDirectories(folder);secure(folder);return folder;
    }
    private void secure(Path path) throws java.io.IOException {
        if(Files.isSymbolicLink(path))throw new IllegalArgumentException("图片私有目录不能为符号链接");
        if(Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    }
    public Path requireOwned(String value) {
        Path p=Paths.get(value).toAbsolutePath().normalize();
        if(!p.startsWith(root()) || p.equals(root()) || Files.isSymbolicLink(p) || Files.isSymbolicLink(p.getParent()))
            throw new IllegalArgumentException("图片临时文件路径无效");
        return p;
    }
    public void deleteSource(String value) {
        if(value==null) return;
        try {Files.deleteIfExists(requireOwned(value));}catch(java.io.IOException e){throw new IllegalStateException("图片临时文件清理失败",e);}
    }
    public void deleteTree(Path folder) {
        Path owned=requireOwned(folder.toString());
        if(!Files.exists(owned))return;
        try(java.util.stream.Stream<Path> stream=Files.walk(owned)) {
            java.util.List<Path> paths=stream.sorted(java.util.Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList());
            for(Path path:paths) Files.deleteIfExists(requireOwned(path.toString()));
        }catch(java.io.IOException e){throw new IllegalStateException("图片临时文件清理失败",e);}
    }
}
