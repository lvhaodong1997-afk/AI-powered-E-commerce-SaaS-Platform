<template>
  <div class="creative-workshop">
    <section class="hero-panel">
      <div class="hero-copy">
        <div class="hero-kicker">
          <Icon icon="ep:magic-stick" />
          <span>AI 创意工坊</span>
        </div>
        <h1>把商品卖点变成可投放创意</h1>
        <p>输入产品信息、场景诉求或参考脚本，快速预生成短视频、图片、头像和带货创意方案。</p>
      </div>
      <div class="hero-orbit" aria-hidden="true">
        <span class="orbit-core">AI</span>
        <span class="orbit-dot dot-video"><Icon icon="ep:video-camera" /></span>
        <span class="orbit-dot dot-image"><Icon icon="ep:picture" /></span>
        <span class="orbit-dot dot-script"><Icon icon="ep:document" /></span>
      </div>
    </section>

    <section class="prompt-panel">
      <div class="prompt-topline">
        <button class="upload-entry" type="button" disabled>
          <Icon icon="ep:picture-filled" />
        </button>
        <button class="wizard-button" type="button" @click="handlePromptWizard">
          <Icon icon="ep:magic-stick" />
          提示词助手
        </button>
      </div>

      <el-input
        v-model="form.prompt"
        class="prompt-input"
        type="textarea"
        :autosize="{ minRows: 4, maxRows: 7 }"
        resize="none"
        maxlength="1200"
        show-word-limit
        placeholder="请输入创意需求。例如：为一款便携补光灯生成 TikTok 带货短视频，突出轻便、可调光、适合直播和旅行拍摄。禁止暴力、色情、侵权及名人相关内容。"
      />

      <div class="control-row">
        <el-select v-model="form.mode" class="control-select mode-select">
          <el-option
            v-for="item in modeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="form.model" class="control-select model-select">
          <el-option
            v-for="item in modelOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <span>{{ item.label }}</span>
            <span v-if="item.badge" class="option-badge">{{ item.badge }}</span>
          </el-option>
        </el-select>

        <div class="quick-controls">
          <button
            v-for="item in quickControls"
            :key="item.key"
            class="quick-control"
            type="button"
            @click="cycleQuickControl(item.key)"
          >
            <Icon :icon="item.icon" />
            <span>{{ item.label }}</span>
          </button>
        </div>

        <button class="generate-button" type="button" :disabled="generating" @click="handleGenerate">
          <Icon icon="ep:promotion" />
          <span>{{ generating ? '生成中' : '生成创意' }}</span>
          <em>{{ creditCost }} 点</em>
        </button>
      </div>
    </section>

    <div class="tabs-row">
      <button
        v-for="tab in categoryTabs"
        :key="tab.value"
        class="tab-button"
        :class="{ active: activeCategory === tab.value }"
        type="button"
        @click="activeCategory = tab.value"
      >
        {{ tab.label }}
      </button>
    </div>

    <section class="tool-grid">
      <article v-for="tool in visibleTools" :key="tool.title" class="tool-card">
        <div class="tool-visual" :class="`tone-${tool.tone}`">
          <span v-if="tool.badge" class="tool-badge">{{ tool.badge }}</span>
          <div v-if="tool.preview === 'people'" class="people-preview">
            <span></span>
            <span></span>
            <span></span>
          </div>
          <div v-else-if="tool.preview === 'commerce'" class="commerce-preview">
            <Icon icon="ep:goods" />
            <span></span>
            <span></span>
          </div>
          <div v-else-if="tool.preview === 'avatar'" class="avatar-preview">
            <span class="avatar-head"></span>
            <span class="avatar-body"></span>
          </div>
          <div v-else class="logo-preview">
            <Icon :icon="tool.icon" />
          </div>
        </div>
        <div class="tool-title">
          <strong>{{ tool.title }}</strong>
          <span>{{ tool.desc }}</span>
        </div>
      </article>
    </section>

    <button class="floating-help" type="button" disabled>
      <Icon icon="ep:chat-dot-round" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { TkCreativeWorkshopApi } from '@/api/tk/creativeWorkshop'

