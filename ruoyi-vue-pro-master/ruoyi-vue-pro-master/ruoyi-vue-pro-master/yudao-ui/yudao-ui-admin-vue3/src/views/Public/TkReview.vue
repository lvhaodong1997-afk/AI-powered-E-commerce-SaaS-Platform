<template>
  <main class="public-home-page">
    <nav class="topbar" aria-label="Primary navigation">
      <a class="brand" href="#top" aria-label="ClipForge Studio home">
        <img class="brand-mark" src="/clipforge-app-icon.png?v=20260728-system-icon" alt="" />
        <span>ClipForge Studio</span>
      </a>
      <div class="nav-links">
        <a href="#product">Product</a>
        <a href="#integration">TikTok Integration</a>
        <a href="#controls">Controls</a>
        <a href="#support">Support</a>
        <RouterLink to="/privacy-policy">Privacy</RouterLink>
        <RouterLink to="/terms-of-service">Terms</RouterLink>
        <RouterLink to="/data-deletion">Data Deletion</RouterLink>
      </div>
      <RouterLink class="demo-link" :to="demoLoginTarget">Open App</RouterLink>
    </nav>

    <section id="top" class="hero-section">
      <div class="hero-copy">
        <p class="eyebrow">Official site</p>
        <h1>AI video creation workflows for short-form commerce teams</h1>
        <p class="hero-text">
          ClipForge Studio helps creators and businesses analyze references, generate scripts,
          assemble edited videos, and prepare user-reviewed publishing tasks.
        </p>
        <p class="reviewer-note">
          This website is the public product site for ClipForge Studio, not a login page. Reviewers
          can read the product workflow, TikTok integration model, support contact, and data
          deletion instructions without signing in.
        </p>
        <div class="hero-actions">
          <a class="primary-action" href="#product">See product workflow</a>
          <RouterLink class="secondary-action" to="/privacy-policy">Read data policy</RouterLink>
        </div>
        <div class="compliance-strip" aria-label="TikTok integration permissions">
          <span>OAuth Login Kit</span>
          <span>user.info.basic</span>
          <span>video.upload</span>
          <span>video.publish only after confirmation</span>
        </div>
      </div>

      <aside class="product-proof-card" aria-label="ClipForge Studio public product summary">
        <p class="proof-kicker">Public product website</p>
        <h2>One workspace for short-form video preparation</h2>
        <p class="proof-summary">
          ClipForge Studio is a SaaS product for teams that plan scripts, organize source material,
          assemble video variations, and review output before external submission.
        </p>
        <div class="proof-list" aria-label="Product capabilities">
          <article v-for="item in proofItems" :key="item.title">
            <span>{{ item.label }}</span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </aside>
    </section>

    <section id="product" class="workflow-section">
      <div class="section-heading">
        <p class="eyebrow">Product workflow</p>
        <h2>How ClipForge Studio works</h2>
      </div>
      <div class="workflow-grid">
        <article v-for="item in productCards" :key="item.title" class="workflow-card">
          <span>{{ item.label }}</span>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
        </article>
      </div>
    </section>

    <section id="integration" class="flow-section">
      <div class="section-heading">
        <p class="eyebrow">Integration flow</p>
        <h2>Every TikTok action is user-authorized</h2>
      </div>
      <div class="flow-grid">
        <article v-for="step in flowSteps" :key="step.title" class="flow-step">
          <span>{{ step.number }}</span>
          <h3>{{ step.title }}</h3>
          <p>{{ step.description }}</p>
        </article>
      </div>
    </section>

    <section id="controls" class="details-section">
      <div class="section-heading wide-heading">
        <p class="eyebrow">Platform controls</p>
        <h2>Platform and data controls</h2>
      </div>
      <article>
        <h3>Regional availability</h3>
        <p>
          TikTok-related features are intended only for users in regions where TikTok services and
          TikTok APIs are available. The service is not designed for mainland China-only TikTok
          publishing use cases.
        </p>
      </article>
      <article>
        <h3>User confirmation</h3>
        <p>
          Upload or Direct Post actions are submitted only after the user reviews the video,
          caption, privacy, interaction, commercial, and AIGC disclosure settings.
        </p>
      </article>
      <article>
        <h3>Data use summary</h3>
        <p>
          TikTok OAuth tokens are used only for user-confirmed upload or publishing tasks and
          publishing status checks. ClipForge Studio does not publish automatically or post without
          a clear user action.
        </p>
      </article>
    </section>

    <section id="support" class="support-section">
      <div>
        <p class="eyebrow">Support and legal</p>
        <h2>Public contact for product, privacy, and platform questions</h2>
      </div>
      <div class="support-card">
        <a href="mailto:shangan@zswo.net">shangan@zswo.net</a>
        <p
          >Use this address for account help, privacy requests, TikTok integration questions, and
          access removal requests.</p
        >
      </div>
    </section>

    <footer class="public-footer">
      <span>ClipForge Studio</span>
      <div>
        <RouterLink to="/">Home</RouterLink>
        <RouterLink to="/privacy-policy">Privacy Policy</RouterLink>
        <RouterLink to="/terms-of-service">Terms of Service</RouterLink>
        <RouterLink to="/data-deletion">Data Deletion</RouterLink>
      </div>
    </footer>
  </main>
