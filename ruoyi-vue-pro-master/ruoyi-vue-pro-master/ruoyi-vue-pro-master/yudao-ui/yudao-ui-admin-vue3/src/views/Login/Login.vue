<template>
  <div :class="[prefixCls, 'tk-login']">
    <div class="tk-login__grid"></div>
    <div class="tk-login__beam tk-login__beam--one"></div>
    <div class="tk-login__beam tk-login__beam--two"></div>
    <div class="tk-login__topbar">
      <div class="tk-login__brand">
        <img alt="" class="tk-login__logo" src="@/assets/imgs/logo.png" />
        <div>
          <div class="tk-login__name">{{ underlineToHump(appStore.getTitle) }}</div>
          <div class="tk-login__caption">AI MIXING MATERIAL OS</div>
        </div>
      </div>
      <div class="tk-login__tools">
        <ThemeSwitch />
        <LocaleDropdown />
      </div>
    </div>

    <div class="tk-login__main">
      <section class="tk-login__showcase">
        <TransitionGroup
          appear
          enter-active-class="animate__animated animate__fadeInLeft"
          tag="div"
          class="tk-login__showcase-inner"
        >
          <div key="eyebrow" class="tk-login__eyebrow">TK AUTO MIX ENGINE</div>
          <h1 key="title" class="tk-login__hero-title">{{ loginCopy.heroTitle }}</h1>
          <p key="copy" class="tk-login__hero-copy">
            {{ loginCopy.heroCopy }}
          </p>
          <div key="chips" class="tk-login__chips">
            <span v-for="chip in loginCopy.chips" :key="chip">{{ chip }}</span>
          </div>
          <div key="visual" class="tk-login__visual">
            <div class="tk-login__visual-core">
              <div class="tk-login__core-ring tk-login__core-ring--outer"></div>
              <div class="tk-login__core-ring tk-login__core-ring--inner"></div>
              <img alt="" class="tk-login__core-logo" src="@/assets/imgs/logo.png" />
            </div>
            <div class="tk-login__node tk-login__node--a">
              <span>URL</span>
              <strong>Link Parse</strong>
            </div>
            <div class="tk-login__node tk-login__node--b">
              <span>AI</span>
              <strong>Scene Cut</strong>
            </div>
            <div class="tk-login__node tk-login__node--c">
              <span>Queue</span>
              <strong>Render</strong>
            </div>
            <div class="tk-login__timeline">
              <i></i>
              <i></i>
              <i></i>
              <i></i>
              <i></i>
              <i></i>
            </div>
          </div>
          <div key="metrics" class="tk-login__metrics">
            <div v-for="metric in loginCopy.metrics" :key="metric.label">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </div>
          </div>
        </TransitionGroup>
      </section>

      <section class="tk-login__panel-wrap">
        <Transition appear enter-active-class="animate__animated animate__fadeInRight">
          <div class="tk-login__panel">
            <div class="tk-login__panel-header">
              <div>
                <div class="tk-login__panel-kicker">{{ loginCopy.panelKicker }}</div>
                <h2>{{ loginCopy.panelTitle }}</h2>
              </div>
              <span class="tk-login__status">{{ loginCopy.status }}</span>
            </div>
            <div class="tk-login__panel-body">
              <LoginForm class="tk-login__form" />
              <SSOLoginVue class="tk-login__form tk-login__form--sso" />
              <ForgetPasswordForm class="tk-login__form" />
            </div>
            <div class="tk-login__panel-footer">
              <span v-for="item in loginCopy.footer" :key="item">{{ item }}</span>
            </div>
            <div class="tk-login__legal-links">
              <a href="/privacy-policy" target="_blank" rel="noopener noreferrer">Privacy Policy</a>
              <i></i>
              <a href="/terms-of-service" target="_blank" rel="noopener noreferrer">Terms of Service</a>
            </div>
          </div>
        </Transition>
      </section>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { underlineToHump } from '@/utils'

import { useDesign } from '@/hooks/web/useDesign'
import { useAppStore } from '@/store/modules/app'
import { useLocaleStore } from '@/store/modules/locale'
import { ThemeSwitch } from '@/layout/components/ThemeSwitch'
import { LocaleDropdown } from '@/layout/components/LocaleDropdown'

import { LoginForm, SSOLoginVue, ForgetPasswordForm } from './components'

defineOptions({ name: 'Login' })

