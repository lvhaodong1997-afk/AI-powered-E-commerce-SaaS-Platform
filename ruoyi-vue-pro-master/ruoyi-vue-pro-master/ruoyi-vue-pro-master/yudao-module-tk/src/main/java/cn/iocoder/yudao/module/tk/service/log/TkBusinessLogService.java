package cn.iocoder.yudao.module.tk.service.log;

public interface TkBusinessLogService {

    String LEVEL_INFO = "INFO";
    String LEVEL_WARN = "WARN";
    String LEVEL_ERROR = "ERROR";

    void info(String bizType, Long bizId, String action, String status, String message, Object detail);

    void info(String businessTraceId, String bizType, Long bizId, String action, String status, String message, Object detail);

    void warn(String bizType, Long bizId, String action, String status, String message, Object detail);

    void warn(String businessTraceId, String bizType, Long bizId, String action, String status, String message, Object detail);

    void error(String bizType, Long bizId, String action, String status, String message, Object detail);

    void error(String businessTraceId, String bizType, Long bizId, String action, String status, String message, Object detail);

}
