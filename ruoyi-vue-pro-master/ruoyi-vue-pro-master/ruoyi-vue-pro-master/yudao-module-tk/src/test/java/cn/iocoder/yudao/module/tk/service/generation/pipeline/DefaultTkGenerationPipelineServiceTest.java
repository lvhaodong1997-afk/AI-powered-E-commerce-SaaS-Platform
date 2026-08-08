package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.ArgumentCaptor;

class DefaultTkGenerationPipelineServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void probeDownloadedMediaDurationReadsDurationFromDownloadedAudioFile() throws Exception {
        DefaultTkGenerationPipelineService service = new DefaultTkGenerationPipelineService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        byte[] wav = oneSecondSilentWav();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/voice.wav", exchange -> {
            exchange.sendResponseHeaders(200, wav.length);
            exchange.getResponseBody().write(wav);
            exchange.close();
        });
        server.start();

        Double duration = service.probeDownloadedMediaDuration("http://127.0.0.1:" + server.getAddress().getPort() + "/voice.wav");

        assertNotNull(duration);
        assertTrue(duration >= 0.9D && duration <= 1.1D);
    }

    @Test
    void resolveScriptReusesExistingScriptText() {
        DefaultTkGenerationPipelineService service = new DefaultTkGenerationPipelineService();
        TkScriptGenerationService scriptGenerationService = mock(TkScriptGenerationService.class);
        ReflectionTestUtils.setField(service, "scriptGenerationService", scriptGenerationService);
        TkGenerationTaskDO task = new TkGenerationTaskDO()
                .setTitle("Saved title")
                .setScriptText("Saved script")
                .setSegmentTimeline("[]")
                .setReferenceDuration(21)
                .setTargetDuration(25);

        TkGeneratedScript script = service.resolveScript(task, new TkMaterialLibraryDO());

        assertEquals("Saved title", script.getTitle());
        assertEquals("Saved script", script.getContent());
        assertEquals("[]", script.getSegmentTimeline());
        assertEquals(21, script.getReferenceDuration());
        assertEquals(25, script.getTargetDuration());
        verifyNoInteractions(scriptGenerationService);
    }

    @Test
    void resolveAudioAssetReusesExistingAudioUrl() {
        DefaultTkGenerationPipelineService service = new DefaultTkGenerationPipelineService();
        TkVoiceSynthesisService voiceSynthesisService = mock(TkVoiceSynthesisService.class);
        ReflectionTestUtils.setField(service, "voiceSynthesisService", voiceSynthesisService);
        TkGenerationTaskDO task = new TkGenerationTaskDO()
                .setVoiceEnabled(true)
                .setAudioUrl("https://example.com/audio.mp3")
                .setSubtitleUrl("https://example.com/subtitle.ass");

        TkAudioAsset audioAsset = service.resolveAudioAsset(task, "Saved script");

        assertEquals("https://example.com/audio.mp3", audioAsset.getAudioUrl());
        assertEquals("https://example.com/subtitle.ass", audioAsset.getSubtitleUrl());
        verifyNoInteractions(voiceSynthesisService);
    }

    @Test
    void resolveClipPlanReusesExistingClipPlanJson() {
        DefaultTkGenerationPipelineService service = new DefaultTkGenerationPipelineService();
        TkClipPlannerService clipPlannerService = mock(TkClipPlannerService.class);
        ReflectionTestUtils.setField(service, "clipPlannerService", clipPlannerService);
        TkClipPlanItem item = new TkClipPlanItem(1, "MATERIAL", 11L, "a.mp4",
                "https://example.com/a.mp4", 0, 3, "reuse");
        TkGenerationTaskDO task = new TkGenerationTaskDO()
                .setClipPlan(JsonUtils.toJsonString(Arrays.asList(item)));

        List<TkClipPlanItem> clipPlan = service.resolveClipPlan(task, "Saved script", 30);

        assertEquals(1, clipPlan.size());
        assertEquals(11L, clipPlan.get(0).getMaterialVideoId());
        assertEquals("https://example.com/a.mp4", clipPlan.get(0).getFileUrl());
        verifyNoInteractions(clipPlannerService);
    }

    @Test
    void updateSuccessClearsPreviousFailCodeAndReason() {
        DefaultTkGenerationPipelineService service = new DefaultTkGenerationPipelineService();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        org.mockito.Mockito.when(taskMapper.selectById(188L)).thenReturn(new TkGenerationTaskDO().setId(188L));

        ReflectionTestUtils.invokeMethod(service, "update", 188L, TkGenerationStatusEnum.SUCCESS, 100,
                "生成完成", null, null);

        ArgumentCaptor<Wrapper<TkGenerationTaskDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).update(isNull(), wrapperCaptor.capture());
        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("fail_code"));
        assertTrue(sqlSet.contains("fail_reason"));
    }

    private byte[] oneSecondSilentWav() throws IOException {
        int sampleRate = 8000;
        int channels = 1;
        int bitsPerSample = 16;
        int dataSize = sampleRate * channels * bitsPerSample / 8;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("RIFF".getBytes());
        writeInt(output, 36 + dataSize);
        output.write("WAVE".getBytes());
        output.write("fmt ".getBytes());
        writeInt(output, 16);
        writeShort(output, 1);
        writeShort(output, channels);
        writeInt(output, sampleRate);
        writeInt(output, sampleRate * channels * bitsPerSample / 8);
        writeShort(output, channels * bitsPerSample / 8);
        writeShort(output, bitsPerSample);
        output.write("data".getBytes());
        writeInt(output, dataSize);
        output.write(new byte[dataSize]);
        return output.toByteArray();
    }

    private void writeInt(ByteArrayOutputStream output, int value) throws IOException {
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private void writeShort(ByteArrayOutputStream output, int value) throws IOException {
        output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array());
    }

}
