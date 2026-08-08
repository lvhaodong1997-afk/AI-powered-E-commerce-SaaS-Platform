package cn.iocoder.yudao.module.tk.service.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkDashboardDiagnosticsTest {

    @Test
    void classifyFailureShouldPreferRenderStepForFfmpegTimeouts() {
        TkDashboardDiagnostics.FailureDiagnosis diagnosis = TkDashboardDiagnostics.classifyFailure(
                "FFMPEG_RENDER_FAILED",
                "RENDERING",
                "FFmpeg render failed: SocketTimeoutException: Read timed out");

        assertEquals("FFMPEG_RENDER", diagnosis.getCategory());
        assertEquals("RENDERING", diagnosis.getActionStatus());
        assertTrue(diagnosis.getActionHint().contains("render"));
    }

    @Test
    void classifyFailureShouldDetectDownloadTimeouts() {
        TkDashboardDiagnostics.FailureDiagnosis diagnosis = TkDashboardDiagnostics.classifyFailure(
                "SOURCE_TIMEOUT",
                "ANALYZING",
                "SocketTimeoutException: Read timed out while downloading source video");

        assertEquals("REFERENCE_DOWNLOAD", diagnosis.getCategory());
        assertEquals("FAILED", diagnosis.getActionStatus());
        assertTrue(diagnosis.getActionHint().contains("source"));
    }

    @Test
    void runningStatusShouldIncludePipelineStepsButExcludeTerminalStatuses() {
        assertTrue(TkDashboardDiagnostics.isRunningStatus("PENDING"));
        assertTrue(TkDashboardDiagnostics.isRunningStatus("EXPORTING"));
        assertEquals(false, TkDashboardDiagnostics.isRunningStatus("SUCCESS"));
        assertEquals(false, TkDashboardDiagnostics.isRunningStatus("FAILED"));
    }
}
