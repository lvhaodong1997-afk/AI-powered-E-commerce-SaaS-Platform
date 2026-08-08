<template>
  <main class="public-legal-page">
    <section class="legal-shell">
      <p class="eyebrow">ClipForge Studio</p>
      <h1>Privacy Policy</h1>
      <p class="updated">Last updated: July 2, 2026</p>

      <article>
        <h2>1. Overview</h2>
        <p>
          ClipForge Studio is a web-based video creation and publishing tool for creators and
          businesses. This Privacy Policy explains how we collect, use, store, and protect
          information when users create edited videos and connect their TikTok account for
          publishing.
        </p>

        <h2>2. Information We Collect</h2>
        <p>
          We collect account information needed to operate the service, including login identity,
          company workspace settings, uploaded media, generated video assets, captions, publishing
          settings, and operational logs. When a user connects TikTok, we store TikTok account
          identifiers, display information, authorization status, token status, and encrypted access
          tokens required to publish only on behalf of that authorized user.
        </p>

        <h2>3. TikTok Data and Permissions</h2>
        <p>
          We use TikTok OAuth so users can explicitly authorize access to their own TikTok account.
          Requested permissions may include user.info.basic, video.upload, and video.publish. We use
          user.info.basic to show the connected TikTok account, video.upload for upload-to-inbox
          flows, and video.publish for user-confirmed Direct Post publishing. We do not publish
          content automatically without user action.
        </p>

        <h2>4. TikTok Service Availability</h2>
        <p>
          TikTok-related features are intended only for users in regions where TikTok services and
          TikTok APIs are available. ClipForge Studio does not provide TikTok publishing
          functionality for mainland China-only use cases.
        </p>

        <h2>5. How We Use Information</h2>
        <p>
          Information is used to provide video analysis, script generation, video editing,
          publishing configuration, TikTok authorization, upload processing, publishing status
          tracking, customer support, fraud prevention, security monitoring, and service
          improvement.
        </p>

        <h2>6. User Control</h2>
        <p>
          Users can choose whether to connect TikTok, select the video to publish, review the
          caption and privacy settings, and confirm each publish action. Users may revoke TikTok
          access from TikTok account settings or request token removal from ClipForge Studio
          support.
        </p>

        <h2>7. Data Sharing</h2>
        <p>
          We do not sell personal information. We share data only with service providers needed to
          operate the product, such as infrastructure, storage, analytics, AI processing, and TikTok
          APIs when the user chooses to publish or upload content.
        </p>

        <h2>8. Security</h2>
        <p>
          TikTok tokens are encrypted at rest. Access to backend systems is limited to authorized
          operators. We use HTTPS, access control, logging, and operational monitoring to protect
          user information.
        </p>

        <h2>9. Retention and Deletion</h2>
        <p>
          We retain account, project, media, generated output, and publishing records while needed
          to provide the service, meet legal obligations, and support audit history. Users may
          request data deletion for account data, uploaded media assets, generated videos, or TikTok
          authorization records.
        </p>

        <h2>10. Contact</h2>
        <p>
          For privacy requests, account deletion, TikTok authorization removal, or security
          questions, contact ClipForge Studio support at
          <a href="mailto:shangan@zswo.net">shangan@zswo.net</a>.
        </p>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
defineOptions({ name: 'PrivacyPolicy' })
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