const appStore = useAppStore()
const localeStore = useLocaleStore()
const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('login')
const currentLocale = computed(() => localeStore.getCurrentLocale.lang)
const loginCopy = computed(() => {
  if (currentLocale.value === 'en') {
    return {
      heroTitle: 'AI Remix Material Production Platform',
      heroCopy:
        'Bring material capture, intelligent analysis, batch generation, and publishing review into one controllable workflow.',
      chips: ['Material sync', 'Script analysis', 'Generation queue'],
      metrics: [
        { label: 'Materials processed', value: '24K+' },
        { label: 'Remix pipeline', value: '8ms' },
        { label: 'Engine status', value: 'Online' }
      ],
      panelKicker: 'SECURE ACCESS',
      panelTitle: 'Workspace Login',
      status: 'Secure connection',
      footer: ['AI material engine', 'Permission check', 'Queue guard']
    }
  }

  return {
    heroTitle: 'AI 混剪素材生产平台',
    heroCopy: '让素材抓取、智能分析、批量生成和投放复盘进入同一条可控流水线。',
    chips: ['素材库同步', '脚本分析', '生成队列'],
    metrics: [
      { label: '素材处理', value: '24K+' },
      { label: '混剪链路', value: '8ms' },
      { label: '引擎状态', value: 'Online' }
    ],
    panelKicker: 'SECURE ACCESS',
    panelTitle: '工作台登录',
    status: '安全连接',
    footer: ['AI 素材引擎', '权限验证', '队列守护']
  }
})
</script>

<style lang="scss" scoped>
$prefix-cls: #{$namespace}-login;

