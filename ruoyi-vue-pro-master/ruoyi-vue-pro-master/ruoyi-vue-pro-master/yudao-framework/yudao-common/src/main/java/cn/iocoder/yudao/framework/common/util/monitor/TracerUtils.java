package cn.iocoder.yudao.framework.common.util.monitor;

/**
 * 链路追踪工具类。
 *
 * @author 秀美源码
 */
public class TracerUtils {

    /**
     * 私有化构造方法
     */
    private TracerUtils() {
    }

    /**
     * 获得链路追踪编号。当前项目不接入链路追踪，固定返回空字符串。
     *
     * @return 链路追踪编号
     */
    public static String getTraceId() {
        return "";
    }

}