</template>

<script setup lang="ts">
defineOptions({ name: 'TkReview' })

onMounted(() => {
  document.title = 'ClipForge Studio'
})

const demoLoginTarget = {
  path: '/login',
  query: {
    redirect: '/tk/video-publish-center'
  }
}

const productCards = [
  {
    label: 'Analyze',
    title: 'Reference video analysis',
    description:
      'Import short-form references and extract reusable structure, pacing, scene notes, and copy direction.'
  },
  {
    label: 'Create',
    title: 'Script and caption drafting',
    description:
      'Generate editable scripts and captions that the user can review before production or publishing.'
  },
  {
    label: 'Assemble',
    title: 'Video material workflow',
    description:
      'Organize uploaded materials, build video variations, and preview the final result before export.'
  },
  {
    label: 'Prepare',
    title: 'Publishing preparation',
    description:
      'Connect a TikTok account, review required settings, and submit only after an explicit confirmation.'
  }
]

const proofItems = [
  {
    label: 'Plan',
    title: 'Reference and script planning',
    description:
      'Teams turn reference material into reusable structure, pacing, and copy direction.'
  },
  {
    label: 'Assemble',
    title: 'Material-based video assembly',
    description: 'Teams organize source clips and prepare edited variations for review.'
  },
  {
    label: 'Review',
    title: 'Output review workflow',
    description: 'Teams check captions, video output, and publishing details before submission.'
  }
]

const flowSteps = [
  {
    number: '01',
    title: 'Create video',
    description: 'Users generate or select a short-form video inside ClipForge Studio.'
  },
  {
    number: '02',
    title: 'Connect TikTok',
    description: 'Users authorize their own TikTok account through OAuth Login Kit.'
  },
  {
    number: '03',
    title: 'Review settings',
    description: 'Users review caption, privacy, interaction, commercial, and AIGC settings.'
  },
  {
    number: '04',
    title: 'Upload or publish',
    description: 'ClipForge Studio submits the task only after explicit user confirmation.'
  }
]
</script>

<style scoped>
.public-home-page {
  height: 100vh;
  overflow-y: auto;
  background: #f7f8fb;
  color: #111827;
  font-family:
    Inter,
    ui-sans-serif,
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    'Segoe UI',
    sans-serif;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 72px;
  padding: 0 48px;
  background: rgb(255 255 255 / 92%);
  border-bottom: 1px solid #e6e9ef;
  backdrop-filter: blur(12px);
}

.brand,
.nav-links,
.hero-actions,
.compliance-strip,
.public-footer,
.public-footer div {
  display: flex;
  align-items: center;
}

.brand {
  gap: 10px;
  color: #111827;
  font-size: 18px;
  font-weight: 800;
  text-decoration: none;
}

.brand-mark {
  display: block;
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 8px;
  box-shadow: 0 8px 20px rgb(15 23 42 / 18%);
}

.nav-links {
  gap: 28px;
}

.nav-links a,
.public-footer a {
  color: #4b5563;
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}

.nav-links a:hover,
.public-footer a:hover {
  color: #111827;
}

.demo-link,
.primary-action,
.secondary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 18px;
  font-size: 14px;
  font-weight: 750;
  text-decoration: none;
  border-radius: 8px;
}

.demo-link,
.primary-action {
  color: #fff;
  background: #111827;
}

.secondary-action {
  color: #111827;
  background: #fff;
  border: 1px solid #d8dde8;
}

.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(420px, 520px);
  gap: 64px;
  max-width: 1240px;
  min-height: calc(100vh - 190px);
  padding: 54px 48px 30px;
  margin: 0 auto;
  align-items: center;
}

.hero-copy {
  max-width: 650px;
}

.eyebrow {
  margin: 0 0 14px;
  color: #e5365c;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.11em;
  text-transform: uppercase;
}

h1,
h2,
h3,
p {
  margin: 0;
}

h1 {
  max-width: 720px;
  font-size: 58px;
  font-weight: 860;
  line-height: 1.02;
}

.hero-text {
  max-width: 610px;
  margin-top: 24px;
  color: #4b5563;
  font-size: 19px;
  line-height: 1.7;
}

.reviewer-note {
  max-width: 610px;
  margin-top: 18px;
  padding: 16px 18px;
  color: #334155;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.65;
  background: #fff;
  border: 1px solid #dce3ee;
  border-radius: 8px;
}

.hero-actions {
  gap: 12px;
  margin-top: 32px;
  flex-wrap: wrap;
}

