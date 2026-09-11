package cn.iocoder.yudao.module.tk.service.open.ocr;

import org.springframework.web.multipart.MultipartFile;

public interface TkOpenOcrService {

    String recognize(MultipartFile file);

}