defineOptions({ name: 'TkCreativeWorkshop' })

type Category = 'all' | 'video' | 'image' | 'avatar'
type QuickKey = 'ratio' | 'duration' | 'count'

const message = useMessage()
const generating = ref(false)
const activeCategory = ref<Category>('all')

const form = reactive({
  prompt: '',
  mode: 'video',
  model: 'spark-video',
  ratio: '9:16',
  duration: '12秒',
  count: 1,
  style: 'commercial'
})

const modeOptions = [
  { label: 'AI 视频', value: 'video' },
  { label: 'AI 图片', value: 'image' },
  { label: '数字人', value: 'avatar' },
  { label: '创意脚本', value: 'script' }
]

const modelOptions = [
  { label: '闪电创意引擎', value: 'spark-video', badge: '推荐' },
  { label: '商品短片生成', value: 'commerce-video', badge: '限时体验' },
  { label: '图像创意生成', value: 'image-lab' },
  { label: '数字人分身', value: 'avatar-studio' }
]

const categoryTabs: Array<{ label: string; value: Category }> = [
  { label: '全部', value: 'all' },
  { label: 'AI 视频生成', value: 'video' },
  { label: 'AI 图片生成', value: 'image' },
  { label: '数字人头像', value: 'avatar' }
]

const tools = [
  {
    title: '全能闪电',
    desc: '快速生成短视频创意',
    category: 'video',
    icon: 'ep:lightning',
    tone: 'neon',
    badge: '新'
  },
  {
    title: '种草短片 2.0',
    desc: '商品卖点自动成片',
    category: 'video',
    icon: 'ep:video-camera',
    tone: 'aurora'
  },
  {
    title: '爆款脚本视频',
    desc: '脚本、镜头、字幕一体生成',
    category: 'video',
    icon: 'ep:film',
    tone: 'blue',
    badge: '限时 50% Off'
  },
  {
    title: '智能配图',
    desc: '电商主图与场景图',
    category: 'image',
    icon: 'ep:picture',
    tone: 'rainbow'
  },
  {
    title: '商品海报',
    desc: '促销图与广告素材',
    category: 'image',
    icon: 'ep:goods',
    tone: 'dark'
  },
  {
    title: 'AI 精修图',
    desc: '画质增强与背景重绘',
    category: 'image',
    icon: 'ep:brush',
    tone: 'ink'
  },
  {
    title: '视频复刻',
    desc: '参考结构生成新创意',
    category: 'video',
    icon: 'ep:copy-document',
    tone: 'photo',
    preview: 'people'
  },
  {
    title: 'UGC 带货内容',
    desc: '达人视角商品口播',
    category: 'video',
    icon: 'ep:shopping-cart',
    tone: 'market',
    preview: 'commerce'
  },
  {
    title: '我的数字人',
    desc: '虚拟主播和品牌分身',
    category: 'avatar',
    icon: 'ep:user',
    tone: 'stage',
    preview: 'avatar'
  },
  {
    title: '数字人长视频',
    desc: '知识讲解与带货直播切片',
    category: 'avatar',
    icon: 'ep:user-filled',
    tone: 'soft',
    preview: 'avatar'
  },
  {
    title: '真人 + 动作',
    desc: '动作参考驱动数字人',
    category: 'avatar',
    icon: 'ep:coordinate',
    tone: 'studio',
    preview: 'avatar'
  },
  {
    title: '提示词反推',
    desc: '从图片/视频生成提示词',
    category: 'image',
    icon: 'ep:search',
    tone: 'ice'
  }
]

const visibleTools = computed(() =>
  activeCategory.value === 'all'
    ? tools
    : tools.filter((item) => item.category === activeCategory.value)
)