.#{$prefix-cls} {
  position: relative;
  min-height: 100%;
  overflow: auto;
  color: #f8fbff;
  background:
    radial-gradient(circle at 14% 18%, rgba(0, 201, 255, 0.22), transparent 28%),
    radial-gradient(circle at 76% 22%, rgba(119, 83, 255, 0.2), transparent 26%),
    radial-gradient(circle at 50% 96%, rgba(0, 87, 255, 0.18), transparent 34%),
    linear-gradient(135deg, #020711 0%, #06142c 47%, #020611 100%);

  &::before,
  &::after {
    position: absolute;
    content: '';
    pointer-events: none;
  }

  &::before {
    inset: 0;
    background:
      linear-gradient(rgba(92, 216, 255, 0.06) 1px, transparent 1px),
      linear-gradient(90deg, rgba(92, 216, 255, 0.06) 1px, transparent 1px);
    background-size: 46px 46px;
    mask-image: linear-gradient(to bottom, transparent, #000 18%, #000 72%, transparent);
  }

  &::after {
    right: -16%;
    bottom: -30%;
    width: 82%;
    height: 58%;
    background:
      linear-gradient(90deg, rgba(58, 218, 255, 0.2), transparent 26%),
      repeating-linear-gradient(90deg, rgba(93, 226, 255, 0.18) 0 1px, transparent 1px 78px);
    transform: perspective(760px) rotateX(62deg) rotateZ(-6deg);
    opacity: 0.74;
  }
}

.tk-login {
  &,
  * {
    box-sizing: border-box;
  }

  &__grid,
  &__beam {
    position: absolute;
    pointer-events: none;
  }

  &__grid {
    inset: 0;
    background:
      radial-gradient(circle at 28% 50%, rgba(44, 214, 255, 0.12), transparent 24%),
      linear-gradient(115deg, transparent 0 48%, rgba(63, 216, 255, 0.16) 49%, transparent 50%);
    opacity: 0.84;
  }

  &__beam {
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(77, 225, 255, 0.78), transparent);
    filter: drop-shadow(0 0 10px rgba(77, 225, 255, 0.48));
    opacity: 0.74;

    &--one {
      top: 19%;
      left: -10%;
      width: 54%;
      transform: rotate(-10deg);
    }

    &--two {
      right: -8%;
      bottom: 22%;
      width: 50%;
      transform: rotate(14deg);
    }
  }

  &__topbar {
    position: relative;
    z-index: 2;
    display: flex;
    height: 76px;
    padding: 22px 44px 0;
    align-items: center;
    justify-content: space-between;
  }

  &__brand {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  &__logo {
    width: 46px;
    height: 46px;
    border-radius: 8px;
    box-shadow:
      0 0 0 1px rgba(113, 226, 255, 0.24),
      0 0 24px rgba(54, 180, 255, 0.35);
  }

  &__name {
    font-size: 18px;
    font-weight: 700;
    line-height: 1.2;
  }

  &__caption {
    margin-top: 3px;
    color: rgba(178, 226, 255, 0.6);
    font-size: 11px;
    font-weight: 600;
  }

  &__tools {
    display: flex;
    height: 42px;
    padding: 0 10px;
    align-items: center;
    gap: 10px;
    border: 1px solid rgba(115, 223, 255, 0.18);
    border-radius: 999px;
    background: rgba(8, 25, 48, 0.52);
    backdrop-filter: blur(16px);
  }

  &__main {
    position: relative;
    z-index: 1;
    display: grid;
    min-height: calc(100vh - 76px);
    padding: 26px 58px 58px;
    align-items: center;
    gap: 52px;
    box-sizing: border-box;
    grid-template-columns: minmax(0, 1fr) minmax(380px, 460px);
  }

  &__showcase {
    min-width: 0;
  }

  &__showcase-inner {
    position: relative;
    display: flex;
    min-height: min(620px, calc(100vh - 160px));
    flex-direction: column;
    justify-content: center;
  }

  &__eyebrow {
    width: fit-content;
    padding: 7px 12px;
    border: 1px solid rgba(101, 224, 255, 0.26);
    border-radius: 999px;
    color: #7ce9ff;
    background: rgba(9, 35, 66, 0.58);
    font-size: 12px;
    font-weight: 700;
  }

  &__hero-title {
    max-width: 640px;
    margin: 22px 0 0;
    color: #f8fbff;
    font-size: clamp(38px, 5vw, 70px);
    font-weight: 800;
    line-height: 1.05;
  }

  &__hero-copy {
    max-width: 570px;
    margin: 22px 0 0;
    color: rgba(213, 235, 255, 0.72);
    font-size: 17px;
    line-height: 1.9;
  }

  &__chips {
    display: flex;
    margin-top: 26px;
    gap: 12px;
    flex-wrap: wrap;

    span {
      padding: 9px 14px;
      border: 1px solid rgba(107, 219, 255, 0.22);
      border-radius: 999px;
      color: rgba(226, 247, 255, 0.78);
      background: rgba(8, 28, 56, 0.62);
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
      font-size: 13px;
    }
  }

  &__visual {
    position: relative;
    width: min(620px, 100%);
    min-height: 300px;
    margin-top: 48px;
  }

  &__visual-core {
    position: absolute;
    top: 40px;
    left: 50%;
    display: grid;
    width: 172px;
    height: 172px;
    place-items: center;
    transform: translateX(-50%);
  }

  &__core-ring,
  &__core-logo {
    position: absolute;
  }

  &__core-ring {
    border-radius: 50%;

    &--outer {
      inset: 0;
      border: 1px solid rgba(72, 220, 255, 0.54);
      box-shadow:
        0 0 48px rgba(34, 198, 255, 0.22),
        inset 0 0 30px rgba(81, 85, 255, 0.12);
      animation: orbit 9s linear infinite;

      &::before,
      &::after {
        position: absolute;
        width: 9px;
        height: 9px;
        border-radius: 50%;
        background: #69e8ff;
        box-shadow: 0 0 18px #69e8ff;
        content: '';
      }

      &::before {
        top: 12px;
        left: 34px;
      }

      &::after {
        right: 18px;
        bottom: 28px;
      }
    }

    &--inner {
      inset: 29px;
      border: 1px dashed rgba(134, 108, 255, 0.62);
      animation: orbit 12s linear infinite reverse;
    }
  }

  &__core-logo {
    width: 80px;
    height: 80px;
    border-radius: 8px;
    box-shadow:
      0 0 0 1px rgba(116, 228, 255, 0.26),
      0 0 34px rgba(73, 172, 255, 0.38);
  }

  &__node {
    position: absolute;
    min-width: 146px;
    padding: 13px 15px;
    border: 1px solid rgba(105, 222, 255, 0.22);
    border-radius: 8px;
    background: linear-gradient(145deg, rgba(15, 44, 79, 0.82), rgba(7, 17, 37, 0.72));
    box-shadow:
      0 18px 44px rgba(0, 0, 0, 0.28),
      inset 0 1px 0 rgba(255, 255, 255, 0.1);
    backdrop-filter: blur(16px);

    span {
      display: block;
      color: #6ee8ff;
      font-size: 11px;
      font-weight: 700;
    }

    strong {
      display: block;
      margin-top: 5px;
      color: rgba(247, 252, 255, 0.92);
      font-size: 16px;
    }

    &--a {
      top: 12px;
      left: 4%;
    }

    &--b {
      top: 112px;
      right: 5%;
    }

    &--c {
      bottom: 24px;
      left: 13%;
    }
  }

  &__timeline {
    position: absolute;
    right: 0;
    bottom: 8px;
    display: grid;
    width: min(390px, 64%);
    padding: 16px;
    gap: 10px;
    grid-template-columns: repeat(6, 1fr);
    border: 1px solid rgba(105, 222, 255, 0.16);
    border-radius: 8px;
    background: rgba(5, 19, 39, 0.54);

    i {
      display: block;
      height: 66px;
      border-radius: 6px;
      background:
        linear-gradient(180deg, rgba(93, 235, 255, 0.32), rgba(123, 90, 255, 0.12)),
        linear-gradient(135deg, transparent 0 54%, rgba(255, 255, 255, 0.18) 55%, transparent 56%);
      box-shadow: inset 0 0 0 1px rgba(180, 237, 255, 0.1);

      &:nth-child(even) {
        transform: translateY(12px);
      }
    }
  }

  &__metrics {
    display: grid;
    max-width: 640px;
    margin-top: 34px;
    gap: 12px;
    grid-template-columns: repeat(3, minmax(0, 1fr));

    div {
      padding: 16px;
      border: 1px solid rgba(109, 221, 255, 0.16);
      border-radius: 8px;
      background: rgba(6, 21, 43, 0.54);
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
    }

    span {
      display: block;
      color: rgba(196, 227, 249, 0.62);
      font-size: 12px;
    }

    strong {
      display: block;
      margin-top: 8px;
      color: #f8fbff;
      font-size: 24px;
    }
  }

  &__panel-wrap {
    display: flex;
    justify-content: flex-end;
  }

  &__panel {
    position: relative;
    width: 100%;
    max-width: 460px;
    max-height: calc(100vh - 126px);
    padding: 26px;
    overflow-x: hidden;
    overflow-y: auto;
    border: 1px solid rgba(123, 224, 255, 0.24);
    border-radius: 8px;
    background:
      linear-gradient(150deg, rgba(13, 31, 60, 0.88), rgba(4, 11, 28, 0.84)),
      radial-gradient(circle at 18% 0%, rgba(73, 209, 255, 0.24), transparent 34%);
    box-shadow:
      0 34px 90px rgba(0, 0, 0, 0.42),
      0 0 60px rgba(0, 172, 255, 0.16),
      inset 0 1px 0 rgba(255, 255, 255, 0.14);
    backdrop-filter: blur(22px);
    scrollbar-color: rgba(104, 221, 255, 0.36) transparent;
    scrollbar-width: thin;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: 999px;
      background: rgba(104, 221, 255, 0.32);
    }

    &::before,
    &::after {
      position: absolute;
      width: 68px;
      height: 68px;
      border-color: rgba(108, 229, 255, 0.52);
      content: '';
      pointer-events: none;
    }

    &::before {
      top: 14px;
      left: 14px;
      border-top: 1px solid;
      border-left: 1px solid;
    }

    &::after {
      right: 14px;
      bottom: 14px;
      border-right: 1px solid;
      border-bottom: 1px solid;
    }
  }

  &__panel-header {
    position: relative;
    z-index: 1;
    display: flex;
    margin-bottom: 24px;
    align-items: flex-start;
    justify-content: space-between;
    gap: 18px;

    h2 {
      margin: 6px 0 0;
      color: #fff;
      font-size: 26px;
      font-weight: 800;
      line-height: 1.25;
    }
  }

  &__panel-kicker {
    color: #6ee8ff;
    font-size: 12px;
    font-weight: 800;
  }

  &__status {
    position: relative;
    flex: 0 0 auto;
    padding: 8px 12px 8px 25px;
    border: 1px solid rgba(45, 255, 173, 0.22);
    border-radius: 999px;
    color: rgba(212, 255, 238, 0.86);
    background: rgba(8, 60, 47, 0.34);
    font-size: 12px;

    &::before {
      position: absolute;
      top: 50%;
      left: 11px;
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #35ffb0;
      box-shadow: 0 0 12px #35ffb0;
      transform: translateY(-50%);
      content: '';
    }
  }

  &__panel-body {
    position: relative;
    z-index: 1;
  }

  &__form {
    width: 100%;
  }

  &__panel-footer {
    position: relative;
    z-index: 1;
    display: flex;
    margin-top: 20px;
    gap: 9px;
    flex-wrap: wrap;

    span {
      padding: 7px 10px;
      border: 1px solid rgba(104, 221, 255, 0.18);
      border-radius: 999px;
      color: rgba(206, 235, 255, 0.68);
      background: rgba(8, 27, 52, 0.56);
      font-size: 12px;
    }
  }

  &__legal-links {
    position: relative;
    z-index: 1;
    display: flex;
    margin-top: 16px;
    align-items: center;
    justify-content: center;
    gap: 12px;
    color: rgba(206, 235, 255, 0.68);
    font-size: 12px;

    a {
      color: rgba(218, 243, 255, 0.82);
      text-decoration: none;
      transition:
        color 0.18s ease,
        text-shadow 0.18s ease;

      &:hover {
        color: #6ee8ff;
        text-shadow: 0 0 14px rgba(110, 232, 255, 0.38);
      }
    }

    i {
      width: 1px;
      height: 12px;
      background: rgba(109, 221, 255, 0.26);
    }
  }
}

