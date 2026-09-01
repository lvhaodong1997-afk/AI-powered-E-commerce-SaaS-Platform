package cn.iocoder.yudao.module.tk.service.generation.pipeline;

@FunctionalInterface
public interface TkRenderProgressReporter {

    TkRenderProgressReporter NOOP = (stepCode, stepName, progress, completed, total) -> {
    };

    void report(String stepCode, String stepName, int progress, int completed, int total);

}