const creditCost = computed(() => {
  const durationCost = form.duration === '20秒' ? 20 : form.duration === '16秒' ? 16 : 12
  return durationCost + form.count * 3
})

const quickControls = computed(() => [
  { key: 'ratio' as const, icon: 'ep:full-screen', label: form.ratio },
  { key: 'duration' as const, icon: 'ep:clock', label: form.duration },
  { key: 'count' as const, icon: 'ep:video-play', label: `x${form.count}` }
])

const cycleQuickControl = (key: QuickKey) => {
  const ratioList = ['9:16', '1:1', '16:9']
  const durationList = ['12秒', '16秒', '20秒']
  const countList = [1, 2, 4]
  if (key === 'ratio') {
    form.ratio = nextValue(ratioList, form.ratio)
  }
  if (key === 'duration') {
    form.duration = nextValue(durationList, form.duration)
  }
  if (key === 'count') {
    form.count = nextValue(countList, form.count)
  }
}

const nextValue = <T,>(list: T[], current: T) => {
  const index = list.findIndex((item) => item === current)
  return list[(index + 1) % list.length]
}

const handlePromptWizard = () => {
  if (!form.prompt) {
    form.prompt =
      '为一款跨境电商商品生成 TikTok 创意短视频，要求：前 3 秒强钩子，中段展示核心卖点和使用场景，结尾加入购买引导；画面干净、节奏快、适合竖屏投放。'
  }
}

