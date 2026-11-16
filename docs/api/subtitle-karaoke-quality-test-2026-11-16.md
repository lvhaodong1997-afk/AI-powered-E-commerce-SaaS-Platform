# 字幕逐字高亮 P0 优化测试报告

日期：2026-11-16。范围：Java 字幕对齐、ASS 渲染可靠性判断、质量诊断与一次备选 ASR 重试。此报告为代码发布前验收；发布提交、制品哈希、服务器回放及健康检查另记于发布记录。

## 结论

139 项后端聚焦测试和 4 项 Python 字幕测试通过。任务 2071 使用原音频、原 ASR 和原文案对照：可靠高亮由 1/4 段提升至 3/4 段，普通字幕降级由 3/4 段减少至 1/4 段；可靠文字覆盖由 5/41（12.20%）提升至 31/41（75.61%）。结果仍为 `partial`，未达到 95% 目标。

剩余一段包含 ASR 把“湿”识别为“石”的真实文字不匹配。该段继续普通字幕，原文案中的“湿”保留，不将错字或插值时间标为可靠。服务器缓存仅有 base 模型，缺少默认备选 medium；按现有离线模型策略跳过，不自动下载。

## 修改内容

- 复用已有 OpenCC 依赖，将简繁体用于匹配时等价化，显示文字保留原文；不把同音字等价化。
- 在既有宽度和两行约束内移动字幕边界，尽量保留完整 ASR 词组及其实际时间。
- 校验词组的完整连续来源，组内漏字、重复字错配、切组与估算时间继续安全降级。
- 可靠文字覆盖不足时，且启用了逐字高亮，最多尝试一次备选识别；主备共享总识别超时，选择有效可靠文字覆盖更高的结果。
- 禁用高亮、禁用重试、相同模型、缺模型及预算耗尽时，不新增无效识别。保留原有文字不匹配重试路径。
- 质量 JSON 追加可靠文字数、总文字数、覆盖率和逐段原因；诊断与渲染使用同一个校验方法。外部状态仍为 `off/enabled/partial/disabled`。
- 无数据库迁移、前端改版、新付费接口或强制对齐依赖。

## 真实任务证据

租户“秀美二楼”，任务 2071。文案：雨天出门，给鞋子多一层保护。套好防雨鞋套，检查边缘是否贴合。遇到湿滑路面放慢脚步，出行更从容。

实际字幕设置：`classic_white`、`extra_large`，字幕/关键词/逐字高亮开启。原音频归一为单声道 24000 Hz PCM，时长 7.140125 秒；使用服务器现有 base 模型重识别耗时 3.655 秒。新识别 JSON 与原始 ASR 文件逐字节一致。

| 字幕段 | 旧版 | 优化后 | 依据 |
| --- | --- | --- | --- |
| 雨天出门，给鞋子多一层保护。 | 普通字幕 | 可靠高亮 | 门/門、给/給、层/層、护/護为简繁等价 |
| 套好防雨鞋套，检查边缘是否贴合。 | 普通字幕 | 可靠高亮 | 检/檢、边/邊、缘/緣、贴/貼为简繁等价 |
| 遇到湿滑路面放慢脚步， | 普通字幕 | 普通字幕 | 湿/石不匹配，`WORD_TEXT_MISMATCH` |
| 出行更从容。 | 可靠高亮 | 可靠高亮 | 保留有效实际时间 |

旧发布包在服务器生产字体下离线回放复现原 1/4 可靠段。新代码本机独立回放复现 3/4，匹配词的原始时间锚点保留，例如“门”起点 0.52 秒。回放不调用 TTS、不创建收费任务，不覆盖任务原始成片或数据库记录。

| 原始证据 | SHA-256 |
| --- | --- |
| 音频 MP3 | `3b953098d39431babddfbaf5eac1005c4145367189c558f06984b9c1867cd3d7` |
| ASR JSON | `be091d4e7746c8515b5ea28d2c69eae5f88704f1d046ccf9c3feadc1af7a190a` |
| 时间轴 JSON | `eeebe72a799f685c32bcb9c612b1806f855dd3de7ee28c57471a05be111aace9` |
| ASS | `6dc5fb9136be3a1c9736895dae4ca27c85db1f7f70af11c9c410de436d34a2d7` |

## 自动化验证

| 测试类 | 通过 |
| --- | ---: |
| DefaultTkSubtitleTimelineServiceTest | 62 |
| DefaultTkAssSubtitleRenderServiceTest | 18 |
| TkSubtitleKaraokeQualityTest | 23 |
| TkSubtitleDeliveryTest | 20 |
| TkSubtitleLineBreakSupportTest | 7 |
| DefaultTkSubtitleLayoutServiceTest | 9 |
| Python test_asr_faster_whisper | 4 |

六个 Java 测试类共 139 项，使用服务器 Noto Sans/Serif CJK Regular/Bold 四个 TTC 字体。首次环境不完整时排版断言失败；补齐生产字体后相关 36 项全部通过、无跳过。核心 103 项已在最终代码上通过。未改动排版断言以绕过问题。

从产品源目录执行，使用仓库 Maven、JDK 和 `-Djava.version=17`：

```text
mvn -pl yudao-module-tk -am -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17 -Dtest=DefaultTkSubtitleTimelineServiceTest,DefaultTkAssSubtitleRenderServiceTest,TkSubtitleKaraokeQualityTest,TkSubtitleDeliveryTest,TkSubtitleLineBreakSupportTest,DefaultTkSubtitleLayoutServiceTest -Dtk.subtitle.fonts=<生产字体目录> test
python -m unittest discover -s tools/subtitle -p test_asr_faster_whisper.py -v
```

覆盖包括：负时间、零时长、非有限时间、倒序/重叠、超出字幕/音频边界、正文不一致、估算时间不可信、完整实际词组、中文跨组边界、组内插字、重复字插字、重复短语、数字、小数、中英混排、标点/引号、原文换行、关键词颜色、字号/预设排版、缺少 words、备选改善/变差、坏 JSON、非零退出、超时、共享预算耗尽、禁用重试、同模型、缺缓存模型、关闭高亮时不触发新增重试。

新增关键回归先验证 RED，再验证 GREEN。独立审查提出的“组内漏字仍被信任”和“关闭高亮仍重试”两项已补六个回归用例修正。两个旧渲染测试辅助方法纠正了正文与 words 不一致、仅读第一条 Dialogue 的不实夹具；旧版独立回放也复现了后者丢尾词的测试问题。

## 验收边界

- 通过：减少简繁差异造成的误降级；诊断与渲染一致；一次重试和总预算；备选失败保留可用主结果；有效词组完整；原文保留。
- 未达到：该真实样本可靠覆盖率 95%，以及全部 4/4 段逐字高亮。
- 未测得：人工标注的词边界 P95 ≤200 ms、多语言大规模准确率、并发压力和完整口播人工听审。因此不以自动文本匹配推断这些指标通过。
- 后续可选：准备适当的备选模型和独立标注集后，再测“湿/石”等真实识别错误；这不属于本次模型依赖变更。
