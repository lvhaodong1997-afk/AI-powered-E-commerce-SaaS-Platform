<template>
  <main class="public-legal-page">
    <section class="legal-shell">
      <p class="eyebrow">ClipForge Studio</p>
      <h1>Data Deletion</h1>
      <p class="updated">Last updated: August 6, 2026</p>

      <article>
        <h2>How to request deletion</h2>
        <p>
          Users can request deletion of ClipForge Studio account data, uploaded media, generated
          videos, captions, project records, and TikTok authorization records by emailing
          <a href="mailto:shangan@zswo.net">shangan@zswo.net</a>.
        </p>

        <h2>What to include</h2>
        <p>
          Use the subject line ClipForge Studio Data Deletion Request. Include the account email,
          workspace name if available, and the type of data to delete. Do not include passwords or
          payment information in the request.
        </p>

        <h2>TikTok authorization removal</h2>
        <p>
          Users may revoke TikTok access from TikTok account settings. Users may also request
          removal of TikTok authorization records stored by ClipForge Studio through the same
          support email.
        </p>

        <h2>Processing</h2>
        <p>
          ClipForge Studio will review deletion requests, verify account ownership when needed, and
          remove eligible data from active systems. Some records may be retained when required for
          security, legal compliance, billing, abuse prevention, or audit history.
        </p>

        <h2>Contact</h2>
        <p>
          For account deletion, media deletion, TikTok authorization removal, or privacy questions,
          contact <a href="mailto:shangan@zswo.net">shangan@zswo.net</a>.
        </p>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
defineOptions({ name: 'DataDeletion' })
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