const handleGenerate = async () => {
  if (!form.prompt.trim()) {
    message.warning('请先输入创意需求')
    return
  }

  generating.value = true
  try {
    await TkCreativeWorkshopApi.generate({
      prompt: form.prompt.trim(),
      mode: form.mode,
      model: form.model,
      ratio: form.ratio,
      duration: form.duration,
      count: form.count,
      style: form.style
    })
    message.success('创意生成任务已提交')
  } catch {
    message.error('创意生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.creative-workshop {
  position: relative;
  min-height: 100%;
  padding: 24px 28px 34px;
  color: #101828;
  background:
    linear-gradient(135deg, rgb(244 248 255 / 96%) 0%, rgb(250 251 255 / 96%) 42%, #f6f8fb 100%);
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 24px;
  max-width: 1280px;
  margin: 0 auto 20px;
  align-items: center;
}

.hero-copy h1 {
  margin: 8px 0 10px;
  font-size: 32px;
  font-weight: 900;
  line-height: 1.18;
  color: #101322;
}

.hero-copy p {
  max-width: 680px;
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #667085;
}

.hero-kicker,
.wizard-button,
.generate-button,
.quick-control,
.tab-button,
.upload-entry,
.floating-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.hero-kicker {
  gap: 7px;
  width: fit-content;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 800;
  color: #6f47df;
  background: #f0ecff;
  border: 1px solid #e3dafe;
  border-radius: 999px;
}

.hero-orbit {
  position: relative;
  width: 160px;
  height: 120px;
  justify-self: end;
}

.orbit-core {
  position: absolute;
  top: 31px;
  left: 44px;
  display: grid;
  width: 74px;
  height: 74px;
  font-size: 24px;
  font-weight: 900;
  color: #fff;
  background: linear-gradient(135deg, #171b2e, #4937ff);
  border-radius: 26px;
  box-shadow: 0 22px 40px rgb(73 55 255 / 28%);
  place-items: center;
}

.orbit-dot {
  position: absolute;
  display: grid;
  width: 38px;
  height: 38px;
  color: #fff;
  border-radius: 50%;
  box-shadow: 0 12px 22px rgb(24 30 52 / 14%);
  place-items: center;
}

.dot-video {
  top: 8px;
  left: 12px;
  background: #16a3b8;
}

.dot-image {
  right: 0;
  bottom: 18px;
  background: #f06292;
}

.dot-script {
  bottom: 0;
  left: 20px;
  background: #f59e0b;
}

.prompt-panel {
  max-width: 1280px;
  padding: 28px 30px 30px;
  margin: 0 auto 28px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid #edf0f6;
  border-radius: 8px;
  box-shadow: 0 22px 42px rgb(15 23 42 / 10%);
}

.prompt-topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.upload-entry {
  width: 50px;
  height: 50px;
  color: #8c98ad;
  cursor: pointer;
  background: #fff;
  border: 1px dashed #b9c2d3;
  border-radius: 8px;
}

.upload-entry:not(:disabled):hover {
  color: #6f47df;
  border-color: #9e8cff;
}

.upload-entry:disabled,
.floating-help:disabled {
  cursor: not-allowed;
  opacity: 0.52;
}

.wizard-button {
  height: 40px;
  padding: 0 18px;
  font-weight: 800;
  color: #8b4ff3;
  cursor: pointer;
  background: linear-gradient(90deg, #eef6ff, #faedf6);
  border: 0;
  border-radius: 999px;
  gap: 7px;
}

.prompt-input :deep(.el-textarea__inner) {
  padding: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #384152;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.control-row {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  align-items: center;
}

.control-select {
  flex: 0 0 auto;
}

.mode-select {
  width: 130px;
}

.model-select {
  width: 250px;
}

.control-select :deep(.el-select__wrapper) {
  min-height: 44px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dce2ec inset;
}

.option-badge {
  margin-left: 10px;
  font-size: 11px;
  color: #ff4d5e;
}

.quick-controls {
  display: inline-flex;
  min-height: 36px;
  padding: 4px 8px;
  border: 1px solid #dde4ef;
  border-radius: 999px;
  align-items: center;
  gap: 2px;
}

.quick-control {
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
  color: #516176;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
  gap: 5px;
}

.quick-control:hover {
  background: #f4f6fb;
}

.generate-button {
  height: 42px;
  padding: 0 18px;
  margin-left: auto;
  font-weight: 900;
  color: #111827;
  cursor: pointer;
  background: linear-gradient(135deg, #cbb7ff, #d8c9ff);
  border: 0;
  border-radius: 999px;
  gap: 7px;
}

.generate-button:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.generate-button em {
  font-size: 12px;
  font-style: normal;
  color: #ff4b5c;
}

.tabs-row {
  display: inline-flex;
  max-width: 1280px;
  padding: 4px;
  margin: 0 calc((100% - min(1280px, 100%)) / 2) 18px;
  background: rgb(255 255 255 / 86%);
  border: 1px solid #dce3ef;
  border-radius: 999px;
  box-shadow: 0 16px 34px rgb(21 31 55 / 7%);
  gap: 4px;
}

.tab-button {
  height: 36px;
  padding: 0 15px;
  font-size: 13px;
  font-weight: 800;
  color: #536174;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
}

.tab-button.active {
  color: #fff;
  background: #05070b;
}

.tool-grid {
  display: grid;
  max-width: 1280px;
  margin: 0 auto;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 18px;
}

.tool-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e1e6ef;
  border-radius: 8px;
  box-shadow: 0 14px 30px rgb(15 23 42 / 6%);
}

.tool-visual {
  position: relative;
  display: grid;
  height: 110px;
  overflow: hidden;
  place-items: center;
}

.tool-badge {
  position: absolute;
  top: 8px;
  right: 9px;
  z-index: 2;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 900;
  color: #fff;
  background: #ef4444;
  border-radius: 999px;
}

.logo-preview {
  display: grid;
  width: 58px;
  height: 58px;
  color: #fff;
  background: rgb(0 0 0 / 55%);
  border-radius: 16px;
  place-items: center;
}

.logo-preview :deep(.iconify) {
  font-size: 30px;
}

.tool-title {
  min-height: 66px;
  padding: 13px 15px 14px;
  text-align: center;
}

.tool-title strong {
  display: block;
  overflow: hidden;
  font-size: 14px;
  font-weight: 900;
  color: #101828;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-title span {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  font-size: 12px;
  color: #667085;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tone-neon,
.tone-dark,
.tone-ink {
  background: #101016;
}

.tone-aurora {
  background:
    radial-gradient(circle at 34% 50%, #8957ff 0 20%, transparent 21%),
    radial-gradient(circle at 57% 55%, #5bedc5 0 20%, transparent 21%),
    #11131d;
}

.tone-blue {
  background: linear-gradient(135deg, #4076ff, #0b246a);
}

.tone-rainbow {
  background: conic-gradient(from 30deg, #5b7cfa, #30d5a1, #ffd04d, #ff5f6d, #5b7cfa);
}

.tone-photo {
  background: linear-gradient(135deg, #edf7ff, #ffece9);
}

.tone-market {
  background: linear-gradient(135deg, #fdebd1, #e3f1ff);
}

.tone-stage {
  background: linear-gradient(135deg, #101016, #3a255d);
}

.tone-soft {
  background: linear-gradient(135deg, #eef7ff, #fff1f7);
}

.tone-studio {
  background: linear-gradient(135deg, #1b2140, #6c4fee);
}

.tone-ice {
  background: linear-gradient(135deg, #dff6ff, #9dd9ff);
}

.people-preview {
  display: flex;
  width: 100%;
  height: 100%;
}

.people-preview span {
  flex: 1;
  background:
    radial-gradient(circle at 50% 34%, #f6c6a4 0 12%, transparent 13%),
    linear-gradient(180deg, transparent 45%, rgb(255 255 255 / 55%) 46% 100%);
  border-right: 1px solid rgb(255 255 255 / 72%);
}

.commerce-preview {
  position: relative;
  display: grid;
  width: 96px;
  height: 74px;
  background: rgb(255 255 255 / 52%);
  border-radius: 18px;
  place-items: center;
}

.commerce-preview :deep(.iconify) {
  font-size: 34px;
  color: #6b4ee6;
}

.commerce-preview span {
  position: absolute;
  width: 9px;
  height: 9px;
  background: #7bd88f;
  border-radius: 50%;
}

.commerce-preview span:nth-child(2) {
  top: 13px;
  left: 15px;
}

.commerce-preview span:nth-child(3) {
  right: 16px;
  bottom: 14px;
  background: #ffd166;
}

.avatar-preview {
  display: grid;
  justify-items: center;
}

.avatar-head {
  width: 44px;
  height: 44px;
  background: #ffd2b8;
  border-radius: 50%;
}

.avatar-body {
  width: 82px;
  height: 46px;
  margin-top: -4px;
  background: linear-gradient(135deg, #fff, #aeb8ff);
  border-radius: 24px 24px 12px 12px;
}

.floating-help {
  position: fixed;
  right: 30px;
  bottom: 28px;
  z-index: 8;
  width: 56px;
  height: 56px;
  color: #7a61e8;
  cursor: pointer;
  background: #d4c5ff;
  border: 0;
  border-radius: 50%;
  box-shadow: 0 12px 22px rgb(54 46 84 / 24%);
}

.floating-help :deep(.iconify) {
  font-size: 24px;
}

@media (width <= 1180px) {
  .tool-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .control-row {
    flex-wrap: wrap;
  }

  .generate-button {
    margin-left: 0;
  }
}

@media (width <= 760px) {
  .creative-workshop {
    padding: 18px 14px 28px;
  }

  .hero-panel {
    grid-template-columns: 1fr;
  }

  .hero-orbit {
    display: none;
  }

  .hero-copy h1 {
    font-size: 25px;
  }

  .prompt-panel {
    padding: 20px;
  }

  .control-select,
  .mode-select,
  .model-select,
  .generate-button,
  .quick-controls {
    width: 100%;
  }

  .quick-controls {
    justify-content: space-between;
  }

  .tabs-row {
    display: flex;
    overflow-x: auto;
    margin-right: 0;
    margin-left: 0;
  }

  .tab-button {
    flex: 0 0 auto;
  }

  .tool-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
}
</style>