.compliance-strip {
  gap: 8px;
  margin-top: 34px;
  flex-wrap: wrap;
}

.compliance-strip span {
  padding: 8px 12px;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 999px;
}

.product-proof-card {
  padding: 30px;
  background: linear-gradient(180deg, #fff 0%, #f8fafc 100%), #fff;
  border: 1px solid #dce2eb;
  border-radius: 8px;
  box-shadow: 0 24px 70px rgb(17 24 39 / 11%);
}

.proof-kicker,
.proof-list span {
  color: #e5365c;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.product-proof-card h2 {
  max-width: 430px;
  margin-top: 12px;
  color: #111827;
  font-size: 30px;
  line-height: 1.16;
}

.proof-summary {
  margin-top: 18px;
  color: #4b5563;
  font-size: 15px;
  line-height: 1.75;
}

.proof-list {
  display: grid;
  gap: 12px;
  margin-top: 26px;
}

.proof-list article {
  padding: 16px;
  background: #fff;
  border: 1px solid #e3e8f0;
  border-radius: 8px;
}

.proof-list strong {
  display: block;
  margin-top: 8px;
  color: #111827;
  font-size: 15px;
}

.proof-list p,
.flow-step p,
.details-section p {
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.flow-section,
.workflow-section,
.details-section,
.support-section,
.public-footer {
  max-width: 1240px;
  margin: 0 auto;
  padding-inline: 48px;
}

.workflow-section {
  padding-top: 32px;
  padding-bottom: 34px;
}

.flow-section {
  padding-top: 24px;
  padding-bottom: 68px;
}

.section-heading {
  margin-bottom: 24px;
}

.section-heading h2,
.details-section h2 {
  color: #111827;
  font-size: 30px;
  line-height: 1.2;
}

.flow-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.workflow-card {
  min-height: 210px;
  padding: 22px;
  background: linear-gradient(180deg, #fff 0%, #f8fafc 100%);
  border: 1px solid #e2e7ef;
  border-radius: 8px;
}

.workflow-card span {
  color: #e5365c;
  font-size: 12px;
  font-weight: 850;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workflow-card h3 {
  margin: 42px 0 10px;
  font-size: 19px;
  line-height: 1.25;
}

.workflow-card p {
  color: #667085;
  font-size: 13px;
  line-height: 1.65;
}

.flow-step,
.details-section article {
  padding: 22px;
  background: #fff;
  border: 1px solid #e2e7ef;
  border-radius: 8px;
}

.flow-step span {
  color: #e5365c;
  font-size: 13px;
  font-weight: 850;
}

.flow-step h3 {
  margin: 14px 0 8px;
  font-size: 18px;
}

.details-section {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding-bottom: 64px;
}

.wide-heading {
  grid-column: 1 / -1;
  margin-bottom: 10px;
}

.details-section h3 {
  margin-bottom: 12px;
  font-size: 21px;
}

.support-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 440px);
  gap: 28px;
  align-items: center;
  padding-top: 4px;
  padding-bottom: 56px;
}

.support-section h2 {
  max-width: 640px;
  color: #111827;
  font-size: 30px;
  line-height: 1.2;
}

.support-card {
  padding: 22px;
  background: #fff;
  border: 1px solid #e2e7ef;
  border-radius: 8px;
}

.support-card a {
  color: #111827;
  font-size: 18px;
  font-weight: 850;
  text-decoration: none;
}

.support-card p {
  margin-top: 12px;
  color: #667085;
  font-size: 13px;
  line-height: 1.65;
}

.public-footer {
  justify-content: space-between;
  min-height: 86px;
  color: #667085;
  border-top: 1px solid #e1e6ef;
}

.public-footer span {
  font-weight: 800;
}

.public-footer div {
  gap: 20px;
}

@media (max-width: 980px) {
  .topbar {
    padding: 0 24px;
  }

  .nav-links {
    display: none;
  }

  .hero-section {
    grid-template-columns: 1fr;
    gap: 34px;
    min-height: auto;
    padding: 48px 24px;
  }

  h1 {
    font-size: 46px;
  }

  .flow-section,
  .workflow-section,
  .details-section,
  .support-section,
  .public-footer {
    padding-inline: 24px;
  }

  .flow-grid,
  .workflow-grid,
  .details-section {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .support-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .topbar {
    min-height: 64px;
    padding: 0 16px;
  }

  .brand {
    font-size: 16px;
  }

  .demo-link {
    min-height: 38px;
    padding: 0 12px;
  }

  .hero-section {
    padding: 36px 16px;
  }

  h1 {
    font-size: 36px;
  }

  .hero-text {
    font-size: 16px;
  }

  .public-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .flow-grid,
  .workflow-grid,
  .details-section {
    grid-template-columns: 1fr;
  }

  .flow-section,
  .workflow-section,
  .details-section,
  .support-section,
  .public-footer {
    padding-inline: 16px;
  }
}
</style>