@keyframes orbit {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1180px) {
  .tk-login {
    &__main {
      grid-template-columns: minmax(0, 1fr);
      gap: 28px;
    }

    &__showcase-inner {
      min-height: auto;
      padding-top: 20px;
    }

    &__visual,
    &__metrics {
      display: none;
    }

    &__panel-wrap {
      justify-content: center;
    }
  }
}

@media (max-width: 720px) {
  .tk-login {
    &__topbar {
      height: auto;
      padding: 18px 18px 0;
      gap: 14px;
    }

    &__caption {
      display: none;
    }

    &__main {
      min-height: auto;
      padding: 28px 18px 32px;
    }

    &__showcase {
      display: none;
    }

    &__panel {
      max-height: none;
      overflow: visible;
      padding: 22px 18px;
    }

    &__panel-header {
      flex-direction: column;
    }
  }
}
</style>

<style lang="scss">
.tk-login {
  --el-color-primary: #20d8ff;
  --el-color-primary-light-3: #5be8ff;
  --el-color-primary-light-5: #79efff;
  --el-color-primary-light-7: rgba(32, 216, 255, 0.28);
  --el-color-primary-light-8: rgba(32, 216, 255, 0.18);
  --el-color-primary-light-9: rgba(32, 216, 255, 0.1);
  --el-color-primary-dark-2: #13aee0;
  --el-fill-color-blank: rgba(7, 20, 42, 0.78);
  --el-fill-color-light: rgba(11, 35, 65, 0.8);
  --el-bg-color-overlay: #08152b;
  --el-border-color: rgba(109, 221, 255, 0.18);
  --el-border-color-light: rgba(109, 221, 255, 0.18);
  --el-text-color-primary: #f8fbff;
  --el-text-color-regular: rgba(223, 238, 255, 0.78);
  --el-text-color-placeholder: rgba(188, 216, 238, 0.48);

  .login-form,
  .form-cont {
    color: rgba(232, 246, 255, 0.86);
  }

  .login-form h2 {
    margin-bottom: 18px !important;
    color: #f8fbff;
    font-size: 28px;
  }

  .login-form .el-input__wrapper {
    min-height: 46px;
    border: 1px solid rgba(105, 222, 255, 0.18);
    border-radius: 8px;
    background:
      linear-gradient(180deg, rgba(14, 37, 69, 0.76), rgba(6, 17, 37, 0.7)), rgba(7, 20, 42, 0.72);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.08),
      0 0 0 0 rgba(32, 216, 255, 0);
  }

  .login-form .el-input__wrapper.is-focus {
    border-color: rgba(78, 226, 255, 0.7);
    box-shadow:
      0 0 0 3px rgba(32, 216, 255, 0.12),
      0 0 24px rgba(32, 216, 255, 0.12);
  }

  .login-form .el-input__inner {
    color: #f8fbff;
  }

  .login-form .el-form-item {
    margin-bottom: 22px;
  }

  .login-form .el-checkbox,
  .login-form .el-link {
    color: rgba(216, 238, 255, 0.76);
  }

  .login-form .el-checkbox__inner {
    border-color: rgba(111, 224, 255, 0.36);
    background: rgba(4, 14, 31, 0.58);
  }

  .login-form .el-button {
    min-height: 46px;
    border-radius: 8px;
    font-weight: 700;
  }

  .login-form .el-button--primary {
    border: 0;
    background: linear-gradient(135deg, #19d3ff 0%, #376dff 54%, #8a5cff 100%);
    box-shadow: 0 14px 30px rgba(23, 149, 255, 0.28);
  }

  .login-form .el-button:not(.el-button--primary) {
    border-color: rgba(108, 223, 255, 0.22);
    color: rgba(227, 246, 255, 0.88);
    background: rgba(8, 30, 58, 0.62);
  }

  .el-tabs__item {
    color: rgba(219, 240, 255, 0.7);
  }

  .el-tabs__item.is-active {
    color: #68e8ff;
  }

  .el-tabs__nav-wrap::after {
    background-color: rgba(109, 221, 255, 0.12);
  }
}

.dark .login-form {
  .el-divider__text {
    background-color: var(--login-bg-color);
  }

  .el-card {
    background-color: var(--login-bg-color);
  }
}
</style>
