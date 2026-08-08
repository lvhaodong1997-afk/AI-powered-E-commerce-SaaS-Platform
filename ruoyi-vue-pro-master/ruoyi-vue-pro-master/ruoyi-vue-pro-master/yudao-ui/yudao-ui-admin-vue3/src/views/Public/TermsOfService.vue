<template>
  <main class="public-legal-page">
    <section class="legal-shell">
      <p class="eyebrow">ClipForge Studio</p>
      <h1>Terms of Service</h1>
      <p class="updated">Last updated: July 2, 2026</p>

      <article>
        <h2>1. Service Description</h2>
        <p>
          ClipForge Studio provides tools for short-form video analysis, script generation, video
          editing, preview, and user-confirmed publishing to connected TikTok accounts. The service
          is intended for creators and businesses that manage their own video content.
        </p>

        <h2>2. TikTok Service Availability</h2>
        <p>
          TikTok-related features are intended only for users in regions where TikTok services and
          TikTok APIs are available. ClipForge Studio does not provide TikTok publishing
          functionality for mainland China-only use cases.
        </p>

        <h2>3. User Responsibilities</h2>
        <p>
          Users are responsible for the content they upload, generate, edit, and publish. Users must
          have all rights, permissions, licenses, and approvals required for submitted videos,
          images, music, product claims, captions, trademarks, and commercial disclosures.
        </p>

        <h2>4. TikTok Publishing</h2>
        <p>
          TikTok publishing requires the user to connect their own TikTok account through TikTok
          OAuth. ClipForge Studio only initiates upload or Direct Post actions after the user
          reviews the video, caption, privacy options, interaction settings, branded content
          settings, commercial content settings, AI-generated content disclosure, and confirms the
          publish action.
        </p>

        <h2>5. Prohibited Use</h2>
        <p>
          Users may not use ClipForge Studio for spam, impersonation, deceptive activity,
          unauthorized scraping, mass posting without user review, engagement manipulation, illegal
          content, harmful content, content that violates TikTok Community Guidelines, or content
          that infringes third-party rights.
        </p>

        <h2>6. AI-Generated Content</h2>
        <p>
          ClipForge Studio may assist with AI-generated scripts, captions, edits, or creative
          variations. Users are responsible for reviewing AI-assisted output and selecting the
          correct AI-generated content disclosure before publishing.
        </p>

        <h2>7. Availability and Changes</h2>
        <p>
          We may update, limit, suspend, or discontinue parts of the service when needed for
          maintenance, security, policy compliance, infrastructure reliability, or third-party API
          requirements.
        </p>

        <h2>8. Account and Access</h2>
        <p>
          Users must keep login credentials secure and may only connect TikTok accounts they are
          authorized to manage. We may suspend access if the service is used in a way that creates
          security, legal, policy, or platform compliance risk.
        </p>

        <h2>9. Limitation of Liability</h2>
        <p>
          The service is provided as a content creation and publishing tool. Users remain
          responsible for final content review, publishing choices, platform compliance, and the
          outcome of posts submitted to TikTok or other platforms.
        </p>

        <h2>10. Data Deletion and Contact</h2>
        <p>
          For service questions, account removal, publishing issues, data deletion requests, or
          policy concerns, contact ClipForge Studio support at
          <a href="mailto:shangan@zswo.net">shangan@zswo.net</a>.
        </p>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
defineOptions({ name: 'TermsOfService' })
</script>

<style scoped>
.public-legal-page {
  min-height: 100vh;
  padding: 48px 20px;
  background: #f6f7fb;
  color: #172033;
}

.legal-shell {
  max-width: 920px;
  margin: 0 auto;
  padding: 44px;
  background: #fff;
  border: 1px solid #e3e7ef;
  border-radius: 8px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #4661a6;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 36px;
  line-height: 1.2;
}

.updated {
  margin: 12px 0 34px;
  color: #6d7482;
}

h2 {
  margin: 28px 0 10px;
  font-size: 20px;
}

p {
  margin: 0;
  color: #364052;
  font-size: 15px;
  line-height: 1.75;
}

a {
  color: #2454a6;
  font-weight: 700;
}

@media (max-width: 640px) {
  .public-legal-page {
    padding: 20px 12px;
  }

  .legal-shell {
    padding: 28px 20px;
  }

  h1 {
    font-size: 30px;
  }
}
</style>
