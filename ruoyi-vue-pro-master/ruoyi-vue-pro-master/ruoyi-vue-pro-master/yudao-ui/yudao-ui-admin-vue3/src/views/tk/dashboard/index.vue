<template>
  <div class="tk-home">
    <header class="home-header">
      <div>
        <h1>{{ copy.heroTitle }} <span>AI</span></h1>
        <p>{{ copy.heroSubtitle }}</p>
      </div>
      <div
        v-if="creditBalance.tenantId"
        class="credit-status"
        :class="{ warning: creditBalance.lowBalance }"
      >
        <Icon icon="ep:coin" />
        <strong
          >{{ copy.remainingCredits }} {{ creditBalance.remainingCredits ?? 0 }}
          {{ copy.creditsUnit }}</strong
        >
        <em v-if="creditBalance.lowBalance">{{ copy.lowCredit }}</em>
        <el-button link type="primary" class="recharge-detail-link" @click="showRechargeDetail = true">
          {{ copy.rechargeDetail }}
        </el-button>
      </div>
    </header>

    <el-dialog v-model="showRechargeDetail" :title="copy.rechargeDetail" width="720px">
      <div class="recharge-detail">
        <section>
          <h3>{{ copy.rechargeTiers }}</h3>
          <div class="recharge-table-wrap">
            <table class="recharge-table">
              <thead>
                <tr>
                  <th>{{ copy.rechargeAmount }}</th>
                  <th>{{ copy.rechargeDiscount }}</th>
                  <th>{{ copy.creditUnitPrice }}</th>
                  <th>{{ copy.rechargeCredits }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in rechargeTierRows" :key="item.amount">
                  <td>{{ item.amount }}</td>
                  <td>{{ item.discount }}</td>
                  <td>{{ item.unitPrice }}</td>
                  <td>{{ item.credits }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section>
          <div class="recharge-section-title">
            <h3>{{ copy.fullProcessCost }}</h3>
            <el-tag type="success" effect="light">{{ copy.fullProcessCreditRule }}</el-tag>
          </div>
          <div class="recharge-table-wrap">
            <table class="recharge-table">
              <thead>
                <tr>
                  <th>{{ copy.rechargeAmount }}</th>
                  <th>{{ copy.singleVideoCost }}</th>
                  <th>{{ copy.availableGenerationCount }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in rechargeCostRows" :key="item.amount">
                  <td>{{ item.amount }}</td>
                  <td>{{ item.cost }}</td>
                  <td>{{ item.count }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section>
          <h3>{{ copy.rechargeRules }}</h3>
          <ul class="recharge-rule-list">
            <li v-for="item in rechargeRuleRows" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>
    </el-dialog>

    <div class="home-layout">
      <main class="main-column">
        <section class="panel flow-panel">
          <div
            v-for="(step, index) in flowSteps"
            :key="step.title"
            class="flow-node"
            :class="[`status-${flowStepStatus(index)}`, { active: flowStepStatus(index) === 'current' }]"
            :aria-current="flowStepStatus(index) === 'current' ? 'step' : undefined"
          >
            <div class="flow-icon" :class="`tone-${step.tone}`">
              <Icon :icon="flowStepIcon(step, index)" />
            </div>
            <span class="flow-step-number">{{ formatFlowStepNumber(index) }}</span>
            <div class="flow-copy">
              <strong>{{ step.title }}</strong>
              <span>{{ step.desc }}</span>
            </div>
            <span class="flow-status-label">{{ flowStepStatusLabel(index) }}</span>
            <span
              v-if="index < flowSteps.length - 1"
              class="flow-connector"
              :class="{ done: index < activeStep }"
            ></span>
          </div>
        </section>

        <div class="top-grid">
          <section class="panel analysis-panel">
            <div class="panel-heading">
              <div>
                <span class="step-label">1</span>
                <h2>{{ analyzeTitleText }}</h2>
              </div>
              <p>{{ analyzeDescText }}</p>
            </div>

            <div class="analysis-filter-row">
              <div class="analysis-filter-item" :class="{ invalid: analysisValidation.libraryId }">
                <span>{{ copy.materialRequired }}</span>
                <el-select
                  v-model="createForm.libraryId"
                  :placeholder="copy.materialPlaceholder"
                  class="analysis-filter-control"
                >
                  <el-option
                    v-for="item in currentPurposeLibraries"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </el-select>
                <small v-if="analysisValidation.libraryId" class="analysis-field-message error">
                  <Icon icon="ep:warning" />
                  {{ copy.selectLibraryWarning }}
                </small>
              </div>
              <div class="analysis-filter-item">
                <span>{{ copy.languageRequired }}</span>
                <el-select
                  v-model="createForm.targetLanguage"
                  :placeholder="copy.languagePlaceholder"
                  class="analysis-filter-control"
                >
                  <el-option
                    v-for="item in languageOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </div>
              <div class="analysis-filter-item">
                <span>{{ copy.materialPurposeLabel }}</span>
                <el-select v-model="createForm.materialPurpose" class="analysis-filter-control">
                  <el-option
                    v-for="item in materialPurposeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </div>
              <div class="analysis-filter-item">
                <span>{{ copy.analysisProviderLabel }}</span>
                <el-segmented
                  v-model="createForm.analysisProvider"
                  :options="analysisProviderOptions"
                  class="analysis-filter-control"
                />
              </div>
              <div class="analysis-filter-item duration">
                <span>{{ copy.targetDurationLabel }}</span>
                <el-input-number
                  v-model="createForm.referenceDuration"
                  class="analysis-duration-input"
                  :min="MIN_TARGET_DURATION"
                  :max="MAX_TARGET_DURATION"
                  :step="1"
                  :precision="0"
                  :placeholder="copy.targetDurationPlaceholder"
                  controls-position="right"
                  @blur="normalizeTargetDuration"
                  @change="normalizeTargetDuration"
                />
              </div>
              <div v-if="supportsClipPlanMode" class="analysis-filter-item clip-plan-mode-field">
                <span>{{ copy.clipPlanModeLabel }}</span>
                <el-segmented
                  v-model="createForm.clipPlanMode"
                  :options="clipPlanModeOptions"
                  class="analysis-filter-control clip-plan-mode-control"
                />
              </div>
            </div>

            <div class="analysis-link-block" :class="{ invalid: analysisValidation.sourceUrl }">
              <div class="link-row">
                <el-input
                  v-model="createForm.sourceUrl"
                  class="link-input"
                  :placeholder="linkPlaceholderText"
                  @keyup.enter="handleAnalyzeButtonClick"
                >
                  <template #prefix>
                    <Icon icon="ep:link" />
                  </template>
                </el-input>
                <el-popconfirm
                  v-if="hasCurrentSuccessfulAnalysis"
                  :title="copy.reanalyzeConfirmMessage"
                  :confirm-button-text="copy.reanalyzeConfirmOk"
                  :cancel-button-text="copy.reanalyzeConfirmCancel"
                  :width="268"
                  placement="top-end"
                  @confirm="handleForceReanalyze"
                >
                  <template #reference>
                    <el-button
                      type="primary"
                      class="primary-action"
                      :loading="analyzing"
                      :disabled="analyzeButtonDisabled || optionalAnalyzeDisabled"
                    >
                      <Icon icon="ep:magic-stick" />
                      {{ analyzeButtonText }}
                    </el-button>
                  </template>
                </el-popconfirm>
                <el-button
                  v-else
                  type="primary"
                  class="primary-action"
                  :loading="analyzing"
                  :disabled="analyzeButtonDisabled || optionalAnalyzeDisabled"
                  @click="handleAnalyzeButtonClick"
                >
                  <Icon icon="ep:magic-stick" />
                  {{ analyzeButtonText }}
                </el-button>
              </div>
              <small v-if="analysisValidation.sourceUrl" class="analysis-field-message error">
                <Icon icon="ep:warning" />
                {{ copy.inputLinkWarning }}
              </small>
              <small v-else-if="sampleInfoVisible" class="analysis-field-message info">
                <Icon icon="ep:info-filled" />
                {{ copy.sampleInfo }}
              </small>
            </div>
            <el-button
              class="sample-button"
              :class="{ active: sampleInfoVisible }"
              plain
              @click="handleUseSample"
            >
              {{ copy.sampleFormat }}
            </el-button>

            <div
              v-if="shouldShowAnalysisBody"
              class="analysis-body"
              :class="{ single: !shouldShowReferenceCard }"
            >
              <div
                v-if="shouldShowReferenceCard"
                class="reference-card"
              >
                <div class="tiktok-mark">♪</div>
                <video
                  v-if="referencePreviewUrl"
                  :key="referencePreviewUrl"
                  class="reference-media"
                  :src="referencePreviewUrl"
                  :poster="referenceCoverUrl"
                  muted
                  controls
                  preload="metadata"
                  playsinline
                ></video>
                <img
                  v-else-if="referenceCoverUrl"
                  class="reference-media"
                  :src="referenceCoverUrl"
                  :alt="copy.coverAlt"
                />
                <div v-if="referencePreviewExpired" class="reference-expired">
                  <Icon icon="ep:clock" />
                  <span>{{ copy.referencePreviewExpired }}</span>
                </div>
              </div>

              <div
                v-if="shouldShowAnalysisResultDrawer"
                class="analysis-result-drawer"
                :class="{ expanded: analysisResultExpanded, full: !shouldShowReferenceCard }"
              >
                <button
                  class="analysis-result-head"
                  type="button"
                  @click="analysisResultExpanded = !analysisResultExpanded"
                >
                  <span class="analysis-result-title">
                    <Icon :icon="analysisResultIcon" />
                    <strong>{{ copy.aiAnalysisResult }}</strong>
                  </span>
                  <span class="analysis-result-summary">{{ analysisResultSummary }}</span>
                  <Icon
                    class="config-drawer-chevron"
                    :class="{ expanded: analysisResultExpanded }"
                    icon="ep:arrow-down"
                  />
                </button>

                <div v-show="analysisResultExpanded" class="analysis-result-body">
                  <div
                    v-if="
                      (analyzing && !suppressAnalysisProgress) ||
                      analysisProgress.running ||
                      analysisProgress.failed
                    "
                    class="task-progress-card analysis-progress"
                    :class="{ failed: analysisProgress.failed }"
                  >
                    <div class="task-progress-head">
                      <div class="task-progress-icon">
                        <Icon :icon="currentAnalysisPhase.icon" />
                      </div>
                      <div>
                        <strong>{{ taskProgressCopy.analysisTitle }}</strong>
                        <p>{{ currentAnalysisPhase.desc }}</p>
                      </div>
                      <span>{{ analysisProgress.percent }}%</span>
                    </div>
                    <el-progress
                      :percentage="analysisProgress.percent"
                      :stroke-width="8"
                      :status="analysisProgress.failed ? 'exception' : undefined"
                    />
                    <div class="task-phase-list">
                      <span
                        v-for="(phase, index) in analysisPhases"
                        :key="phase.label"
                        :class="{
                          active: index === analysisProgress.phaseIndex,
                          done: analysisProgress.percent >= phase.percent
                        }"
                      >
                        {{ phase.label }}
                      </span>
                    </div>
                    <div class="task-progress-meta">
                      <span>{{ taskProgressCopy.elapsed }} {{ analysisProgress.elapsedText }}</span>
                      <span>{{
                        analysisProgress.failed
                          ? taskProgressCopy.analysisFailed
                          : taskProgressCopy.analysisHint
                      }}</span>
                    </div>
                  </div>
                  <div v-else class="analysis-result">
                    <div class="result-title">
                      <Icon :icon="referenceAnalysis ? 'ep:circle-check-filled' : 'ep:info-filled'" />
                      <strong>{{
                        referenceAnalysis ? copy.analysisDone : copy.analysisWaiting
                      }}</strong>
                    </div>
                    <p>
                      {{ copy.videoDuration }}：{{ analysisDurationText }} <span></span>
                      {{ copy.publishTime }}：{{ analysisPublishTime }}
                    </p>
                    <div v-if="referenceAnalysis" class="analysis-trace-line">
                      <span
                        >{{ copy.businessTraceId }}：{{
                          referenceAnalysis.businessTraceId || '-'
                        }}</span
                      >
                      <el-button
                        v-if="referenceAnalysis.businessTraceId"
                        text
                        size="small"
                        @click="handleCopyAnalysisTrace"
                      >
                        <Icon icon="ep:copy-document" />
                        {{ copy.copyBusinessTraceId }}
                      </el-button>
                    </div>
                    <div class="subsection-title">
                      <h3>{{ copy.aiAnalysisResult }}</h3>
                    </div>
                    <ul>
                      <li v-for="item in analysisResults" :key="item">
                        <Icon icon="ep:check" />
                        {{ item }}
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section v-if="!isLeadGenerationManualMode" class="panel script-panel">
            <div class="panel-heading inline-heading">
              <div>
                <span class="step-label">3</span>
                <h2>{{ copy.scriptTitle }}</h2>
                <em
                  >{{ copy.generatedCountPrefix }}{{ scriptOptions.length
                  }}{{ copy.generatedCountSuffix }}</em
                >
              </div>
              <div class="heading-actions">
                <div class="script-preview-tabs mini-tabs">
                  <button
                    type="button"
                    :class="{ active: insightPreviewMode === 'zh' }"
                    @click.stop="insightPreviewMode = 'zh'"
                  >
                    {{ copy.zhView }}
                  </button>
                  <button
                    type="button"
                    :class="{ active: insightPreviewMode === 'original' }"
                    @click.stop="insightPreviewMode = 'original'"
                  >
                    {{ copy.originalView }}
                  </button>
                </div>
              </div>
              <el-button
                plain
                size="small"
                :loading="regeneratingScripts"
                :disabled="analyzing"
                @click="handleRegenerateScripts"
              >
                <Icon icon="ep:refresh" />
                {{ copy.regenerate }}
              </el-button>
            </div>

            <div class="script-table">
              <div class="script-head">
                <span>{{ copy.scriptPlan }}</span>
                <span>{{ copy.estimatedRate }}</span>
                <span>{{ copy.operation }}</span>
              </div>
              <button
                v-for="item in displayScriptOptions"
                :key="item.id || item.title"
                class="script-row"
                :class="{ selected: isScriptSelectedForGeneration(item.sourceIndex) }"
                type="button"
                @click="handleScriptOptionClick(item.sourceIndex)"
              >
                <span class="radio-dot"></span>
                <span class="script-title">
                  <strong>{{ item.title }}</strong>
                  <small>{{ copy.sellingPointPrefix }}{{ item.points }}</small>
                </span>
                <span class="rate">{{ item.rate }}</span>
                <span class="level" :class="`level-${item.levelType}`">{{ item.level }}</span>
                <span class="preview-link">{{ copy.preview }}</span>
              </button>
              <div v-if="!displayScriptOptions.length" class="script-empty">
                <Icon icon="ep:info-filled" />
                <span>{{ copy.noScript }}</span>
              </div>
            </div>
            <div class="table-foot">
              <button
                type="button"
                :disabled="
                  analyzing || regeneratingScripts || scriptOptions.length <= DISPLAY_SCRIPT_COUNT
                "
                @click="handleChangeScriptBatch"
              >
                <Icon icon="ep:refresh" />
                {{ copy.changeBatch }}
              </button>
              <span>{{ copy.totalPrefix }}{{ scriptOptions.length }}{{ copy.totalSuffix }}</span>
            </div>
          </section>
        </div>

        <section class="panel insight-panel">
          <div class="panel-heading">
            <div>
              <span class="step-label">2</span>
              <h2>{{ copy.insightTitle }}</h2>
            </div>
            <p>{{ copy.insightDesc }}</p>
          </div>
          <div class="selling-grid">
            <article v-for="point in sellingPoints" :key="point.title" class="selling-card">
              <div class="selling-icon" :class="`tone-${point.tone}`">
                <Icon :icon="point.icon" />
              </div>
              <strong>{{ point.title }}</strong>
              <span>{{ point.desc }}</span>
              <small>{{ copy.occurrenceCount }}{{ point.count }}{{ copy.occurrenceUnit }}</small>
              <em>{{ point.badge }}</em>
            </article>
            <div v-if="!sellingPoints.length" class="panel-empty">
              <Icon icon="ep:info-filled" />
              <span>{{ copy.noSellingPoints }}</span>
            </div>
          </div>
        </section>

        <div class="bottom-grid">
          <section class="panel materials-panel">
            <div class="panel-heading inline-heading">
              <div>
                <h2>{{ copy.materialOverview }}</h2>
                <p>{{ copy.materialDesc }}</p>
              </div>
              <button class="link-action" type="button" @click="goMaterialLibrary()">
                {{ copy.viewAll }} →
              </button>
            </div>
            <div class="material-grid">
              <article v-for="item in displayMaterials" :key="item.id" class="material-card">
                <div
                  class="material-thumb"
                  :class="{ empty: !item.previewVideoUrl && !item.coverUrl }"
                >
                  <img
                    v-if="item.coverUrl && !brokenMaterialCovers[item.id]"
                    :src="item.coverUrl"
                    :alt="item.name"
                    @error="markMaterialCoverBroken(item.id)"
                  />
                  <video
                    v-else-if="item.previewVideoUrl"
                    :src="item.previewVideoUrl"
                    muted
                    preload="metadata"
                    playsinline
                  ></video>
                  <Icon v-else icon="ep:video-camera" />
                </div>
                <strong>{{ item.name }}</strong>
                <span>{{ item.count }}{{ copy.materialUnit }}</span>
              </article>
              <div v-if="!displayMaterials.length" class="material-empty">
                <Icon icon="ep:folder-opened" />
                <span>{{ copy.noMaterials }}</span>
                <small>{{ copy.uploadMaterialHint }}</small>
              </div>
              <button class="add-material" type="button" @click="goMaterialLibraryUpload">
                <Icon icon="ep:plus" />
                <span>{{ copy.addMaterial }}</span>
                <small>{{ copy.dragUpload }}</small>
              </button>
            </div>
          </section>

          <section class="panel final-panel">
            <div class="script-choice">
              <div class="panel-heading compact-heading">
                <div>
                  <span class="step-label">4</span>
                  <h2>{{ copy.chooseScript }}</h2>
                </div>
              </div>
              <div class="chosen-script">
                <template v-if="isLeadGenerationManualMode">
                  <strong>{{ copy.manualScriptTitle }}</strong>
                  <span>{{ copy.manualScriptHint }}</span>
                  <el-input
                    v-model="manualLeadScriptText"
                    type="textarea"
                    :autosize="{ minRows: 6, maxRows: 10 }"
                    maxlength="3000"
                    show-word-limit
                    class="manual-script-input"
                    :placeholder="copy.manualScriptPlaceholder"
                  />
                  <div
                    v-if="manualLeadVoiceDurationHint"
                    class="manual-voice-duration-hint"
                    :class="`is-${manualLeadVoiceDurationLevel}`"
                  >
                    <Icon
                      :icon="
                        manualLeadVoiceDurationLevel === 'danger'
                          ? 'ep:warning-filled'
                          : 'ep:info-filled'
                      "
                    />
                    <span>{{ manualLeadVoiceDurationHint }}</span>
                  </div>
                  <Icon icon="ep:edit-pen" />
                </template>
                <template v-else-if="selectedScript">
                  <strong>{{ selectedScriptDisplayTitle }}</strong>
                  <span
                    >{{ copy.selectedRate }}<em>{{ selectedScript.rate }}</em></span
                  >
                  <small>{{ copy.selectedPoints }}{{ selectedScriptDisplayPoints }}</small>
                  <div class="script-preview-tabs">
                    <button
                      type="button"
                      :class="{ active: scriptPreviewMode === 'zh' }"
                      @click.stop="scriptPreviewMode = 'zh'"
                    >
                      {{ copy.zhDescription }}
                    </button>
                    <button
                      type="button"
                      :class="{ active: scriptPreviewMode === 'original' }"
                      @click.stop="scriptPreviewMode = 'original'"
                    >
                      {{ copy.originalScript }}（{{ targetLanguageLabel }}）
                    </button>
                  </div>
                  <p class="script-preview-text">
                    {{
                      scriptPreviewMode === 'zh'
                        ? selectedScript.displayScriptZh
                        : selectedScript.scriptText
                    }}
                  </p>
                  <Icon icon="ep:circle-check-filled" />
                </template>
                <template v-else>
                  <strong>{{ copy.noSelectedScript }}</strong>
                  <span>{{ copy.finishAnalysisFirst }}</span>
                </template>
              </div>
            </div>

            <div class="config-box">
              <div class="panel-heading compact-heading">
                <div>
                  <span class="step-label">5</span>
                  <h2>{{ copy.configTitle }}</h2>
                </div>
              </div>
              <small class="config-relocated-note">{{ copy.analysisSettingsMoved }}</small>

              <div v-if="showBatchGenerationControls" class="batch-generate-box">
                <div class="batch-switch-row">
                  <div>
                    <strong>{{ copy.batchGenerate }}</strong>
                    <span>{{ copy.batchGenerateHint }}</span>
                  </div>
                  <el-switch v-model="batchGenerationEnabled" />
                </div>
                <div v-if="batchGenerationEnabled" class="batch-options-row">
                  <span>{{ copy.videosPerScript }}</span>
                  <el-input-number v-model="videosPerScript" :min="1" :max="5" size="small" />
                  <em>{{ copy.estimatedVideos }}{{ plannedGenerationCount }}</em>
                </div>
                <small v-if="batchGenerationEnabled" class="field-hint">
                  {{ copy.batchSelectionSummaryPrefix }}{{ selectedScriptsForGeneration.length
                  }}{{ copy.batchSelectionSummaryMiddle }}{{ videosPerScript
                  }}{{ copy.batchSelectionSummarySuffix }}{{ plannedGenerationCount }}
                </small>
              </div>

              <div class="config-drawer voice-config-drawer">
                <button
                  class="config-drawer-head"
                  type="button"
                  @click="voiceConfigExpanded = !voiceConfigExpanded"
                >
                  <span class="config-drawer-title">
                    <strong>{{ copy.voiceRequired }}</strong>
                    <small>{{ voiceConfigSummary }}</small>
                  </span>
                  <span class="config-drawer-meta">
                    <el-switch
                      v-if="isLeadGenerationFlow"
                      v-model="createForm.voiceEnabled"
                      :disabled="isLeadBlankScriptMode"
                      @click.stop
                    />
                    <Icon
                      class="config-drawer-chevron"
                      :class="{ expanded: voiceConfigExpanded }"
                      icon="ep:arrow-down"
                    />
                  </span>
                </button>
                <div v-show="voiceConfigExpanded" class="config-drawer-body voice-config-body">
                  <label>{{ copy.ttsProviderLabel }}</label>
                  <el-radio-group
                    v-model="createForm.ttsProvider"
                    class="voice-provider-row"
                    :disabled="!isVoiceoverEnabled"
                  >
                    <el-radio-button
                      v-for="item in voiceProviderOptions"
                      :key="item.value"
                      :label="item.value"
                    >
                      {{ item.value === TTS_PROVIDER_MIMO ? copy.ttsProviderMimo : copy.ttsProviderDashscope }}
                    </el-radio-button>
                  </el-radio-group>

                  <div v-if="createForm.ttsProvider === TTS_PROVIDER_DASHSCOPE" class="voice-select-row">
                    <el-select
                      v-model="createForm.voiceCode"
                      :placeholder="copy.voicePlaceholder"
                      class="library-select"
                      :disabled="!isVoiceoverEnabled"
                    >
                      <el-option-group :label="copy.systemVoiceGroup">
                        <el-option v-for="item in systemVoiceOptions" :key="item.value" :label="item.label" :value="item.value" />
                      </el-option-group>
                      <el-option-group v-if="customVoiceOptions.length" :label="copy.customVoiceGroup">
                        <el-option v-for="item in customVoiceOptions" :key="item.value" :label="item.label" :value="item.value" />
                      </el-option-group>
                      <el-option-group v-if="historicalVoiceOptions.length" :label="copy.historicalVoiceGroup">
                        <el-option v-for="item in historicalVoiceOptions" :key="item.value" :label="item.label" :value="item.value" />
                      </el-option-group>
                    </el-select>
                    <el-tooltip :content="copy.manageCustomVoice">
                      <el-button circle plain :disabled="!isVoiceoverEnabled" @click="voiceManagerVisible = true"><Icon icon="ep:setting" /></el-button>
                    </el-tooltip>
                  </div>

                  <div v-else class="mimo-voice-grid">
                    <label>{{ copy.mimoSavedVoiceLabel }}</label>
                    <el-select
                      v-model="createForm.mimoVoiceProfileId"
                      class="library-select"
                      clearable
                      :disabled="!isVoiceoverEnabled"
                      :placeholder="copy.mimoSavedVoicePlaceholder"
                    >
                      <el-option
                        v-for="item in mimoSavedVoiceOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </el-select>
                    <label>{{ copy.mimoModeLabel }}</label>
                    <el-radio-group
                      v-model="createForm.mimoVoiceMode"
                      :disabled="!isVoiceoverEnabled || Boolean(createForm.mimoVoiceProfileId)"
                    >
                      <el-radio-button
                        v-for="item in mimoVoiceModeOptions"
                        :key="item.value"
                        :label="item.value"
                      >
                        {{
                          item.value === MIMO_VOICE_MODE_DESIGN
                            ? copy.mimoVoiceDesignMode
                            : item.value === MIMO_VOICE_MODE_CLONE
                              ? copy.mimoVoiceCloneMode
                              : copy.mimoPresetMode
                        }}
                      </el-radio-button>
                    </el-radio-group>
                    <template v-if="createForm.mimoVoiceMode === MIMO_VOICE_MODE_PRESET">
                      <label>{{ copy.mimoPresetLabel }}</label>
                      <el-select
                        v-model="createForm.mimoVoiceCode"
                        class="library-select"
                        :disabled="!isVoiceoverEnabled || Boolean(createForm.mimoVoiceProfileId)"
                      >
                        <el-option
                          v-for="item in mimoPresetVoiceOptions"
                          :key="item.value"
                          :label="item.label"
                          :value="item.value"
                        />
                      </el-select>
                    </template>
                    <template v-if="createForm.mimoVoiceMode === MIMO_VOICE_MODE_DESIGN">
                      <label>{{ copy.mimoPromptLabel }}</label>
                      <el-input
                        v-model="createForm.mimoVoicePrompt"
                        :disabled="!isVoiceoverEnabled || Boolean(createForm.mimoVoiceProfileId)"
                        type="textarea"
                        :rows="2"
                      />
                    </template>
                    <template v-if="createForm.mimoVoiceMode === MIMO_VOICE_MODE_CLONE">
                      <label>{{ copy.mimoSampleLabel }}</label>
                      <el-input
                        v-model="createForm.mimoVoiceSampleUrl"
                        :disabled="!isVoiceoverEnabled || Boolean(createForm.mimoVoiceProfileId)"
                      />
                    </template>
                    <small class="field-hint">{{ copy.mimoHint }}</small>
                  </div>

                  <div class="voice-preview-row">
                    <el-button
                      class="voice-preview-button"
                      plain
                      :loading="voicePreviewing"
                      :disabled="!voicePreviewReady"
                      @click="handlePreviewVoice"
                    >
                      <Icon :icon="voicePreviewing ? 'ep:loading' : 'ep:video-play'" />
                      {{ voicePreviewing ? copy.previewing : copy.previewVoice }}
                    </el-button>
                    <el-button
                      v-if="showSaveMimoVoiceButton"
                      plain
                      :loading="savingMimoVoice"
                      :disabled="!mimoVoiceSaveReady"
                      @click="handleSaveMimoVoice"
                    >
                      <Icon icon="ep:collection-tag" />
                      {{ copy.mimoSaveVoice }}
                    </el-button>
                  </div>
                  <small v-if="!isVoiceoverEnabled" class="field-hint">
                    {{ copy.voiceDisabledHint }}
                  </small>
                </div>
              </div>

              <div v-if="isLeadGenerationFlow" class="config-drawer bgm-config-drawer">
                <button
                  class="config-drawer-head bgm-config-head"
                  type="button"
                  @click="bgmConfigExpanded = !bgmConfigExpanded"
                >
                  <span class="config-drawer-title">
                    <strong>{{ copy.bgmTitle }}</strong>
                    <small>{{ bgmConfigSummary }}</small>
                  </span>
                  <span class="config-drawer-meta">
                    <el-switch v-model="createForm.bgmEnabled" @click.stop />
                    <Icon
                      class="config-drawer-chevron"
                      :class="{ expanded: bgmConfigExpanded }"
                      icon="ep:arrow-down"
                    />
                  </span>
                </button>
                <div v-show="bgmConfigExpanded && createForm.bgmEnabled" class="config-drawer-body bgm-config-body">
                  <label>{{ copy.bgmSelectLabel }}</label>
                  <div class="bgm-select-row">
                    <el-select
                      v-model="createForm.bgmAssetId"
                      :placeholder="copy.bgmSelectPlaceholder"
                      :loading="bgmLoading"
                      class="library-select"
                    >
                      <el-option-group v-if="systemBgmOptions.length" :label="copy.bgmSystem">
                        <el-option
                          v-for="item in systemBgmOptions"
                          :key="item.id"
                          :label="getBgmDisplayName(item)"
                          :value="item.id"
                        />
                      </el-option-group>
                      <el-option-group v-if="userBgmOptions.length" :label="copy.bgmMine">
                        <el-option
                          v-for="item in userBgmOptions"
                          :key="item.id"
                          :label="getBgmDisplayName(item)"
                          :value="item.id"
                        />
                      </el-option-group>
                    </el-select>
                    <el-button plain :disabled="!selectedBgmAsset?.fileUrl" @click="handlePreviewBgm">
                      <Icon icon="ep:video-play" />
                      {{ copy.previewVoice }}
                    </el-button>
                  </div>
                  <div class="bgm-volume-row">
                    <span>{{ copy.bgmVolume }}</span>
                    <el-slider
                      v-model="createForm.bgmVolume"
                      :min="0.01"
                      :max="0.3"
                      :step="0.01"
                      :format-tooltip="(value: number) => `${Math.round(value * 100)}%`"
                    />
                    <em>{{ Math.round(createForm.bgmVolume * 100) }}%</em>
                  </div>
                  <el-upload
                    class="bgm-upload"
                    :auto-upload="false"
                    :show-file-list="false"
                    :on-change="handleBgmUploadChange"
                    accept=".mp3,.wav,.m4a"
                  >
                    <el-button plain :loading="bgmUploading">
                      <Icon icon="ep:upload" />
                      {{ copy.bgmUpload }}
                    </el-button>
                  </el-upload>
                  <small class="field-hint">
                    {{ bgmAssets.length ? copy.bgmUploadHint : copy.bgmNoAssets }}
                  </small>
                </div>
              </div>

              <div class="config-drawer opening-config-drawer">
                <button
                  class="config-drawer-head"
                  type="button"
                  @click="openingConfigExpanded = !openingConfigExpanded"
                >
                  <span class="config-drawer-title">
                    <strong>{{ copy.openingRequired }}</strong>
                    <small>{{ copy.openingDrawerHint }}</small>
                  </span>
                  <span class="config-drawer-meta">
                    <em>{{ openingConfigSummary }}</em>
                    <Icon
                      class="config-drawer-chevron"
                      :class="{ expanded: openingConfigExpanded }"
                      icon="ep:arrow-down"
                    />
                  </span>
                </button>
                <div v-show="openingConfigExpanded" class="config-drawer-body">
                  <el-upload
                    ref="openingUploadRef"
                    class="opening-upload"
                    drag
                    :auto-upload="false"
                    :limit="1"
                    :on-change="handleOpeningVideoChange"
                    :on-remove="handleOpeningVideoRemove"
                    accept=".mp4,.mov,.webm"
                  >
                    <Icon icon="ep:upload-filled" />
                    <strong>{{ copy.uploadLocalVideo }}</strong>
                    <small>{{ copy.uploadHint }}</small>
                  </el-upload>
                  <div class="or-line">{{ copy.or }}</div>
                  <label>{{ copy.inputVideoLink }}</label>
                  <el-input
                    v-model="createForm.openingVideoUrl"
                    :placeholder="copy.videoLinkPlaceholder"
                  >
                    <template #suffix>
                      <Icon icon="ep:link" />
                    </template>
                  </el-input>
                  <div v-if="createForm.openingVideoUrl || openingVideoFile" class="opening-clip-range">
                    <small class="field-hint">
                      {{ isFullPoolRandomMode ? copy.openingFullPoolRandomHint : copy.openingFullVideoHint }}
                    </small>
                  </div>
                </div>
              </div>

              <div class="config-drawer subtitle-config-drawer">
                <button
                  class="config-drawer-head subtitle-config-head"
                  type="button"
                  @click="subtitleConfigExpanded = !subtitleConfigExpanded"
                >
                  <span class="config-drawer-title">
                    <strong>{{ copy.subtitleTitle }}</strong>
                    <small>{{ subtitleConfigSummary }}</small>
                  </span>
                  <span class="config-drawer-meta">
                    <el-switch
                      v-model="createForm.subtitleEnabled"
                      :disabled="!isVoiceoverEnabled"
                      @click.stop
                    />
                    <Icon
                      class="config-drawer-chevron"
                      :class="{ expanded: subtitleConfigExpanded }"
                      icon="ep:arrow-down"
                    />
                  </span>
                </button>
                <template v-if="createForm.subtitleEnabled && subtitleConfigExpanded">
                  <div class="config-drawer-body">
                  <div class="subtitle-grid">
                    <div>
                      <label>{{ copy.subtitleStyleLabel }}</label>
                      <el-select v-model="createForm.subtitleStyle" class="library-select">
                        <el-option
                          v-for="item in subtitleStyleOptions"
                          :key="item.value"
                          :label="localizedOption(item)"
                          :value="item.value"
                        />
                      </el-select>
                    </div>
                    <div>
                      <label>{{ copy.subtitlePositionLabel }}</label>
                      <el-select v-model="createForm.subtitlePositionMode" class="library-select">
                        <el-option
                          v-for="item in subtitlePositionOptions"
                          :key="item.value"
                          :label="localizedOption(item)"
                          :value="item.value"
                        />
                      </el-select>
                    </div>
                    <div>
                      <label>{{ copy.subtitleFontSizeLabel }}</label>
                      <el-segmented
                        v-model="createForm.subtitleFontSize"
                        :options="subtitleFontSizeOptions"
                      />
                    </div>
                    <div class="subtitle-switch-row">
                      <el-checkbox v-model="createForm.subtitleKeywordEnabled">
                        {{ copy.subtitleKeywordEnabled }}
                      </el-checkbox>
                      <el-checkbox v-model="createForm.subtitleKaraokeEnabled">
                        {{ copy.subtitleKaraokeEnabled }}
                      </el-checkbox>
                    </div>
                  </div>
                  <el-input
                    v-if="createForm.subtitleKeywordEnabled"
                    v-model="createForm.subtitleKeywords"
                    class="subtitle-keywords"
                    :placeholder="copy.subtitleKeywordsPlaceholder"
                    clearable
                  />
                  <div class="subtitle-style-preview" :class="subtitlePreviewClasses">
                    <div class="subtitle-preview-screen">
                      <div class="subtitle-preview-caption">
                        <span>{{ localizedSubtitlePreviewParts[0] }}</span>
                        <strong v-if="subtitlePreviewUsesEmphasis">{{
                          localizedSubtitlePreviewParts[1]
                        }}</strong>
                        <span v-else>{{ localizedSubtitlePreviewParts[1] }}</span>
                        <span>{{ localizedSubtitlePreviewParts[2] }}</span>
                      </div>
                      <div class="subtitle-position-guide" aria-hidden="true">
                        <span class="guide-top"></span>
                        <span class="guide-upper"></span>
                        <span class="guide-middle"></span>
                        <span class="guide-bottom"></span>
                        <span class="guide-left-lower"></span>
                      </div>
                    </div>
                    <small>{{ localizedSubtitleStyleDescription }}</small>
                  </div>
                  <small class="field-hint">{{ copy.subtitleHint }}</small>
                  </div>
                </template>
              </div>
            </div>

            <div
              v-if="
                generating ||
                generationProgress.running ||
                generationProgress.failed ||
                currentGenerationTask
              "
              class="generate-submit progressing"
            >
              <div
                class="task-progress-card generation-progress"
                :class="{ failed: generationProgress.failed }"
              >
                <div class="task-progress-head">
                  <div class="task-progress-icon">
                    <Icon :icon="currentGenerationPhase.icon" />
                  </div>
                  <div>
                    <strong>{{
                      precheckFailure
                        ? copy.precheckBlockedTitle
                        : generationProgress.failed
                          ? taskProgressCopy.generationFailedTitle
                          : taskProgressCopy.generationTitle
                    }}</strong>
                    <p>{{
                      precheckFailure?.message ||
                      (generationProgress.failed
                        ? friendlyGenerationFailureReason(currentGenerationTask)
                        : currentGenerationStepDescription)
                    }}</p>
                  </div>
                  <span>{{ generationProgress.failed ? taskProgressCopy.failedBadge : `${generationProgress.percent}%` }}</span>
                </div>
                <el-progress
                  :percentage="generationDisplayPercent"
                  :stroke-width="8"
                  :status="generationProgress.failed ? 'exception' : undefined"
                />
                <div class="task-phase-list">
                  <span
                    v-for="(phase, index) in generationPhases"
                    :key="phase.label"
                    :class="{
                      active: index === generationProgress.phaseIndex,
                      done: generationProgress.percent >= phase.percent
                    }"
                  >
                    {{ phase.label }}
                  </span>
                </div>
                <div v-if="currentGenerationTask" class="generation-task-detail">
                  <span>{{ copy.taskId }}：{{ currentGenerationTask.id }}</span>
                  <span v-if="currentGenerationTaskStepName">
                    {{ copy.currentStep }}：{{ currentGenerationTaskStepName }}
                    <template v-if="currentGenerationTask.currentStepTotal">
                      {{ ` (${currentGenerationTask.currentStepCompleted || 0}/${currentGenerationTask.currentStepTotal})` }}
                    </template>
                  </span>
                </div>
                <div v-if="batchGenerationTasks.length > 1" class="generation-batch-list">
                  <div class="generation-batch-head">
                    <strong>{{ copy.batchQueue }}</strong>
                    <span>{{ finishedBatchTaskCount }} / {{ batchGenerationTasks.length }}</span>
                  </div>
                  <div
                    v-for="task in batchGenerationTasks"
                    :key="task.id"
                    class="generation-batch-row"
                  >
                    <span>#{{ task.id }}</span>
                    <el-progress :percentage="Number(task.progress || 0)" :stroke-width="5" />
                    <em>{{ generationStatusText(task.status) }}</em>
                  </div>
                </div>
                <div v-if="precheckFailure" class="generation-failure precheck-failure">
                  <div class="generation-failure-title-row">
                    <strong>{{ precheckFailure.title }}</strong>
                    <el-tag type="danger" effect="plain">{{ copy.precheckFailed }}</el-tag>
                  </div>
                  <p>{{ precheckFailure.message }}</p>
                  <p v-if="precheckFailure.actionHint" class="precheck-action-hint">
                    {{ precheckFailure.actionHint }}
                  </p>
                  <div v-if="precheckSegmentRows.length" class="precheck-gap-table">
                    <div class="precheck-gap-row head">
                      <span>{{ copy.failureReason }}</span>
                      <span>{{ copy.precheckNeed }}</span>
                      <span>{{ copy.precheckCurrent }}</span>
                      <span>{{ copy.precheckStatus }}</span>
                    </div>
                    <div
                      v-for="item in precheckSegmentRows"
                      :key="item.segmentType"
                      class="precheck-gap-row"
                      :class="{ insufficient: Number(item.missingDuration || 0) > 0 }"
                    >
                      <span>{{ item.segmentName }}</span>
                      <span>{{ formatPrecheckSeconds(item.requiredDuration) }}</span>
                      <span>{{ formatPrecheckSeconds(item.duration) }}</span>
                      <strong>{{ precheckRowStatus(item.missingDuration) }}</strong>
                    </div>
                  </div>
                  <div class="precheck-actions">
                    <el-button type="primary" plain @click="goFixPrecheckMaterials">
                      <Icon icon="ep:folder-opened" />
                      {{ copy.fixMaterials }}
                    </el-button>
                    <el-button @click="handleRecheckGeneration" :loading="precheckingGeneration">
                      <Icon icon="ep:refresh-right" />
                      {{ copy.recheck }}
                    </el-button>
                  </div>
                  <small>{{ copy.precheckNoCredit }}</small>
                </div>
                <div v-if="currentGenerationTask?.outputUrl" class="generation-result-preview">
                  <video :src="currentGenerationTask.outputUrl" controls playsinline></video>
                  <div class="generation-result-name">
                    {{ currentGenerationOutputDisplayName }}
                  </div>
                  <div class="generation-result-actions">
                    <el-button
                      tag="a"
                      :href="currentGenerationTask.outputUrl"
                      :download="currentGenerationOutputDownloadName"
                      target="_blank"
                    >
                      <Icon icon="ep:download" />
                      {{ copy.downloadVideo }}
                    </el-button>
                    <el-button @click="handleCopyGenerationLink">
                      <Icon icon="ep:copy-document" />
                      {{ copy.copyVideoLink }}
                    </el-button>
                    <el-button type="primary" plain @click="clearCurrentGenerationTask">
                      <Icon icon="ep:plus" />
                      {{ copy.newGeneration }}
                    </el-button>
                  </div>
                </div>
                <div
                  v-else-if="generationProgress.failed && currentGenerationTask"
                  class="generation-failure"
                >
                  <strong>{{ copy.failureReason }}</strong>
                  <p>{{ friendlyGenerationFailureReason(currentGenerationTask) }}</p>
                  <el-button type="primary" plain @click="handleRetryGeneration">
                    <Icon icon="ep:refresh-right" />
                    {{ copy.regenerate }}
                  </el-button>
                </div>
                <div class="task-progress-meta">
                  <span>{{ taskProgressCopy.elapsed }} {{ generationProgress.elapsedText }}</span>
                  <span>{{
                    precheckFailure
                      ? precheckFailure.title
                      : generationProgress.failed
                        ? taskProgressCopy.generationFailed
                        : taskProgressCopy.generationHint
                  }}</span>
                </div>
                <div class="generation-repeat-actions">
                  <el-button
                    plain
                    :loading="audioExporting"
                    :disabled="audioExporting"
                    @click="handleCreateAudioExport"
                    v-hasPermi="['tk:generation:create']"
                  >
                    <Icon icon="ep:headset" />
                    {{ audioExporting ? copy.audioExporting : copy.generateAudio }} · {{ copy.audioExportCost }}
                  </el-button>
                  <el-button
                    type="primary"
                    @click="handleCreateGeneration"
                    v-hasPermi="['tk:generation:create']"
                  >
                    <Icon icon="ep:video-camera" />
                    {{ copy.generateVideo }}
                  </el-button>
                </div>
              </div>
            </div>
            <div v-else class="generate-submit">
              <div class="generate-action-row">
                <el-button
                  class="generate-audio-button"
                  :loading="audioExporting"
                  :disabled="audioExporting"
                  @click="handleCreateAudioExport"
                  v-hasPermi="['tk:generation:create']"
                >
                  <Icon icon="ep:headset" />
                  {{ audioExporting ? copy.audioExporting : copy.generateAudio }} · {{ copy.audioExportCost }}
                </el-button>
                <el-button
                  type="primary"
                  class="generate-button"
                  @click="handleCreateGeneration"
                  v-hasPermi="['tk:generation:create']"
                >
                  <Icon icon="ep:video-camera" />
                  {{ copy.generateVideo }}
                </el-button>
              </div>
              <div v-if="audioExportResult?.audioUrl" class="audio-export-result">
                <audio controls :src="audioExportResult.audioUrl" />
                <el-button
                  plain
                  tag="a"
                  :href="audioExportResult.audioUrl"
                  :download="audioExportDownloadName"
                  target="_blank"
                >
                  <Icon icon="ep:download" />
                  {{ copy.downloadAudio }}
                </el-button>
              </div>
              <p>{{ copy.estimateTime }}</p>
            </div>
          </section>
        </div>
      </main>

      <aside class="side-column">
        <section class="panel stats-panel">
          <div class="side-title">
            <h2>{{ copy.todayData }}</h2>
            <span>{{ copy.realtime }}</span>
          </div>
          <div v-for="metric in todayMetrics" :key="metric.label" class="metric-row">
            <div class="metric-icon" :class="`tone-${metric.tone}`">
              <Icon :icon="metric.icon" />
            </div>
            <div>
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </div>
          </div>
          <el-button class="stats-dashboard-link" plain @click="goDataDashboard">
            <Icon icon="ep:data-analysis" />
            {{ copy.viewDataDashboard }}
          </el-button>
        </section>

        <section class="ai-panel">
          <el-carousel
            arrow="never"
            class="ai-carousel"
            height="236px"
            indicator-position="outside"
            :interval="3600"
            pause-on-hover
          >
            <el-carousel-item v-for="item in aiAdvantageSlides" :key="item.title">
              <article class="ai-slide">
                <img class="ai-slide-image" :src="item.image" :alt="item.title" />
                <div class="ai-slide-shade"></div>
                <div class="ai-slide-content">
                  <span class="ai-slide-kicker">{{ item.kicker }}</span>
                  <h2>{{ item.title }}</h2>
                  <p>{{ item.desc }}</p>
                  <ul>
                    <li v-for="point in item.points" :key="point">
                      <Icon icon="ep:check" />
                      {{ point }}
                    </li>
                  </ul>
                </div>
              </article>
            </el-carousel-item>
          </el-carousel>
        </section>

        <section class="panel course-panel">
          <div class="side-title">
            <h2>{{ copy.courseTitle }}</h2>
          </div>
          <p>{{ copy.quickGuide }}</p>
          <div v-for="course in courses" :key="course.title" class="course-row">
            <Icon icon="ep:video-play" />
            <span>{{ course.title }}</span>
            <em>{{ course.time }}</em>
          </div>
          <a>{{ copy.viewAllCourses }} →</a>
        </section>
      </aside>
    </div>
    <VoiceProfileDialog v-model="voiceManagerVisible" @changed="loadCustomVoices" />
  </div>
</template>

<script setup lang="ts">
import { TkDashboardApi } from '@/api/tk/dashboard'
import { TkBgmAssetApi, type TkBgmAssetVO } from '@/api/tk/bgm'
import { TkGenerationApi } from '@/api/tk/generation'
import type {
  TkAudioExportTaskVO,
  TkGenerationPrecheckIssueVO,
  TkGenerationPrecheckRespVO,
  TkGenerationTaskStatusVO,
  TkGenerationTaskVO
} from '@/api/tk/generation'
import { TkMaterialApi } from '@/api/tk/material'
import type { TkMaterialLibraryVO } from '@/api/tk/material'
import { TkReferenceApi } from '@/api/tk/reference'
import type { TkReferenceAnalysisVO, TkReferenceScriptOptionVO } from '@/api/tk/reference'
import { TkCreditApi, TkCreditBalanceVO } from '@/api/tk/credit'
import { useLocaleStore } from '@/store/modules/locale'
import aiCopyOptimizeImage from '@/assets/imgs/tk-dashboard/ai-copy-optimize.webp'
import aiEfficiencyImage from '@/assets/imgs/tk-dashboard/ai-efficiency.webp'
import aiMaterialMatchImage from '@/assets/imgs/tk-dashboard/ai-material-match.webp'
import aiVoiceoverImage from '@/assets/imgs/tk-dashboard/ai-voiceover.webp'
import { TkVoiceProfileApi, type TkVoiceProfileVO } from '@/api/tk/voice'
import VoiceProfileDialog from '@/views/tk/voice/components/VoiceProfileDialog.vue'
import {
  getGenerationFocusTask,
  isTerminalGenerationStatus,
  mergeGenerationTasks
} from './generationTaskQueue.mjs'
import {
  buildGenerationOutputDisplayName,
  buildGenerationOutputDownloadName
} from '@/utils/tkGenerationOutputName'

defineOptions({ name: 'TkDashboard' })

interface DashboardScriptOption {
  id?: number
  analysisId?: number
  title: string
  points: string
  originalTitle: string
  originalPoints: string
  displayTitleZh: string
  displayPointsZh: string
  rate: string
  level: string
  levelType: 'high' | 'mid' | 'low'
  scriptText: string
  displayScriptZh: string
}

interface DisplayScriptOption extends DashboardScriptOption {
  sourceIndex: number
}

interface TaskPhase {
  label: string
  desc: string
  percent: number
  icon: string
}

interface TaskProgressState {
  running: boolean
  failed: boolean
  percent: number
  phaseIndex: number
  startedAt: number
  elapsedText: string
}

interface PrecheckFailureState {
  result: TkGenerationPrecheckRespVO
  primary?: TkGenerationPrecheckIssueVO
  title: string
  message: string
  actionHint?: string
}

type MaterialPurpose = 'ECOMMERCE' | 'LEAD_GENERATION'
type AnalysisProvider = 'GEMINI' | 'DASHSCOPE_VIDEO'
type ClipPlanMode = 'SEGMENTED' | 'FULL_POOL_RANDOM'
type ProductCategoryCode =
  | 'DEFAULT'
  | '01'
  | '02'
  | '03'
  | '04'
  | '05'
  | '06'
  | '07'
  | '08'
  | '09'
  | '10'

const DISPLAY_SCRIPT_COUNT = 6
const TK_GENERATION_REPLAY_KEY = 'tk:generation:replay'
const DEFAULT_TARGET_DURATION = 15
const MIN_TARGET_DURATION = 8
const MAX_TARGET_DURATION = 500
const ANALYSIS_RECOVERY_TIME_TOLERANCE_MS = 60_000
const ANALYSIS_POLL_INTERVAL_MS = 3000
const ANALYSIS_POLL_TIMEOUT_MS = 10 * 60 * 1000
const DEFAULT_OPENING_CLIP_START = 0
const DEFAULT_OPENING_CLIP_DURATION = 5
const MATERIAL_PURPOSE_ECOMMERCE: MaterialPurpose = 'ECOMMERCE'
const MATERIAL_PURPOSE_LEAD_GENERATION: MaterialPurpose = 'LEAD_GENERATION'
const CLIP_PLAN_MODE_SEGMENTED: ClipPlanMode = 'SEGMENTED'
const CLIP_PLAN_MODE_FULL_POOL_RANDOM: ClipPlanMode = 'FULL_POOL_RANDOM'
const DEFAULT_PRODUCT_CATEGORY_CODE: ProductCategoryCode = 'DEFAULT'
const MANUAL_LEAD_GENERATION_SOURCE_PREFIX = 'manual-lead-generation://'
const ANALYSIS_PROVIDER_GEMINI: AnalysisProvider = 'GEMINI'
const ANALYSIS_PROVIDER_DASHSCOPE_VIDEO: AnalysisProvider = 'DASHSCOPE_VIDEO'
const targetLanguageOptions = [
  { zh: '中文', en: 'Chinese', value: 'zh-cn' },
  { zh: '英语', en: 'English', value: 'en' },
  { zh: '美式英语', en: 'American English', value: 'en-us' },
  { zh: '德语', en: 'German', value: 'de' },
  { zh: '西班牙语', en: 'Spanish', value: 'es' },
  { zh: '法语', en: 'French', value: 'fr' },
  { zh: '荷兰语', en: 'Dutch', value: 'nl' }
]
const productCategoryItems: Array<{ zh: string; en: string; value: ProductCategoryCode }> = [
  { zh: '默认生成逻辑', en: 'Default route', value: 'DEFAULT' },
  { zh: '01 服饰鞋包', en: '01 Apparel, Shoes & Bags', value: '01' },
  { zh: '02 美妆个护', en: '02 Beauty & Personal Care', value: '02' },
  { zh: '03 食品饮料', en: '03 Food & Beverage', value: '03' },
  { zh: '04 家居生活', en: '04 Home & Living', value: '04' },
  { zh: '05 3C数码', en: '05 3C Digital', value: '05' },
  { zh: '06 家用电器', en: '06 Home Appliances', value: '06' },
  { zh: '07 母婴儿童', en: '07 Mom, Baby & Kids', value: '07' },
  { zh: '08 运动户外', en: '08 Sports & Outdoors', value: '08' },
  { zh: '09 宠物用品', en: '09 Pet Supplies', value: '09' },
  { zh: '10 汽车用品', en: '10 Auto Supplies', value: '10' }
]
const defaultTargetLanguage = targetLanguageOptions[0].value
const TTS_PROVIDER_DASHSCOPE = 'DASHSCOPE'
const TTS_PROVIDER_MIMO = 'MIMO'
const MIMO_VOICE_MODE_PRESET = 'PRESET'
const MIMO_VOICE_MODE_DESIGN = 'VOICE_DESIGN'
const MIMO_VOICE_MODE_CLONE = 'VOICE_CLONE'
const systemVoiceOptions = [
  {
    label: '丽莎',
    value: 'cosyvoice-v3.5-plus-tklisa-06c5654167dd4da3bfd5d69dfd5402b0'
  },
  {
    label: '丽莎 S',
    value: 'cosyvoice-v3.5-plus-tklisas-50aa9d9a3de84a68993fb3f43249f782'
  },
  {
    label: '文森特',
    value: 'cosyvoice-v3.5-plus-tkwincent-a0246845fbee48f998c61d3d5fa552a8'
  },
  {
    label: '乔纳森',
    value: 'cosyvoice-v3.5-plus-tkwincent-eaaebcdfecc646eb9d0457f6a2eadffb'
  },
  {
    label: '兰德鲁特',
    value: 'cosyvoice-v3.5-plus-tklandrut-debf6da87564451f861465eee9fbc7de'
  },
  {
    label: '莉娅',
    value: 'cosyvoice-v3.5-plus-tklea-72cb876e220e4a668e7c5b64ac97faf9'
  },
  {
    label: '乔乔',
    value: 'cosyvoice-v3.5-plus-tkjojosiwa-86a967cf093b4fbcb4325cd7e53a8f88'
  }
]
const defaultVoiceCode = systemVoiceOptions[0].value
const defaultMimoVoiceCode = 'Mia'
const mimoPresetVoiceOptions = [
  { label: 'mimo_default', value: 'mimo_default' },
  { label: '冰糖', value: '冰糖' },
  { label: '茉莉', value: '茉莉' },
  { label: '苏打', value: '苏打' },
  { label: '白桦', value: '白桦' },
  { label: 'Mia', value: 'Mia' },
  { label: 'Chloe', value: 'Chloe' },
  { label: 'Milo', value: 'Milo' },
  { label: 'Dean', value: 'Dean' }
]
const voiceProviderOptions = [
  { label: 'DashScope', value: TTS_PROVIDER_DASHSCOPE },
  { label: 'MiMo', value: TTS_PROVIDER_MIMO }
]
const mimoVoiceModeOptions = [
  { label: '预置音色', value: MIMO_VOICE_MODE_PRESET },
  { label: '音色设计', value: MIMO_VOICE_MODE_DESIGN },
  { label: '音色克隆', value: MIMO_VOICE_MODE_CLONE }
]
const subtitleStyleOptions = [
  {
    zh: '标准白字黑边',
    en: 'Classic white outline',
    value: 'classic_white',
    descZh: '通用高可读白字黑边，适合所有素材兜底。',
    descEn: 'Readable white text with dark outline for general use.',
    previewZh: ['这个产品', '轻松解决', '日常小麻烦'],
    previewEn: ['This product', 'fixes', 'daily hassles']
  },
  {
    zh: '黄色重点词',
    en: 'Yellow keywords',
    value: 'yellow_keyword',
    descZh: '保留白字黑边，自动把价格、折扣和卖点词变黄。',
    descEn: 'White outline captions with yellow commerce keywords.',
    previewZh: ['今天下单', '限时优惠', '直接到手'],
    previewEn: ['Today only', '40% off', 'with fast shipping']
  },
  {
    zh: 'TikTok 大字风',
    en: 'TikTok large text',
    value: 'tiktok_large',
    descZh: '大字号短词组，适合口播强节奏和开头钩子。',
    descEn: 'Large punchy chunks for hooks and creator-style voiceover.',
    previewZh: ['别再忍了', '这个方法', '马上见效'],
    previewEn: ['Stop scrolling', 'this trick', 'works fast']
  },
  {
    zh: '促销爆款风',
    en: 'Promo bold',
    value: 'promo_bold',
    descZh: '红黄促销色块，适合限时、爆款、强转化文案。',
    descEn: 'Bold red and yellow commerce card for urgent offers.',
    previewZh: ['爆款补货', '买一送一', '今晚结束'],
    previewEn: ['Hot deal', 'buy one get one', 'ends tonight']
  },
  {
    zh: '清爽商品讲解',
    en: 'Clean product',
    value: 'clean_product',
    descZh: '浅色说明卡片，适合商品细节、教程和测评。',
    descEn: 'Clean explainer card for product details and tutorials.',
    previewZh: ['透气材质', '稳稳支撑', '日常更舒服'],
    previewEn: ['Breathable design', 'steady support', 'for daily comfort']
  },
  {
    zh: '霓虹撞色风',
    en: 'Neon pop',
    value: 'neon_pop',
    descZh: '青粉撞色高冲击，适合年轻化种草和反差钩子。',
    descEn: 'Cyan and pink high-impact style for social hooks.',
    previewZh: ['这个秘密', '太好用了', '别错过'],
    previewEn: ['This secret', 'actually works', 'do not miss it']
  },
  {
    zh: '黄色情绪字',
    en: 'Yellow story',
    value: 'yellow_story',
    descZh: '暖黄大字带情绪感，适合观点、痛点和反差叙事。',
    descEn: 'Warm yellow story captions for pain points and contrast.',
    previewZh: ['终于不用', '每天纠结', '怎么收纳了'],
    previewEn: ['Finally', 'no more mess', 'every morning']
  },
  {
    zh: '价格闪卡风',
    en: 'Price flash',
    value: 'price_flash',
    descZh: '黑底黄字突出价格、折扣和限时信息。',
    descEn: 'Black and yellow price card for discounts and urgency.',
    previewZh: ['到手价', '$19.99', '今天有效'],
    previewEn: ['Only today', '$19.99', 'free shipping']
  },
  {
    zh: '步骤卡片风',
    en: 'Step card',
    value: 'step_card',
    descZh: '清晰步骤卡片，适合使用方法、教程和对比测评。',
    descEn: 'Step card style for how-to videos and comparisons.',
    previewZh: ['Step 1', '套上产品', '再调节松紧'],
    previewEn: ['Step 1', 'put it on', 'then adjust the fit']
  },
  {
    zh: '品牌极简风',
    en: 'Brand minimal',
    value: 'brand_minimal',
    descZh: '克制的深色字和浅色底，适合高客单、品牌感和质感商品。',
    descEn: 'Restrained dark text on a light base for premium product videos.',
    previewZh: ['质感升级', '细节更稳', '日常更高级'],
    previewEn: ['Premium feel', 'steady details', 'daily upgrade']
  },
  {
    zh: '评论气泡风',
    en: 'Comment bubble',
    value: 'comment_bubble',
    descZh: '社媒评论气泡观感，适合用户反馈、测评和口碑表达。',
    descEn: 'Social comment bubble style for reviews and customer proof.',
    previewZh: ['用户说', '真的好用', '回购了'],
    previewEn: ['They said', 'it works', 'bought again']
  }
]
const subtitlePositionOptions = [
  { zh: '智能避让', en: 'Smart safe area', value: 'smart_safe' },
  { zh: '固定底部', en: 'Fixed bottom', value: 'fixed_bottom' },
  { zh: '固定中下', en: 'Fixed lower middle', value: 'fixed_middle' },
  { zh: '上下交替', en: 'Alternate top/bottom', value: 'alternate' },
  { zh: '每句换位置', en: 'Rotate every sentence', value: 'sentence_rotate' },
  { zh: '随机安全区', en: 'Random safe area', value: 'random_safe' }
]
const message = useMessage()
const router = useRouter()
const localeStore = useLocaleStore()
const currentLocale = computed(() => localeStore.getCurrentLocale.lang)
const isEn = computed(() => currentLocale.value === 'en')
const copy = computed(() =>
  isEn.value
    ? {
        heroTitle: 'TikTok Shop Video · AI Creation',
        heroSubtitle: 'From reference links to high-converting videos in one AI workflow',
        remainingCredits: 'Remaining',
        creditsUnit: 'credits',
        lowCredit: 'Low balance. Contact support to recharge',
        rechargeDetail: 'Recharge details',
        rechargeTiers: 'Recharge tiers',
        rechargeAmount: 'Amount',
        rechargeDiscount: 'Discount',
        creditUnitPrice: 'Credit unit price',
        rechargeCredits: 'Credits received',
        fullProcessCost: 'Full workflow cost',
        fullProcessCreditRule: 'Script + video = 2 credits/item',
        singleVideoCost: 'Cost per item',
        availableGenerationCount: 'Available generations',
        rechargeRules: 'Notes',
        analyzeTitle: 'Analyze reference link',
        analyzeDesc:
          'Enter a TikTok video link. AI will analyze the content and extract selling points.',
        leadAnalyzeTitle: 'Lead-gen video script',
        leadAnalyzeDesc:
          'Reference analysis is optional. You can enter a lead-gen script directly and generate video.',
        linkPlaceholder:
          'Enter a TikTok video link, e.g. https://www.tiktok.com/@username/video/123456789',
        leadLinkPlaceholder:
          'Optional reference link. Leave it empty to generate from your script directly.',
        startAnalyze: 'Start analysis',
        optionalAnalyze: 'Optional analysis',
        reanalyze: 'Re-analyze',
        materialPurposeLabel: 'Material type',
        analysisProviderLabel: 'Analysis engine',
        geminiAnalysis: 'Existing analysis',
        ecommerceMaterial: 'E-commerce material',
        leadGenerationMaterial: 'Lead-gen material',
        reanalyzeConfirmTitle: 'Re-analyze reference link',
        reanalyzeConfirmMessage:
          'A completed analysis already matches the current link and settings. Re-analyzing will consume credits again. Continue?',
        reanalyzeConfirmOk: 'Re-analyze',
        reanalyzeConfirmCancel: 'Cancel',
        sampleFormat: 'View format',
        coverAlt: 'Reference video cover',
        referencePreviewExpired: 'Preview expired. Analysis and scripts are still available.',
        linkEmpty: 'Enter a link to show the real video',
        analysisDone: 'Analysis complete',
        analysisWaiting: 'Waiting for analysis',
        videoDuration: 'Duration',
        publishTime: 'Published',
        aiAnalysisResult: 'AI analysis result',
        scriptTitle: 'AI script titles',
        manualScriptTitle: 'Manual lead-gen script',
        manualScriptHint:
          'Optional. If left empty, the system creates a visual-only lead-gen mix. If filled, it can be used for voiceover, subtitles, and editing.',
        manualScriptPlaceholder:
          'Optional. Example: Comment GUIDE and I will send you the full checklist. Save this before your next campaign.',
        manualVoiceDurationEmptyHint:
          'Leave the script empty for a visual-only mix. Enter text to estimate voiceover duration.',
        manualVoiceDurationNormal: (estimated: number, target: number) =>
          `Estimated voiceover ${estimated}s, close to your target ${target}s. Final length still depends on complete material clips.`,
        manualVoiceDurationWarning: (estimated: number, target: number) =>
          `Estimated voiceover ${estimated}s, target ${target}s. Adjust the script or target duration if you need the voiceover to fit better.`,
        manualVoiceDurationDanger: (estimated: number, target: number) =>
          `Estimated voiceover ${estimated}s, much longer than target ${target}s. Shorten the script or increase target duration.`,
        generatedCountPrefix: 'Generated ',
        generatedCountSuffix: ' options',
        zhView: 'Chinese view',
        originalView: 'Original view',
        regenerate: 'Regenerate',
        scriptPlan: 'Script title option',
        estimatedRate: 'Estimated conversion',
        operation: 'Action',
        sellingPointPrefix: 'Selling points: ',
        preview: 'Preview',
        noScript: 'No real script options yet. Finish reference analysis first.',
        changeBatch: 'Refresh scripts',
        totalPrefix: 'Total ',
        totalSuffix: ' options',
        insightTitle: 'AI selling point insights',
        insightDesc: 'AI extracts core selling points and details from the video.',
        noSellingPoints: 'Selling points appear after real reference analysis.',
        occurrenceCount: 'Occurrences: ',
        occurrenceUnit: '',
        materialOverview: 'Material library overview',
        materialDesc: 'All materials by product category',
        viewAll: 'View all',
        materialUnit: ' materials',
        noMaterials: 'No real material libraries',
        uploadMaterialHint: 'Upload videos in Material Library first',
        addMaterial: 'Add material',
        dragUpload: 'Drag-and-drop upload supported',
        chooseScript: 'Choose script option',
        selectedRate: 'Estimated conversion: ',
        selectedPoints: 'Selling point mix: ',
        zhDescription: 'Chinese notes',
        originalScript: 'Original voiceover',
        noSelectedScript: 'No script option available',
        finishAnalysisFirst: 'Finish real reference analysis to choose a script.',
        configTitle: 'Configure video settings',
        materialRequired: 'Material library (required)',
        materialPlaceholder: 'Select a material library',
        languageRequired: 'Script/voiceover language (required)',
        languagePlaceholder: 'Select script and voiceover language',
        targetDurationLabel: 'Target video duration',
        targetDurationPlaceholder: '15',
        targetDurationHint: 'Leave empty to use 15 seconds. Supported range: 8-500 seconds.',
        clipPlanModeLabel: 'Video generation mode',
        clipPlanModeSegmented: 'Default structure',
        clipPlanModeFullPoolRandom: 'Random pool',
        analysisSettingsMoved: 'Material, language, and duration are set before analysis.',
        voiceRequired: 'AI voice',
        ttsProviderLabel: 'Voice provider',
        ttsProviderDashscope: 'DashScope',
        ttsProviderMimo: 'MiMo',
        voicePlaceholder: 'Default: Lisa',
        voiceEnabledSummary: 'On',
        voiceDisabledSummary: 'Off',
        voiceDisabledHint:
          'Voiceover is off. Leave the script empty for visuals only, or enter a script to enable voiceover.',
        systemVoiceGroup: 'System voices',
        customVoiceGroup: 'My voices',
        manageCustomVoice: 'Manage voices',
        historicalVoice: 'Historical voice',
        historicalVoiceGroup: 'Historical voices',
        mimoModeLabel: 'MiMo mode',
        mimoPresetMode: 'Preset voice',
        mimoVoiceDesignMode: 'Voice design',
        mimoVoiceCloneMode: 'Voice clone',
        mimoSavedVoiceLabel: 'My MiMo voices',
        mimoSavedVoicePlaceholder: 'Optional: use a saved MiMo voice',
        mimoPresetLabel: 'Preset voice',
        mimoPromptLabel: 'Voice design prompt',
        mimoSampleLabel: 'Sample audio URL',
        mimoHint:
          'MiMo only takes effect after switching the voice provider to MiMo. Keep DashScope unchanged to use the current flow.',
        mimoSaveVoice: 'Save voice',
        mimoSaveNamePrompt: 'Enter a name for this MiMo voice',
        mimoSaveConsentWarning: 'Confirm you have authorization to use this sample before saving.',
        mimoSaveSuccess: 'MiMo voice saved',
        mimoSaveFailed: 'Failed to save MiMo voice',
        previewing: 'Playing',
        previewVoice: 'Preview',
        subtitleTitle: 'Smart subtitles',
        subtitleStyleLabel: 'Subtitle style',
        subtitlePositionLabel: 'Subtitle position',
        subtitleFontSizeLabel: 'Font size',
        subtitleKeywordEnabled: 'Highlight keywords',
        subtitleKaraokeEnabled: 'Karaoke timing',
        subtitleKeywordsPlaceholder: 'Manual keywords, separated by commas',
        subtitleHint:
          'Subtitles are burned into the final video with dynamic positions and ASS styling.',
        bgmTitle: 'Background music',
        bgmDrawerHint: 'Lead-gen videos only. Can be used with or without voiceover.',
        bgmEnabledSummary: 'On',
        bgmDisabledSummary: 'Off',
        bgmSelectLabel: 'Music track',
        bgmSelectPlaceholder: 'Select background music',
        bgmUpload: 'Upload BGM',
        bgmUploadHint: 'MP3/WAV/M4A, max 20MB',
        bgmSystem: 'System',
        bgmMine: 'Uploaded',
        bgmVolume: 'Volume',
        bgmNoAssets: 'No BGM yet. Upload one to use background music.',
        bgmLoadError: 'Failed to load BGM list',
        bgmUploadSuccess: 'BGM uploaded',
        bgmUploadError: 'BGM upload failed',
        bgmPlayError: 'Failed to play BGM',
        bgmRequiredWarning: 'Select or upload a BGM track before generating.',
        openingRequired: 'Opening hook material (optional)',
        openingDrawerHint: 'Expand only when you need a fixed opening hook.',
        openingNotConfigured: 'Not configured',
        openingUploaded: 'Local video selected',
        openingLinked: 'Video link set',
        subtitleEnabledSummary: 'On',
        subtitleDisabledSummary: 'Off',
        uploadLocalVideo: 'Upload local video',
        uploadHint:
          'If skipped, the system randomly uses an S1_HOOK material. MP4/MOV recommended, max 100MB',
        or: 'or',
        inputVideoLink: 'Enter hook video link',
        videoLinkPlaceholder: 'Enter video link',
        openingFullVideoHint:
          'This video is used as a whole source. If it exceeds the hook duration, the hook section is compressed.',
        openingFullPoolRandomHint:
          'In Random pool mode, this video is fixed as the first 3 seconds; later clips are selected randomly from the full material pool.',
        generateVideo: 'Generate mixed video',
        generateAudio: 'Generate audio',
        audioExportCost: '1 credit / generation',
        audioExporting: 'Generating audio',
        audioExportSuccess: 'Audio generated. 1 credit used.',
        audioExportMissingScript: 'Enter or select a script before generating audio.',
        audioExportFailed: 'Audio generation failed. No credit was used.',
        downloadAudio: 'Download audio',
        estimateTime: 'Estimated time: 3-5 minutes',
        todayData: 'Today',
        realtime: 'Live',
        viewDataDashboard: 'View full dashboard',
        courseTitle: 'Getting started',
        quickGuide: 'Quick start guide',
        viewAllCourses: 'View all tutorials',
        analysisEmpty:
          'Select a material library, enter a TikTok reference link, then start analysis.',
        leadAnalysisEmpty:
          'Lead-gen material can skip reference analysis. Enter a script below and generate directly.',
        productDetected: 'Product: ',
        coreSellingPoints: 'Core selling points: ',
        targetAudience: 'Target audience: ',
        usageScenarios: 'Usage scenarios: ',
        videoStructure: 'Video structure: ',
        unidentified: 'Unidentified',
        fallbackSellingTitle: 'Selling point ',
        fallbackSellingDesc: 'A high-frequency conversion point from the reference video',
        fallbackSellingBadge: 'Key selling point',
        libraryFallback: 'Material library',
        generatedVideos: 'Generated videos',
        consumedCredits: 'Credits used',
        mediumLevel: 'Medium',
        highLevel: 'High',
        lowLevel: 'Low',
        scriptFallbackTitle: 'Script option ',
        scriptFallbackPoints: 'Core selling point | Scenario | Call to action',
        languageFallback: 'Select language',
        finishAnalysisWarning: 'Finish reference analysis first',
        missingReplayAnalysis: 'This analysis record is missing a link or material library.',
        replayAnalysisSuccess:
          'Analysis record restored. You can generate directly or configure optional hook material.',
        missingReplayGeneration: 'This generation record is missing a link or material library.',
        replayGenerationSuccessWithUrl:
          'Generation record restored. Adjust settings and generate again.',
        replayGenerationSuccessNeedFile:
          'Generation record restored. Please reselect the locally uploaded hook video.',
        inputLinkWarning: 'Enter a TikTok reference link first',
        selectLibraryWarning: 'Select a material library first',
        analysisSuccess: 'Reference analysis completed. Script options are ready.',
        regenerateSuccess: 'Script options regenerated.',
        sampleInfo:
          'Paste a real public video link, e.g. https://www.tiktok.com/@username/video/real-video-id',
        selectVoiceWarning: 'Using the default AI voice',
        voicePlayError: 'Failed to play preview audio',
        voiceConfigIncompleteWarning: 'Complete the voice settings first',
        generationMissingWarning: 'Enter a TikTok link and select a material library.',
        leadGenerationMissingWarning: 'Select a lead-gen material library and enter a script.',
        selectScriptWarning: 'Finish reference analysis and select a script option first',
        manualScriptWarning: 'Enter a lead-gen script first',
        remoteHookVideo: 'Remote hook video',
        generationCreated: 'Generation task created. The AI editing pipeline is running.',
        batchGenerate: 'Batch generation',
        batchGenerateHint: 'Off by default. Turn on to select multiple scripts.',
        videosPerScript: 'Videos per script',
        estimatedVideos: 'Estimated videos: ',
        batchSelectionSummaryPrefix: 'Selected ',
        batchSelectionSummaryMiddle: ' scripts, ',
        batchSelectionSummarySuffix: ' per script. Total ',
        batchLimitWarning: 'A single batch can create up to 30 videos.',
        batchGenerationQueued: 'Batch tasks have been added to the generation queue.',
        batchGenerationCreated: 'Batch generation completed.',
        batchQueue: 'Generation queue',
        generationSuccess: 'Generation completed. Preview or download the video below.',
        generationRetrying: 'Retry submitted. The AI editing pipeline is running again.',
        precheckFailed: 'Precheck failed',
        precheckBlockedTitle: 'Precheck not passed',
        precheckNoCredit: 'The task has not started and no generation credits were consumed.',
        precheckNeed: 'Need',
        precheckCurrent: 'Current',
        precheckStatus: 'Status',
        precheckSatisfied: 'Ready',
        precheckPassed: 'Precheck passed. You can start generation.',
        fixMaterials: 'Fix materials',
        recheck: 'Recheck',
        retry: 'Retry',
        downloadVideo: 'Download',
        copyVideoLink: 'Copy link',
        businessTraceId: 'Trace ID',
        copyBusinessTraceId: 'Copy',
        newGeneration: 'Create another',
        taskId: 'Task ID',
        currentStep: 'Current step',
        completedStep: 'Done',
        pendingStep: 'Pending',
        failureReason: 'Failure reason',
        copySuccess: 'Video link copied',
        businessTraceCopySuccess: 'Trace ID copied'
      }
    : {
        heroTitle: 'TikTok电商视频 · 智能创作',
        heroSubtitle: '从对标链接到爆款视频，AI帮你一键生成高转化素材',
        remainingCredits: '剩余',
        creditsUnit: '积分',
        lowCredit: '额度不足，请联系客服充值',
        rechargeDetail: '充值详情',
        rechargeTiers: '充值阶梯',
        rechargeAmount: '充值金额',
        rechargeDiscount: '折扣',
        creditUnitPrice: '积分单价',
        rechargeCredits: '获得积分',
        fullProcessCost: '全流程（文案+视频 = 2积分/条）',
        fullProcessCreditRule: '文案 + 视频 = 2积分/条',
        singleVideoCost: '单条成本',
        availableGenerationCount: '可生成条数',
        rechargeRules: '说明',
        analyzeTitle: '分析对标链接',
        analyzeDesc: '输入TikTok视频链接，AI将分析视频内容并提炼卖点细节',
        leadAnalyzeTitle: '引流视频文案',
        leadAnalyzeDesc: '对标链接分析为可选项，可直接输入引流文案并生成视频',
        linkPlaceholder:
          '请输入TikTok视频链接，例如：https://www.tiktok.com/@username/video/123456789',
        leadLinkPlaceholder: '可选填写对标链接，不填也可以直接用下方引流文案生成视频',
        startAnalyze: '开始分析',
        optionalAnalyze: '可选分析',
        reanalyze: '重新分析',
        materialPurposeLabel: '素材类型',
        analysisProviderLabel: '分析引擎',
        geminiAnalysis: '现有分析',
        ecommerceMaterial: '电商素材',
        leadGenerationMaterial: '引流素材',
        reanalyzeConfirmTitle: '重新分析对标链接',
        reanalyzeConfirmMessage: '当前链接和设置已有分析结果，重新分析会再次消耗积分，是否继续？',
        reanalyzeConfirmOk: '确认重新分析',
        reanalyzeConfirmCancel: '取消',
        sampleFormat: '查看格式',
        coverAlt: '对标视频封面',
        referencePreviewExpired: '参考视频预览已过期，分析结果和文案仍可使用',
        linkEmpty: '输入链接后展示真实视频',
        analysisDone: '分析完成',
        analysisWaiting: '等待分析',
        videoDuration: '视频时长',
        publishTime: '发布时间',
        aiAnalysisResult: 'AI分析结果',
        scriptTitle: 'AI生成文案标题',
        manualScriptTitle: '手动输入引流文案',
        manualScriptHint: '可选。不填写时生成纯画面引流混剪；填写后可用于口播、字幕和素材匹配。',
        manualScriptPlaceholder: '可不填。例如：评论关键词，我发你完整方案。先收藏这条，做投放前照着检查一遍。',
        manualVoiceDurationEmptyHint: '文案为空时生成纯画面混剪；输入文案后会预估口播时长。',
        manualVoiceDurationNormal: (estimated: number, target: number) =>
          `预计口播约 ${estimated} 秒，与你设置的目标 ${target} 秒接近；最终成片仍以完整素材拼接结果为准。`,
        manualVoiceDurationWarning: (estimated: number, target: number) =>
          `预计口播约 ${estimated} 秒，目标 ${target} 秒；如需口播更贴合，请调整文案或目标时长。`,
        manualVoiceDurationDanger: (estimated: number, target: number) =>
          `预计口播约 ${estimated} 秒，明显超过目标 ${target} 秒，建议缩短文案或提高目标时长。`,
        generatedCountPrefix: '共生成',
        generatedCountSuffix: '个方案',
        zhView: '中文展示',
        originalView: '原文展示',
        regenerate: '重新生成',
        scriptPlan: '文案标题方案',
        estimatedRate: '预估转化率',
        operation: '操作',
        sellingPointPrefix: '卖点：',
        preview: '预览',
        noScript: '暂无真实文案方案，请先完成对标分析',
        changeBatch: '换一批文案',
        totalPrefix: '共',
        totalSuffix: '个方案',
        insightTitle: 'AI提炼卖点细节',
        insightDesc: '基于视频内容，AI提炼出核心卖点和细节',
        noSellingPoints: '完成真实对标分析后展示卖点',
        occurrenceCount: '出现频次：',
        occurrenceUnit: '次',
        materialOverview: '素材库概览',
        materialDesc: '全部素材（按产品分类）',
        viewAll: '查看全部',
        materialUnit: '个素材',
        noMaterials: '暂无真实素材库',
        uploadMaterialHint: '请先到素材库上传视频',
        addMaterial: '添加素材',
        dragUpload: '支持拖拽上传',
        chooseScript: '选择文案方案',
        selectedRate: '预估转化率：',
        selectedPoints: '卖点组合：',
        zhDescription: '中文说明',
        originalScript: '原文口播',
        noSelectedScript: '暂无可选文案方案',
        finishAnalysisFirst: '完成真实对标分析后选择文案',
        configTitle: '配置视频设置',
        materialRequired: '素材库（必选）',
        materialPlaceholder: '请选择素材库',
        languageRequired: '文案/配音语言（必选）',
        languagePlaceholder: '请选择文案和配音语言',
        targetDurationLabel: '目标视频时长',
        targetDurationPlaceholder: '15',
        targetDurationHint: '不填默认 15 秒，支持 8-500 秒',
        clipPlanModeLabel: '视频生成方式',
        clipPlanModeSegmented: '默认结构拼接',
        clipPlanModeFullPoolRandom: '全素材随机拼接',
        analysisSettingsMoved: '素材库、语言、时长已在分析前设置',
        voiceRequired: 'AI配音音色',
        ttsProviderLabel: '音色提供方',
        ttsProviderDashscope: 'DashScope',
        ttsProviderMimo: 'MiMo',
        voicePlaceholder: '默认使用丽莎音色',
        voiceEnabledSummary: '已开启',
        voiceDisabledSummary: '已关闭',
        voiceDisabledHint: '口播已关闭。文案为空时只生成画面；填写文案后可开启口播。',
        systemVoiceGroup: '系统音色',
        customVoiceGroup: '我的音色',
        manageCustomVoice: '管理我的音色',
        historicalVoice: '历史音色',
        historicalVoiceGroup: '历史音色',
        mimoModeLabel: 'MiMo模式',
        mimoPresetMode: '预置音色',
        mimoVoiceDesignMode: '音色设计',
        mimoVoiceCloneMode: '音色克隆',
        mimoSavedVoiceLabel: '我的 MiMo 音色',
        mimoSavedVoicePlaceholder: '可选：使用已保存的 MiMo 音色',
        mimoPresetLabel: '预置音色',
        mimoPromptLabel: '音色设计提示词',
        mimoSampleLabel: '样本音频地址',
        mimoHint: 'MiMo 只在音色提供方切换为 MiMo 后生效。保持 DashScope 不变即可继续使用现有链路。',
        mimoSaveVoice: '保存音色',
        mimoSaveNamePrompt: '请输入这个 MiMo 音色的名称',
        mimoSaveConsentWarning: '保存前请确认你已获得该样本音频的使用授权。',
        mimoSaveSuccess: 'MiMo 音色已保存',
        mimoSaveFailed: 'MiMo 音色保存失败',
        previewing: '试听中',
        previewVoice: '试听',
        subtitleTitle: '智能字幕',
        subtitleStyleLabel: '字幕样式',
        subtitlePositionLabel: '字幕位置',
        subtitleFontSizeLabel: '字幕字号',
        subtitleKeywordEnabled: '关键词高亮',
        subtitleKaraokeEnabled: '逐字卡拉 OK',
        subtitleKeywordsPlaceholder: '手动关键词，用逗号分隔',
        subtitleHint: '字幕会以 ASS 样式烧录到最终视频，支持动态换位置和安全区避让。',
        bgmTitle: '背景音乐',
        bgmDrawerHint: '仅引流素材视频生效，可配合口播使用，也可单独作为背景音乐。',
        bgmEnabledSummary: '已开启',
        bgmDisabledSummary: '已关闭',
        bgmSelectLabel: '音乐曲目',
        bgmSelectPlaceholder: '请选择背景音乐',
        bgmUpload: '上传 BGM',
        bgmUploadHint: '支持 MP3/WAV/M4A，最大 20MB',
        bgmSystem: '系统曲库',
        bgmMine: '我的上传',
        bgmVolume: '音量',
        bgmNoAssets: '暂无可用 BGM，可先上传一首背景音乐。',
        bgmLoadError: 'BGM 列表加载失败',
        bgmUploadSuccess: 'BGM 上传成功',
        bgmUploadError: 'BGM 上传失败',
        bgmPlayError: 'BGM 播放失败',
        bgmRequiredWarning: '已开启 BGM，请先选择或上传一首背景音乐',
        openingRequired: '开头黄金3秒素材（可选）',
        openingDrawerHint: '需要固定开头素材时再展开配置。',
        openingNotConfigured: '未配置',
        openingUploaded: '已选择本地视频',
        openingLinked: '已填写视频链接',
        subtitleEnabledSummary: '已开启',
        subtitleDisabledSummary: '已关闭',
        uploadLocalVideo: '上传本地视频',
        uploadHint: '不上传则从 S1_HOOK 黄金3秒素材池随机使用完整视频，支持MP4/MOV，最大100MB',
        or: '或',
        inputVideoLink: '输入开头视频链接',
        videoLinkPlaceholder: '请输入视频链接',
        openingFullVideoHint: '该视频会作为完整素材使用，超过黄金3秒环节目标时长时按环节压缩。',
        openingFullPoolRandomHint: '全素材随机拼接时，该视频固定作为前3秒片头，后续从全部素材中随机拼接。',
        generateVideo: '生成混剪视频',
        generateAudio: '生成音频',
        audioExportCost: '1 积分 / 次',
        audioExporting: '正在生成音频',
        audioExportSuccess: '音频生成成功，已扣除 1 积分。',
        audioExportMissingScript: '请先输入或选择文案后再生成音频。',
        audioExportFailed: '音频生成失败，积分未扣除。',
        downloadAudio: '下载音频',
        estimateTime: '预计生成时间：3-5分钟',
        todayData: '今日数据',
        realtime: '实时更新',
        viewDataDashboard: '查看完整看板',
        courseTitle: '新手教程',
        quickGuide: '快速上手指南',
        viewAllCourses: '查看全部教程',
        analysisEmpty: '请选择素材库并输入 TikTok 对标链接，点击开始分析生成真实结果',
        leadAnalysisEmpty: '引流素材可跳过对标分析，直接在下方输入文案后生成视频',
        productDetected: '识别到产品：',
        coreSellingPoints: '核心卖点：',
        targetAudience: '目标人群：',
        usageScenarios: '使用场景：',
        videoStructure: '视频结构：',
        unidentified: '未识别',
        fallbackSellingTitle: '卖点',
        fallbackSellingDesc: '对标视频中高频出现的转化点',
        fallbackSellingBadge: '重要卖点',
        libraryFallback: '素材库',
        generatedVideos: '生成视频数',
        consumedCredits: '消耗积分',
        mediumLevel: '中',
        highLevel: '高',
        lowLevel: '低',
        scriptFallbackTitle: '文案方案',
        scriptFallbackPoints: '核心卖点｜使用场景｜行动号召',
        languageFallback: '选择语言',
        finishAnalysisWarning: '请先完成对标分析',
        missingReplayAnalysis: '该分析记录缺少链接或素材库，无法回填',
        replayAnalysisSuccess: '已回填分析记录，可直接生成，也可配置可选开头素材',
        missingReplayGeneration: '该生成记录缺少链接或素材库，无法回填',
        replayGenerationSuccessWithUrl: '已回填生成记录，可直接调整后重新生成视频',
        replayGenerationSuccessNeedFile: '已回填生成记录，本地上传的开头视频需要重新选择',
        inputLinkWarning: '请先输入 TikTok 对标链接',
        selectLibraryWarning: '请先选择素材库',
        analysisSuccess: '对标分析已完成，已生成可选文案方案',
        regenerateSuccess: '文案方案已重新生成',
        sampleInfo:
          '请粘贴真实公开视频链接，例如：https://www.tiktok.com/@username/video/真实视频ID',
        selectVoiceWarning: '将使用默认 AI 配音音色',
        voicePlayError: '试听音频播放失败',
        voiceConfigIncompleteWarning: '请先补完整配音配置',
        generationMissingWarning: '请填写 TikTok 链接并选择素材库',
        leadGenerationMissingWarning: '请选择引流素材库并输入引流文案',
        selectScriptWarning: '请先完成对标分析并选择文案方案',
        manualScriptWarning: '请先输入引流文案',
        remoteHookVideo: '远程黄金三秒视频',
        generationCreated: '生成任务已创建，正在执行智能混剪流水线',
        batchGenerate: '批量生成',
        batchGenerateHint: '默认关闭，开启后可多选文案',
        videosPerScript: '每条文案生成',
        estimatedVideos: '预计生成：',
        batchSelectionSummaryPrefix: '已选择 ',
        batchSelectionSummaryMiddle: ' 个文案，每条生成 ',
        batchSelectionSummarySuffix: ' 条，合计 ',
        batchLimitWarning: '单次批量最多生成 30 个视频',
        batchGenerationQueued: '批量任务已加入生成队列',
        batchGenerationCreated: '批量生成已完成',
        batchQueue: '生成队列',
        generationSuccess: '生成完成，可在下方预览或下载视频',
        generationRetrying: '已提交重试，正在重新执行智能混剪流水线',
        precheckFailed: '生成预检未通过',
        precheckBlockedTitle: '素材预检未通过，暂时无法开始生成',
        precheckNoCredit: '任务尚未开始生成，不会消耗生成积分。',
        precheckNeed: '需要',
        precheckCurrent: '当前',
        precheckStatus: '状态',
        precheckSatisfied: '已满足',
        precheckPassed: '预检已通过，可以开始生成',
        fixMaterials: '去处理素材',
        recheck: '重新预检',
        retry: '重试',
        downloadVideo: '下载视频',
        copyVideoLink: '复制链接',
        businessTraceId: '业务流水号',
        copyBusinessTraceId: '复制',
        newGeneration: '再生成一个',
        taskId: '任务编号',
        currentStep: '当前步骤',
        completedStep: '已完成',
        pendingStep: '待执行',
        failureReason: '失败原因',
        copySuccess: '视频链接已复制',
        businessTraceCopySuccess: '业务流水号已复制'
      }
)
const languageOptions = computed(() =>
  targetLanguageOptions.map((item) => ({
    label: isEn.value ? item.en : item.zh,
    value: item.value
  }))
)
const materialPurposeOptions = computed(() => [
  { label: copy.value.ecommerceMaterial, value: MATERIAL_PURPOSE_ECOMMERCE },
  { label: copy.value.leadGenerationMaterial, value: MATERIAL_PURPOSE_LEAD_GENERATION }
])
const clipPlanModeOptions = computed(() => [
  { label: copy.value.clipPlanModeSegmented, value: CLIP_PLAN_MODE_SEGMENTED },
  { label: copy.value.clipPlanModeFullPoolRandom, value: CLIP_PLAN_MODE_FULL_POOL_RANDOM }
])
const analysisProviderOptions = computed(() => [
  { label: copy.value.geminiAnalysis, value: ANALYSIS_PROVIDER_GEMINI }
])
const subtitleFontSizeOptions = computed(() => [
  { label: isEn.value ? 'Small' : '小', value: 'small' },
  { label: isEn.value ? 'Medium' : '中', value: 'medium' },
  { label: isEn.value ? 'Large' : '大', value: 'large' }
])
const localizedOption = (item: { zh: string; en: string }) => (isEn.value ? item.en : item.zh)
const currentSubtitleStyleOption = computed(
  () =>
    subtitleStyleOptions.find((item) => item.value === createForm.subtitleStyle) ||
    subtitleStyleOptions[0]
)
const localizedSubtitleStyleDescription = computed(() =>
  isEn.value ? currentSubtitleStyleOption.value.descEn : currentSubtitleStyleOption.value.descZh
)
const localizedSubtitlePreviewParts = computed(() =>
  isEn.value
    ? currentSubtitleStyleOption.value.previewEn
    : currentSubtitleStyleOption.value.previewZh
)
const subtitlePreviewEmphasisStyles = new Set([
  'yellow_keyword',
  'tiktok_large',
  'promo_bold',
  'neon_pop',
  'yellow_story',
  'price_flash',
  'brand_minimal',
  'comment_bubble'
])
const subtitlePreviewUsesEmphasis = computed(
  () =>
    createForm.subtitleKeywordEnabled &&
    subtitlePreviewEmphasisStyles.has(createForm.subtitleStyle)
)
const subtitlePreviewClasses = computed(() => [
  `is-${createForm.subtitleStyle}`,
  `is-position-${createForm.subtitlePositionMode}`,
  `is-size-${createForm.subtitleFontSize}`,
  {
    'has-keyword-preview': subtitlePreviewUsesEmphasis.value,
    'has-karaoke-preview': createForm.subtitleKaraokeEnabled
  }
])
const summary = ref<any>({})
const libraries = ref<TkMaterialLibraryVO[]>([])
const librariesWithId = computed(
  () =>
    libraries.value.filter((item) => item.id !== undefined) as Array<
      TkMaterialLibraryVO & { id: number }
    >
)
const currentPurposeLibraries = computed(() =>
  librariesWithId.value.filter(
    (item) => normalizeMaterialPurpose(item.materialPurpose) === createForm.materialPurpose
  )
)
const recentTasks = ref<TkGenerationTaskVO[]>([])
const generationSubmittingCount = ref(0)
const generating = computed(() => generationSubmittingCount.value > 0)
const analyzing = ref(false)
const regeneratingScripts = ref(false)
const voicePreviewing = ref(false)
const savingMimoVoice = ref(false)
const audioExporting = ref(false)
const audioExportResult = ref<TkAudioExportTaskVO>()
const audioExportDownloadName = computed(() => {
  const audioUrl = audioExportResult.value?.audioUrl
  const extension = audioUrl?.match(/\.([a-z0-9]+)(?:[?#]|$)/i)?.[1] || 'mp3'
  return `tk-audio-${audioExportResult.value?.id || 'export'}.${extension}`
})
const precheckingGenerationCount = ref(0)
const precheckingGeneration = computed(() => precheckingGenerationCount.value > 0)
const selectedScriptIndex = ref(0)
const selectedBatchScriptIndexes = ref<number[]>([])
const showBatchGenerationControls = false
const batchGenerationEnabled = ref(false)
const videosPerScript = ref(1)
const displayScriptIndexes = ref<number[]>([])
const insightPreviewMode = ref<'zh' | 'original'>('zh')
const scriptPreviewMode = ref<'zh' | 'original'>('zh')
const activeStep = ref(0)
type FlowStepStatus = 'completed' | 'current' | 'pending'
const openingVideoFile = ref<File>()
const openingUploadRef = ref()
const referenceAnalysis = ref<TkReferenceAnalysisVO>()
const hydratingReplay = ref(false)
const creditBalance = ref<TkCreditBalanceVO>({})
const RECHARGE_POPUP_SESSION_KEY = 'tk-dashboard-recharge-popup-shown'
const showRechargeDetail = ref(false)
const rechargeTierRows = [
  { amount: '¥1,000', discount: '8.0折', unitPrice: '¥4.00/积分', credits: '250积分' },
  { amount: '¥5,000', discount: '7.5折', unitPrice: '¥3.75/积分', credits: '1,333积分' },
  { amount: '¥10,000', discount: '6.5折', unitPrice: '¥3.25/积分', credits: '3,077积分' },
  { amount: '¥50,000', discount: '6.0折', unitPrice: '¥3.00/积分', credits: '16,667积分' }
]
const rechargeCostRows = [
  { amount: '¥1,000', cost: '¥8.00', count: '125条' },
  { amount: '¥5,000', cost: '¥7.50', count: '666条' },
  { amount: '¥10,000', cost: '¥6.50', count: '1,538条' },
  { amount: '¥50,000', cost: '¥6.00', count: '8,333条' }
]
const rechargeRuleRows = [
  '积分充值后即时到账',
  '积分有效期自充值日起 6个月，到期未消耗部分自动清零',
  '支持团队多人共用同一积分池',
  '充值后 7天内 如未使用任何积分，可申请全额退款；超过7天视为认可服务，不再退费'
]
const maybeShowRechargeDetailPopup = () => {
  if (!creditBalance.value.lowBalance) {
    return
  }
  if (sessionStorage.getItem(RECHARGE_POPUP_SESSION_KEY)) {
    return
  }
  showRechargeDetail.value = true
  sessionStorage.setItem(RECHARGE_POPUP_SESSION_KEY, '1')
}
const currentGenerationTask = ref<TkGenerationTaskVO>()
const currentGenerationOutputDisplayName = computed(() =>
  currentGenerationTask.value ? buildGenerationOutputDisplayName(currentGenerationTask.value) : ''
)
const currentGenerationOutputDownloadName = computed(() =>
  currentGenerationTask.value ? buildGenerationOutputDownloadName(currentGenerationTask.value) : ''
)
const batchGenerationTasks = ref<TkGenerationTaskStatusVO[]>([])
const hasActiveGenerationTasks = computed(() =>
  batchGenerationTasks.value.some((task) => !isTerminalGenerationStatus(task.status))
)
const precheckFailure = ref<PrecheckFailureState>()
const manualLeadScriptText = ref('')
const voicePreviewAudio = ref<HTMLAudioElement>()
const voicePreviewUrl = ref('')
const voiceManagerVisible = ref(false)
const customVoiceProfiles = ref<TkVoiceProfileVO[]>([])
const bgmAssets = ref<TkBgmAssetVO[]>([])
const bgmLoading = ref(false)
const bgmUploading = ref(false)
const bgmPreviewAudio = ref<HTMLAudioElement>()
const customVoiceOptions = computed(() => customVoiceProfiles.value
  .filter((item) => item.provider !== TTS_PROVIDER_MIMO && item.status === 'READY' && item.enabled)
  .map((item) => ({ label: item.name, value: `custom:${item.id}` })))
const mimoSavedVoiceOptions = computed(() => customVoiceProfiles.value
  .filter((item) => item.provider === TTS_PROVIDER_MIMO && item.status === 'READY' && item.enabled)
  .map((item) => ({
    label: item.tags ? `${item.name} · ${item.tags}` : item.name,
    value: item.id
  })))
const historicalVoiceOptions = ref<Array<{ label: string; value: string }>>([])
const systemBgmOptions = computed(() => bgmAssets.value.filter((item) => item.sourceType === 'SYSTEM'))
const userBgmOptions = computed(() => bgmAssets.value.filter((item) => item.sourceType === 'USER'))
const selectedBgmAsset = computed(() =>
  bgmAssets.value.find((item) => item.id === createForm.bgmAssetId)
)
const getBgmDisplayName = (item?: TkBgmAssetVO) => {
  if (!item) {
    return copy.value.bgmNoAssets
  }
  if (item.sourceType === 'USER' && item.fileUrl) {
    const rawName = item.fileUrl.split('?')[0].split('/').pop() || ''
    try {
      return decodeURIComponent(rawName) || item.name || copy.value.bgmNoAssets
    } catch {
      return rawName || item.name || copy.value.bgmNoAssets
    }
  }
  return item.name || copy.value.bgmNoAssets
}
const bgmConfigSummary = computed(() => {
  if (!isLeadGenerationFlow.value) {
    return copy.value.bgmDrawerHint
  }
  if (!createForm.bgmEnabled) {
    return copy.value.bgmDisabledSummary
  }
  return getBgmDisplayName(selectedBgmAsset.value)
})
const loadCustomVoices = async () => {
  customVoiceProfiles.value = await TkVoiceProfileApi.getList()
}
const ensureDefaultBgmSelection = () => {
  if (!bgmAssets.value.length) {
    createForm.bgmAssetId = undefined
    return
  }
  if (!createForm.bgmAssetId || !bgmAssets.value.some((item) => item.id === createForm.bgmAssetId)) {
    createForm.bgmAssetId = bgmAssets.value[0].id
  }
}
const loadBgmAssets = async () => {
  bgmLoading.value = true
  try {
    bgmAssets.value = await TkBgmAssetApi.getList()
    ensureDefaultBgmSelection()
  } catch (error) {
    message.error(copy.value.bgmLoadError)
  } finally {
    bgmLoading.value = false
  }
}
const selectedVoicePayload = () => {
  if (!isVoiceoverEnabled.value) {
    return { ttsProvider: TTS_PROVIDER_DASHSCOPE }
  }
  if (createForm.ttsProvider === TTS_PROVIDER_MIMO) {
    if (createForm.mimoVoiceProfileId) {
      return {
        ttsProvider: TTS_PROVIDER_MIMO,
        voiceCode: undefined,
        voiceProfileId: createForm.mimoVoiceProfileId,
        mimoVoiceMode: undefined,
        mimoVoiceCode: undefined,
        mimoVoicePrompt: undefined,
        mimoVoiceSampleUrl: undefined
      }
    }
    return {
      ttsProvider: TTS_PROVIDER_MIMO,
      voiceCode: undefined,
      voiceProfileId: undefined,
      mimoVoiceMode: createForm.mimoVoiceMode,
      mimoVoiceCode:
        createForm.mimoVoiceMode === MIMO_VOICE_MODE_PRESET
          ? createForm.mimoVoiceCode.trim()
          : undefined,
      mimoVoicePrompt:
        createForm.mimoVoiceMode === MIMO_VOICE_MODE_DESIGN
          ? createForm.mimoVoicePrompt.trim()
          : undefined,
      mimoVoiceSampleUrl:
        createForm.mimoVoiceMode === MIMO_VOICE_MODE_CLONE
          ? createForm.mimoVoiceSampleUrl.trim()
          : undefined
    }
  }
  const match = /^custom:(\d+)$/.exec(createForm.voiceCode)
  return match
    ? {
        ttsProvider: TTS_PROVIDER_DASHSCOPE,
        voiceProfileId: Number(match[1]),
        voiceCode: undefined,
        mimoVoiceMode: undefined,
        mimoVoiceCode: undefined,
        mimoVoicePrompt: undefined,
        mimoVoiceSampleUrl: undefined
      }
    : {
        ttsProvider: TTS_PROVIDER_DASHSCOPE,
        voiceCode: createForm.voiceCode,
        voiceProfileId: undefined,
        mimoVoiceMode: undefined,
        mimoVoiceCode: undefined,
        mimoVoicePrompt: undefined,
        mimoVoiceSampleUrl: undefined
      }
}

const findReadyCustomVoiceByCode = (voiceCode?: string) =>
  voiceCode
    ? customVoiceProfiles.value.find(
        (item) => item.voiceCode === voiceCode && item.status === 'READY' && item.enabled
      )
    : undefined

const isKnownVoiceCode = (voiceCode: string) =>
  systemVoiceOptions.some((item) => item.value === voiceCode) ||
  customVoiceOptions.value.some((item) => item.value === voiceCode) ||
  historicalVoiceOptions.value.some((item) => item.value === voiceCode)

const ensureHistoricalVoiceOption = (voiceCode?: string, preferredLabel?: string) => {
  if (!voiceCode || isKnownVoiceCode(voiceCode)) {
    return
  }
  const customVoice = findReadyCustomVoiceByCode(voiceCode)
  historicalVoiceOptions.value.push({
    label: preferredLabel || customVoice?.name || copy.value.historicalVoice,
    value: voiceCode
  })
}

const restoreVoiceSelection = async (task: TkGenerationTaskVO) => {
  createForm.ttsProvider = task.ttsProvider || TTS_PROVIDER_DASHSCOPE
  createForm.voiceEnabled = task.voiceEnabled !== false
  createForm.mimoVoiceMode = task.mimoVoiceMode || MIMO_VOICE_MODE_PRESET
  createForm.mimoVoiceCode = task.mimoVoiceCode || defaultMimoVoiceCode
  createForm.mimoVoicePrompt = task.mimoVoicePrompt || ''
  createForm.mimoVoiceSampleUrl = task.mimoVoiceSampleUrl || ''
  createForm.mimoVoiceProfileId = undefined
  if (createForm.ttsProvider === TTS_PROVIDER_MIMO) {
    createForm.voiceCode = defaultVoiceCode
    if (task.voiceProfileId) {
      try {
        if (!customVoiceProfiles.value.length) {
          await loadCustomVoices()
        }
        const customVoice = customVoiceProfiles.value.find(
          (item) =>
            item.id === task.voiceProfileId &&
            item.provider === TTS_PROVIDER_MIMO &&
            item.status === 'READY' &&
            item.enabled
        )
        if (customVoice) {
          createForm.mimoVoiceProfileId = task.voiceProfileId
        }
      } catch {
        // Keep direct MiMo fields when saved voice metadata cannot be loaded.
      }
    }
    return
  }
  if (!createForm.voiceEnabled) {
    createForm.voiceCode = defaultVoiceCode
    return
  }
  if (task.voiceProfileId) {
    try {
      if (!customVoiceProfiles.value.length) {
        await loadCustomVoices()
      }
      const customVoice = customVoiceProfiles.value.find(
        (item) => item.id === task.voiceProfileId && item.status === 'READY' && item.enabled
      )
      if (customVoice) {
        createForm.voiceCode = `custom:${task.voiceProfileId}`
        return
      }
    } catch {
      // Fall back to the stored system voice when custom voice metadata cannot be loaded.
    }
  }
  if (task.voiceCode) {
    try {
      if (!customVoiceProfiles.value.length) {
        await loadCustomVoices()
      }
      const customVoice = findReadyCustomVoiceByCode(task.voiceCode)
      if (customVoice) {
        createForm.voiceCode = `custom:${customVoice.id}`
        return
      }
    } catch {
      // Keep the saved provider voice code available even when custom voice metadata is unavailable.
    }
    ensureHistoricalVoiceOption(
      task.voiceCode,
      (task as TkGenerationTaskVO & { voiceName?: string; voiceProfileName?: string }).voiceName ||
        (task as TkGenerationTaskVO & { voiceName?: string; voiceProfileName?: string }).voiceProfileName
    )
    createForm.voiceCode = task.voiceCode
    return
  }
  createForm.voiceCode = defaultVoiceCode
}
const brokenMaterialCovers = reactive<Record<string | number, boolean>>({})
const createTaskProgress = (): TaskProgressState => ({
  running: false,
  failed: false,
  percent: 0,
  phaseIndex: 0,
  startedAt: 0,
  elapsedText: '00:00'
})
const analysisProgress = reactive<TaskProgressState>(createTaskProgress())
const generationProgress = reactive<TaskProgressState>(createTaskProgress())
const suppressAnalysisProgress = ref(false)
let analysisProgressTimer: number | undefined
let generationProgressTimer: number | undefined
let generationPollingTimer: number | undefined

const createForm = reactive<{
  sourceUrl: string
  libraryId?: number
  ttsProvider: string
  voiceCode: string
  voiceEnabled: boolean
  mimoVoiceProfileId?: number
  mimoVoiceMode: string
  mimoVoiceCode: string
  mimoVoicePrompt: string
  mimoVoiceSampleUrl: string
  targetLanguage: string
  materialPurpose: MaterialPurpose
  productCategoryCode: ProductCategoryCode
  clipPlanMode: ClipPlanMode
  analysisProvider: AnalysisProvider
  referenceDuration?: number
  openingVideoUrl: string
  openingClipStartSecond: number
  openingClipEndSecond: number
  subtitleEnabled: boolean
  subtitleStyle: string
  subtitlePositionMode: string
  subtitleKeywordEnabled: boolean
  subtitleKeywords: string
  subtitleKeywordMode: string
  subtitleKaraokeEnabled: boolean
  subtitleActiveColor: string
  subtitleKeywordColor: string
  subtitleFontSize: string
  bgmEnabled: boolean
  bgmAssetId?: number
  bgmVolume: number
}>({
  sourceUrl: '',
  libraryId: undefined,
  ttsProvider: TTS_PROVIDER_DASHSCOPE,
  voiceCode: defaultVoiceCode,
  voiceEnabled: true,
  mimoVoiceProfileId: undefined,
  mimoVoiceMode: MIMO_VOICE_MODE_PRESET,
  mimoVoiceCode: defaultMimoVoiceCode,
  mimoVoicePrompt: '',
  mimoVoiceSampleUrl: '',
  targetLanguage: defaultTargetLanguage,
  materialPurpose: MATERIAL_PURPOSE_ECOMMERCE,
  productCategoryCode: DEFAULT_PRODUCT_CATEGORY_CODE,
  clipPlanMode: CLIP_PLAN_MODE_SEGMENTED,
  analysisProvider: ANALYSIS_PROVIDER_GEMINI,
  referenceDuration: DEFAULT_TARGET_DURATION,
  openingVideoUrl: '',
  openingClipStartSecond: DEFAULT_OPENING_CLIP_START,
  openingClipEndSecond: DEFAULT_OPENING_CLIP_START + DEFAULT_OPENING_CLIP_DURATION,
  subtitleEnabled: true,
  subtitleStyle: 'classic_white',
  subtitlePositionMode: 'smart_safe',
  subtitleKeywordEnabled: true,
  subtitleKeywords: '',
  subtitleKeywordMode: 'auto_manual',
  subtitleKaraokeEnabled: true,
  subtitleActiveColor: '#35F27A',
  subtitleKeywordColor: '#FFD84D',
  subtitleFontSize: 'medium',
  bgmEnabled: true,
  bgmAssetId: undefined,
  bgmVolume: 0.1
})
const selectedMimoSavedVoice = computed(() =>
  createForm.mimoVoiceProfileId
    ? customVoiceProfiles.value.find((item) => item.id === createForm.mimoVoiceProfileId)
    : undefined
)
const openingConfigExpanded = ref(false)
const subtitleConfigExpanded = ref(false)
const voiceConfigExpanded = ref(false)
const bgmConfigExpanded = ref(false)
const analysisResultExpanded = ref(false)
const currentVoiceLabel = computed(() => {
  const systemVoice = systemVoiceOptions.find((item) => item.value === createForm.voiceCode)
  if (systemVoice) {
    return systemVoice.label
  }
  const customVoice = customVoiceOptions.value.find((item) => item.value === createForm.voiceCode)
  if (customVoice) {
    return customVoice.label
  }
  const historicalVoice = historicalVoiceOptions.value.find(
    (item) => item.value === createForm.voiceCode
  )
  return historicalVoice?.label || copy.value.historicalVoice
})
const currentSubtitlePositionOption = computed(
  () =>
    subtitlePositionOptions.find((item) => item.value === createForm.subtitlePositionMode) ||
    subtitlePositionOptions[0]
)
const currentSubtitleFontSizeOption = computed(
  () =>
    subtitleFontSizeOptions.value.find((item) => item.value === createForm.subtitleFontSize) ||
    subtitleFontSizeOptions.value[1]
)
const estimateLeadVoiceDuration = (text: string) => {
  const normalized = text.trim()
  if (!normalized) {
    return 0
  }
  const cjkCount = (normalized.match(/[\u3400-\u9fff]/g) || []).length
  const wordCount = (normalized.match(/[A-Za-z0-9]+(?:[-'][A-Za-z0-9]+)*/g) || []).length
  const punctuationPause = Math.min(
    8,
    (normalized.match(/[，。！？、,.!?;；:：\n]/g) || []).length * 0.22
  )
  const cjkSeconds = cjkCount / 4.6
  const wordSeconds = wordCount / 2.45
  return Math.max(1, Math.round(cjkSeconds + wordSeconds + punctuationPause))
}
const hasManualLeadScriptText = computed(() => manualLeadScriptText.value.trim().length > 0)
const manualLeadEstimatedVoiceDuration = computed(() =>
  estimateLeadVoiceDuration(manualLeadScriptText.value)
)
const manualLeadVoiceDurationLevel = computed<'normal' | 'warning' | 'danger'>(() => {
  if (!isLeadGenerationManualMode.value || !manualLeadEstimatedVoiceDuration.value) {
    return 'normal'
  }
  const targetDuration = getTargetDuration()
  const ratio = manualLeadEstimatedVoiceDuration.value / targetDuration
  if (ratio > 1.35) {
    return 'danger'
  }
  if (ratio > 1.15 || ratio < 0.7) {
    return 'warning'
  }
  return 'normal'
})
const manualLeadVoiceDurationHint = computed(() => {
  if (!isLeadGenerationManualMode.value) {
    return ''
  }
  const estimatedDuration = manualLeadEstimatedVoiceDuration.value
  if (!estimatedDuration) {
    return copy.value.manualVoiceDurationEmptyHint
  }
  const targetDuration = getTargetDuration()
  if (manualLeadVoiceDurationLevel.value === 'danger') {
    return copy.value.manualVoiceDurationDanger(estimatedDuration, targetDuration)
  }
  if (manualLeadVoiceDurationLevel.value === 'warning') {
    return copy.value.manualVoiceDurationWarning(estimatedDuration, targetDuration)
  }
  return copy.value.manualVoiceDurationNormal(estimatedDuration, targetDuration)
})
const isLeadBlankScriptMode = computed(
  () => isLeadGenerationManualMode.value && !hasManualLeadScriptText.value
)
const isVoiceoverEnabled = computed(
  () => !isLeadGenerationFlow.value || (createForm.voiceEnabled && !isLeadBlankScriptMode.value)
)
const voiceConfigSummary = computed(() => {
  if (!isVoiceoverEnabled.value) {
    return copy.value.voiceDisabledSummary
  }
  if (createForm.ttsProvider === TTS_PROVIDER_MIMO) {
    if (selectedMimoSavedVoice.value) {
      return `${copy.value.ttsProviderMimo} · ${selectedMimoSavedVoice.value.name}`
    }
    if (createForm.mimoVoiceMode === MIMO_VOICE_MODE_PRESET) {
      return `${copy.value.ttsProviderMimo} · ${copy.value.mimoPresetMode} · ${
        createForm.mimoVoiceCode || defaultMimoVoiceCode
      }`
    }
    if (createForm.mimoVoiceMode === MIMO_VOICE_MODE_DESIGN) {
      return `${copy.value.ttsProviderMimo} · ${copy.value.mimoVoiceDesignMode}`
    }
    return `${copy.value.ttsProviderMimo} · ${copy.value.mimoVoiceCloneMode}`
  }
  return `${copy.value.ttsProviderDashscope} · ${currentVoiceLabel.value}`
})
const isMimoPresetReady = computed(
  () => Boolean(createForm.mimoVoiceProfileId) || createForm.ttsProvider !== TTS_PROVIDER_MIMO || createForm.mimoVoiceMode !== MIMO_VOICE_MODE_PRESET || Boolean(createForm.mimoVoiceCode.trim())
)
const isMimoDesignReady = computed(
  () => Boolean(createForm.mimoVoiceProfileId) || createForm.ttsProvider !== TTS_PROVIDER_MIMO || createForm.mimoVoiceMode !== MIMO_VOICE_MODE_DESIGN || Boolean(createForm.mimoVoicePrompt.trim())
)
const isMimoCloneReady = computed(
  () => Boolean(createForm.mimoVoiceProfileId) || createForm.ttsProvider !== TTS_PROVIDER_MIMO || createForm.mimoVoiceMode !== MIMO_VOICE_MODE_CLONE || Boolean(createForm.mimoVoiceSampleUrl.trim())
)
const showSaveMimoVoiceButton = computed(
  () =>
    isVoiceoverEnabled.value &&
    createForm.ttsProvider === TTS_PROVIDER_MIMO &&
    !createForm.mimoVoiceProfileId &&
    createForm.mimoVoiceMode !== MIMO_VOICE_MODE_PRESET
)
const mimoVoiceSaveReady = computed(
  () =>
    showSaveMimoVoiceButton.value &&
    (createForm.mimoVoiceMode === MIMO_VOICE_MODE_DESIGN
      ? Boolean(createForm.mimoVoicePrompt.trim())
      : Boolean(createForm.mimoVoiceSampleUrl.trim()))
)
const voiceConfigReady = computed(() => {
  if (!isVoiceoverEnabled.value) {
    return true
  }
  if (createForm.ttsProvider === TTS_PROVIDER_MIMO) {
    return isMimoPresetReady.value && isMimoDesignReady.value && isMimoCloneReady.value
  }
  return Boolean(createForm.voiceCode)
})
const voicePreviewReady = computed(() => {
  if (!isVoiceoverEnabled.value) {
    return false
  }
  return voiceConfigReady.value
})
const openingConfigSummary = computed(() => {
  if (openingVideoFile.value) {
    return copy.value.openingUploaded
  }
  if (createForm.openingVideoUrl.trim()) {
    return copy.value.openingLinked
  }
  return copy.value.openingNotConfigured
})
const subtitleConfigSummary = computed(() => {
  if (!createForm.subtitleEnabled) {
    return copy.value.subtitleDisabledSummary
  }
  return `${copy.value.subtitleEnabledSummary} · ${localizedOption(
    currentSubtitleStyleOption.value
  )} · ${localizedOption(currentSubtitlePositionOption.value)} · ${
    currentSubtitleFontSizeOption.value.label
  }`
})
const analysisResultRunning = computed(
  () =>
    (analyzing.value && !suppressAnalysisProgress.value) ||
    analysisProgress.running
)
const shouldShowAnalysisResultDrawer = computed(
  () =>
    analysisResultRunning.value ||
    analysisProgress.failed ||
    Boolean(referenceAnalysis.value?.id)
)
const shouldShowAnalysisBody = computed(() => shouldShowAnalysisResultDrawer.value)
const shouldShowReferenceCard = computed(() => Boolean(referencePreviewUrl.value || referenceCoverUrl.value))
const analysisResultStatusText = computed(() => {
  if (analysisProgress.failed) {
    return taskProgressCopy.value.analysisFailed
  }
  if (analysisResultRunning.value) {
    return taskProgressCopy.value.analysisTitle
  }
  return referenceAnalysis.value ? copy.value.analysisDone : copy.value.analysisWaiting
})
const analysisResultIcon = computed(() => {
  if (analysisProgress.failed) {
    return 'ep:warning-filled'
  }
  if (analysisResultRunning.value) {
    return currentAnalysisPhase.value.icon
  }
  return referenceAnalysis.value ? 'ep:circle-check-filled' : 'ep:info-filled'
})
const analysisResultSummary = computed(
  () =>
    `${analysisResultStatusText.value} · ${copy.value.videoDuration}: ${analysisDurationText.value} · ${copy.value.publishTime}: ${analysisPublishTime.value}`
)
const sampleInfoVisible = ref(false)
const analysisValidation = reactive({
  sourceUrl: false,
  libraryId: false
})

const flowSteps = computed(() =>
  isEn.value
    ? [
        { title: 'Analyze link', desc: 'AI reads video content', icon: 'ep:link', tone: 'violet' },
        {
          title: 'Extract insights',
          desc: 'AI finds selling points',
          icon: 'ep:aim',
          tone: 'blue'
        },
        {
          title: 'Generate scripts',
          desc: 'Multiple script options',
          icon: 'ep:document',
          tone: 'indigo'
        },
        {
          title: 'Choose script',
          desc: 'Pick high-converting copy',
          icon: 'ep:check',
          tone: 'purple'
        },
        {
          title: 'Configure video',
          desc: 'Optional hook or S1 random fallback',
          icon: 'ep:video-camera',
          tone: 'cyan'
        },
        {
          title: 'Create video',
          desc: 'AI composes the final video',
          icon: 'ep:cpu',
          tone: 'coral'
        }
      ]
    : [
        { title: '分析对标链接', desc: 'AI识别视频内容', icon: 'ep:link', tone: 'violet' },
        { title: '提炼卖点细节', desc: 'AI提取核心卖点', icon: 'ep:aim', tone: 'blue' },
        { title: '生成文案标题', desc: '输出多个文案方案', icon: 'ep:document', tone: 'indigo' },
        { title: '选择文案方案', desc: '选择高转化文案', icon: 'ep:check', tone: 'purple' },
        {
          title: '配置视频设置',
          desc: '可选开头或 S1 随机兜底',
          icon: 'ep:video-camera',
          tone: 'cyan'
        },
        { title: '生成混剪视频', desc: 'AI自动拼接生成', icon: 'ep:cpu', tone: 'coral' }
      ]
)

const flowStepStatus = (index: number): FlowStepStatus => {
  if (index < activeStep.value) {
    return 'completed'
  }
  if (index === activeStep.value) {
    return 'current'
  }
  return 'pending'
}
const formatFlowStepNumber = (index: number) => String(index + 1).padStart(2, '0')
const flowStepIcon = (step: { icon: string }, index: number) =>
  flowStepStatus(index) === 'completed' ? 'ep:check' : step.icon
const flowStepStatusLabel = (index: number) => {
  const status = flowStepStatus(index)
  if (status === 'completed') {
    return copy.value.completedStep
  }
  if (status === 'current') {
    return copy.value.currentStep
  }
  return copy.value.pendingStep
}

const taskProgressCopy = computed(() =>
  isEn.value
    ? {
        analysisTitle: 'Analyzing reference video',
        generationTitle: 'Creating mixed video',
        generationFailedTitle: 'Generation failed',
        failedBadge: 'Failed',
        elapsed: 'Elapsed',
        analysisHint: 'AI is reading the link and extracting selling points',
        generationHint: 'The AI editing pipeline is running',
        analysisFailed: 'Analysis failed. Check the link and try again',
        analysisPollingTimeout:
          'Analysis is still running. Refresh the page to check the latest status.',
        generationFailed: 'Generation failed. Check materials and try again'
      }
    : {
        analysisTitle: '正在分析对标视频',
        generationTitle: '正在生成混剪视频',
        generationFailedTitle: '生成失败',
        failedBadge: '失败',
        elapsed: '已耗时',
        analysisHint: 'AI正在读取链接并提炼卖点',
        generationHint: 'AI混剪流水线正在执行',
        analysisFailed: '分析失败，请检查链接后重试',
        analysisPollingTimeout: '分析仍在执行，请刷新页面查看最新状态',
        generationFailed: '生成失败，请检查素材后重试'
      }
)

const analysisPhases = computed<TaskPhase[]>(() =>
  isEn.value
    ? [
        {
          label: 'Parse link',
          desc: 'Checking the TikTok link and basic access status',
          percent: 10,
          icon: 'ep:link'
        },
        {
          label: 'Fetch info',
          desc: 'Fetching video duration, cover, and publish metadata',
          percent: 25,
          icon: 'ep:video-camera'
        },
        {
          label: 'Extract frames',
          desc: 'Extracting cover and key visual frames',
          percent: 45,
          icon: 'ep:picture'
        },
        {
          label: 'Find selling points',
          desc: 'Identifying product scenes and core selling points',
          percent: 65,
          icon: 'ep:aim'
        },
        {
          label: 'Build scripts',
          desc: 'Generating analysis notes and script options',
          percent: 85,
          icon: 'ep:document'
        },
        {
          label: 'Done',
          desc: 'Reference analysis completed',
          percent: 100,
          icon: 'ep:circle-check-filled'
        }
      ]
    : [
        { label: '解析链接', desc: '校验 TikTok 链接与访问状态', percent: 10, icon: 'ep:link' },
        {
          label: '获取信息',
          desc: '获取视频时长、封面与发布时间',
          percent: 25,
          icon: 'ep:video-camera'
        },
        { label: '提取画面', desc: '提取封面与关键视觉画面', percent: 45, icon: 'ep:picture' },
        { label: '识别卖点', desc: '识别商品场景与核心卖点', percent: 65, icon: 'ep:aim' },
        { label: '生成结论', desc: '生成分析结论与文案方案', percent: 85, icon: 'ep:document' },
        { label: '完成', desc: '对标分析已完成', percent: 100, icon: 'ep:circle-check-filled' }
      ]
)

const generationPhases = computed<TaskPhase[]>(() =>
  isEn.value
    ? [
        {
          label: 'Validate',
          desc: 'Checking materials, script, voice, and hook fallback',
          percent: 10,
          icon: 'ep:select'
        },
        {
          label: 'Scripts',
          desc: 'Preparing the narration script',
          percent: 30,
          icon: 'ep:document'
        },
        {
          label: 'Voiceover',
          desc: 'Preparing AI voiceover audio',
          percent: 50,
          icon: 'ep:microphone'
        },
        {
          label: 'Materials',
          desc: 'Selecting source materials for the video',
          percent: 65,
          icon: 'ep:film'
        },
        { label: 'Download', desc: 'Downloading selected source materials', percent: 66, icon: 'ep:download' },
        { label: 'Transcode', desc: 'Transcoding and stitching video segments', percent: 72, icon: 'ep:cpu' },
        { label: 'Subtitles', desc: 'Generating subtitle assets', percent: 88, icon: 'ep:document' },
        { label: 'Merge', desc: 'Merging video, voiceover, and background music', percent: 92, icon: 'ep:video-camera' },
        { label: 'Upload', desc: 'Uploading the generated result', percent: 96, icon: 'ep:upload' },
        {
          label: 'Done',
          desc: 'Generated video is ready',
          percent: 100,
          icon: 'ep:circle-check-filled'
        }
      ]
    : [
        {
          label: '校验配置',
          desc: '检查素材、文案、音色与开头兜底',
          percent: 10,
          icon: 'ep:select'
        },
        { label: '生成文案', desc: '准备视频口播文案', percent: 30, icon: 'ep:document' },
        { label: '准备配音', desc: '准备 AI 配音音频', percent: 50, icon: 'ep:microphone' },
        { label: '抽取素材', desc: '为视频选择拼接素材', percent: 65, icon: 'ep:film' },
        { label: '下载素材', desc: '下载已选择的视频素材', percent: 66, icon: 'ep:download' },
        { label: '转码拼接', desc: '转码并拼接视频片段', percent: 72, icon: 'ep:cpu' },
        { label: '生成字幕', desc: '生成字幕资源', percent: 88, icon: 'ep:document' },
        { label: '最终合成', desc: '合成视频、配音和背景音乐', percent: 92, icon: 'ep:video-camera' },
        { label: '上传结果', desc: '上传生成结果', percent: 96, icon: 'ep:upload' },
        { label: '完成', desc: '生成视频已完成', percent: 100, icon: 'ep:circle-check-filled' }
      ]
)

const currentAnalysisPhase = computed(
  () => analysisPhases.value[analysisProgress.phaseIndex] || analysisPhases.value[0]
)
const currentGenerationPhase = computed(
  () => generationPhases.value[generationProgress.phaseIndex] || generationPhases.value[0]
)
const currentGenerationStepDescription = computed(() => currentGenerationPhase.value.desc)
const currentGenerationTaskStepName = computed(() =>
  isEn.value
    ? currentGenerationPhase.value.label
    : currentGenerationTask.value?.currentStep || currentGenerationPhase.value.label
)
const generationDisplayPercent = computed(() =>
  generationProgress.failed
    ? Math.min(
        generationProgress.percent ||
          generationPhases.value[generationProgress.phaseIndex]?.percent ||
          66,
        95
      )
    : generationProgress.percent
)
const precheckSegmentRows = computed(() =>
  (precheckFailure.value?.result.segmentSummary || []).filter(
    (item) => Number(item.requiredDuration || 0) > 0
  )
)

const formatPrecheckSeconds = (value?: number) => `${Math.max(0, Number(value || 0))}s`

const precheckRowStatus = (missingDuration?: number) => {
  const missing = Math.max(0, Number(missingDuration || 0))
  if (!missing) {
    return copy.value.precheckSatisfied
  }
  return isEn.value ? `Missing ${missing}s` : `还差 ${missing}s`
}

const formatElapsed = (startedAt: number) => {
  if (!startedAt) {
    return '00:00'
  }
  const seconds = Math.max(0, Math.floor((Date.now() - startedAt) / 1000))
  const minutesText = String(Math.floor(seconds / 60)).padStart(2, '0')
  const secondsText = String(seconds % 60).padStart(2, '0')
  return `${minutesText}:${secondsText}`
}

const parseAnalysisCreateTime = (value?: string) => {
  if (!value) {
    return 0
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const timestamp = Date.parse(normalized)
  return Number.isFinite(timestamp) ? timestamp : 0
}

const isFreshSuccessfulAnalysis = (
  analysis: TkReferenceAnalysisVO | undefined,
  startedAt: number
) => {
  if (!analysis || analysis.status !== 'SUCCESS') {
    return false
  }
  const createdAt = parseAnalysisCreateTime(analysis.createTime)
  return createdAt > 0 && createdAt >= startedAt - ANALYSIS_RECOVERY_TIME_TOLERANCE_MS
}

const isPendingAnalysis = (analysis: TkReferenceAnalysisVO | undefined) =>
  analysis?.status === 'WAITING' || analysis?.status === 'RUNNING'

const sleep = (duration: number) => new Promise((resolve) => window.setTimeout(resolve, duration))

const syncTaskPhase = (progress: TaskProgressState, phases: TaskPhase[]) => {
  let nextIndex = 0
  phases.forEach((phase, index) => {
    if (progress.percent >= phase.percent) {
      nextIndex = index
    }
  })
  progress.phaseIndex = Math.max(0, nextIndex)
}

const clearTaskTimer = (type: 'analysis' | 'generation') => {
  const timer = type === 'analysis' ? analysisProgressTimer : generationProgressTimer
  if (timer) {
    window.clearInterval(timer)
  }
  if (type === 'analysis') {
    analysisProgressTimer = undefined
  } else {
    generationProgressTimer = undefined
  }
}

const startTaskProgress = (
  progress: TaskProgressState,
  phases: TaskPhase[],
  type: 'analysis' | 'generation',
  holdPercent = 90
) => {
  clearTaskTimer(type)
  Object.assign(progress, createTaskProgress(), {
    running: true,
    percent: phases[0]?.percent || 1,
    startedAt: Date.now()
  })
  syncTaskPhase(progress, phases)
  const timer = window.setInterval(() => {
    progress.elapsedText = formatElapsed(progress.startedAt)
    if (progress.percent >= holdPercent) {
      return
    }
    const step = progress.percent < 35 ? 3 : progress.percent < 70 ? 2 : 1
    progress.percent = Math.min(holdPercent, progress.percent + step)
    syncTaskPhase(progress, phases)
  }, 800)
  if (type === 'analysis') {
    analysisProgressTimer = timer
  } else {
    generationProgressTimer = timer
  }
}

const startGenerationSubmissionProgress = () => {
  clearTaskTimer('generation')
  Object.assign(generationProgress, createTaskProgress(), {
    running: true,
    startedAt: Date.now()
  })
}

const finishTaskProgress = (
  progress: TaskProgressState,
  phases: TaskPhase[],
  type: 'analysis' | 'generation',
  failed = false
) => {
  clearTaskTimer(type)
  progress.elapsedText = formatElapsed(progress.startedAt)
  progress.failed = failed
  if (failed) {
    progress.running = false
    return
  }
  progress.percent = 100
  syncTaskPhase(progress, phases)
  window.setTimeout(() => {
    progress.running = false
  }, 700)
}

const resetTaskProgress = (progress: TaskProgressState, type: 'analysis' | 'generation') => {
  clearTaskTimer(type)
  Object.assign(progress, createTaskProgress())
}

const clearGenerationPolling = () => {
  if (generationPollingTimer) {
    window.clearInterval(generationPollingTimer)
    generationPollingTimer = undefined
  }
}

const generationPhaseIndexByTask = (
  task?: Pick<TkGenerationTaskVO, 'status' | 'currentStepCode'>
) => {
  const detailedMapping: Record<string, number> = {
    RENDER_DOWNLOAD: 4,
    RENDER_TRANSCODE_SEGMENTS: 5,
    RENDER_SUBTITLE: 6,
    RENDER_FINAL_MERGE: 7,
    RENDER_UPLOAD_OSS: 8,
    EXPORTING: 8
  }
  const detailedIndex = detailedMapping[task?.currentStepCode || '']
  if (detailedIndex !== undefined) {
    return detailedIndex
  }
  const mapping: Record<string, number> = {
    PENDING: 0,
    PRECHECKED: 0,
    ANALYZING: 0,
    SCRIPT_READY: 1,
    SCRIPTING: 1,
    VOICE_READY: 2,
    VOICING: 2,
    MATERIAL_MATCHING: 3,
    MATERIAL_MATCHED: 3,
    SUBTITLE_TIMELINE_READY: 3,
    VISUAL_ANALYZED: 3,
    CLIP_PLANNED: 3,
    PLANNING: 3,
    RENDERING: 4,
    EXPORTING: 8,
    SUCCESS: 9,
    FAILED: 0
  }
  return mapping[task?.status || ''] ?? 0
}

const syncGenerationTaskProgress = (task: TkGenerationTaskVO) => {
  currentGenerationTask.value = task
  if (!generationProgress.startedAt) {
    generationProgress.startedAt = Date.now()
  }
  generationProgress.running = task.status !== 'SUCCESS' && task.status !== 'FAILED'
  generationProgress.failed = task.status === 'FAILED'
  generationProgress.phaseIndex = generationPhaseIndexByTask(task)
  const rawPercent = Math.max(0, Math.min(Number(task.progress || 0), 100))
  generationProgress.percent =
    task.status === 'FAILED'
      ? Math.min(rawPercent || generationPhases.value[generationProgress.phaseIndex]?.percent || 75, 95)
      : rawPercent
  generationProgress.elapsedText = formatElapsed(generationProgress.startedAt)
}

const pollGenerationTask = async (taskId: number) => {
  const task = await TkGenerationApi.getGeneration(taskId)
  syncGenerationTaskProgress(task)
  if (task.status === 'SUCCESS') {
    clearGenerationPolling()
    finishTaskProgress(generationProgress, generationPhases.value, 'generation')
    message.success(copy.value.generationSuccess)
    await refreshCreditBalance()
    await getData()
    return
  }
  if (task.status === 'FAILED') {
    clearGenerationPolling()
    finishTaskProgress(generationProgress, generationPhases.value, 'generation', true)
    return
  }
}

const generationStatusText = (status?: string) => {
  const labels: Record<string, string> = isEn.value
    ? {
        PENDING: 'Pending',
        SCRIPTING: 'Scripting',
        VOICING: 'Voice',
        PLANNING: 'Planning',
        RENDERING: 'Rendering',
        SUCCESS: 'Done',
        FAILED: 'Failed'
      }
    : {
        PENDING: '等待中',
        SCRIPTING: '生成文案',
        VOICING: '生成配音',
        PLANNING: '规划素材',
        RENDERING: '渲染中',
        SUCCESS: '已完成',
        FAILED: '失败'
      }
  return status ? labels[status] || status : '-'
}

const friendlyGenerationFailureReason = (task?: TkGenerationTaskVO) => {
  const rawReason = task?.failReason || task?.failCode || taskProgressCopy.value.generationFailed
  const reason = String(rawReason || '')
  const subtitleFailed =
    reason.includes('ASR') ||
    reason.includes('字幕精准对齐') ||
    reason.includes('字幕精準對齊') ||
    reason.includes('subtitle') ||
    reason.includes('Subtitle')
  if (subtitleFailed) {
    return isEn.value
      ? 'Subtitle recognition could not align with the voiceover. Retry generation, or turn subtitles off and generate again.'
      : '字幕识别未能和配音内容对齐，本次视频未生成。请重试，或关闭字幕后重新生成。'
  }
  if (reason.includes('SocketTimeoutException') || reason.includes('Read timed out')) {
    return isEn.value
      ? 'Material download or rendering timed out. Retry after the server finishes the current queue.'
      : '素材下载或渲染等待超时，请稍后重试。'
  }
  return reason
}

const syncGenerationBatchProgress = (tasks: TkGenerationTaskStatusVO[]) => {
  batchGenerationTasks.value = tasks
  const activeTask = getGenerationFocusTask(tasks)
  currentGenerationTask.value = activeTask as TkGenerationTaskVO | undefined
  const totalProgress = tasks.reduce((total, task) => total + Number(task.progress || 0), 0)
  const batchPercent = tasks.length
    ? Math.max(0, Math.min(Math.round(totalProgress / tasks.length), 100))
    : 0
  generationProgress.running = tasks.some((task) => !isTerminalGenerationStatus(task.status))
  generationProgress.failed = tasks.some((task) => task.status === 'FAILED')
  generationProgress.phaseIndex = generationPhaseIndexByTask(activeTask)
  generationProgress.percent = generationProgress.failed ? Math.min(batchPercent || 75, 95) : batchPercent
  generationProgress.elapsedText = formatElapsed(generationProgress.startedAt)
}

const pollGenerationTaskBatch = async (taskIds: number[]) => {
  const tasks = await TkGenerationApi.getGenerationStatusBatch(taskIds)
  syncGenerationBatchProgress(tasks)
  if (!generationProgress.running) {
    clearGenerationPolling()
    await refreshCreditBalance()
    await getData()
    finishTaskProgress(generationProgress, generationPhases.value, 'generation', generationProgress.failed)
    if (!generationProgress.failed) {
      message.success(tasks.length > 1 ? copy.value.batchGenerationCreated : copy.value.generationSuccess)
    }
  }
}

const startGenerationPolling = (taskId: number) => {
  clearGenerationPolling()
  generationPollingTimer = window.setInterval(() => {
    pollGenerationTask(taskId).catch(() => undefined)
  }, 1000)
  pollGenerationTask(taskId).catch(() => undefined)
}

const startGenerationBatchPolling = (taskIds: number[]) => {
  clearGenerationPolling()
  generationPollingTimer = window.setInterval(() => {
    pollGenerationTaskBatch(taskIds).catch(() => undefined)
  }, 1000)
  pollGenerationTaskBatch(taskIds).catch(() => undefined)
}

const resolvePromptTextForGeneration = (script: DashboardScriptOption) =>
  isLeadGenerationManualMode.value ? manualLeadScriptText.value.trim() : script.scriptText || script.title

const createGenerationPayload = (script: DashboardScriptOption): TkGenerationTaskVO => {
  const payload: TkGenerationTaskVO = {
    companyId: selectedLibrary.value?.companyId,
    sourceUrl: createForm.sourceUrl.trim() || undefined,
    libraryId: createForm.libraryId!,
    ...selectedVoicePayload(),
    voiceEnabled: isLeadGenerationFlow.value ? isVoiceoverEnabled.value : true,
    targetLanguage: createForm.targetLanguage,
    materialPurpose: createForm.materialPurpose,
    productCategoryCode: DEFAULT_PRODUCT_CATEGORY_CODE,
    clipPlanMode: supportsClipPlanMode.value ? createForm.clipPlanMode : undefined,
    referenceDuration: getTargetDuration(),
    promptText: resolvePromptTextForGeneration(script),
    ...getBgmPayload(),
    ...getSubtitlePayload()
  }
  if (referenceAnalysis.value?.id) {
    payload.referenceAnalysisId = referenceAnalysis.value.id
  }
  if (script.id) {
    payload.scriptOptionId = script.id
  }
  if (openingVideoFile.value) {
    payload.openingVideoName = openingVideoFile.value.name
  } else if (createForm.openingVideoUrl) {
    payload.openingVideoUrl = createForm.openingVideoUrl
    payload.openingVideoName = copy.value.remoteHookVideo
  }
  return payload
}

const createAudioExportRequestId = () =>
  typeof globalThis.crypto?.randomUUID === 'function'
    ? globalThis.crypto.randomUUID()
    : `audio-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

const handleCreateAudioExport = async () => {
  const script = selectedScriptsForGeneration.value[0]
  const scriptText = script ? resolvePromptTextForGeneration(script).trim() : ''
  if (!scriptText) {
    message.warning(copy.value.audioExportMissingScript)
    return
  }
  if (!isVoiceoverEnabled.value || !voiceConfigReady.value) {
    message.warning(copy.value.voiceConfigIncompleteWarning)
    return
  }
  audioExporting.value = true
  audioExportResult.value = undefined
  try {
    audioExportResult.value = await TkGenerationApi.createAudioExport({
      companyId: selectedLibrary.value?.companyId,
      requestId: createAudioExportRequestId(),
      scriptText,
      ...selectedVoicePayload(),
      targetLanguage: createForm.targetLanguage
    })
    if (audioExportResult.value.status !== 'SUCCESS' || !audioExportResult.value.audioUrl) {
      throw new Error(audioExportResult.value.failReason || copy.value.audioExportFailed)
    }
    await refreshCreditBalance()
    message.success(copy.value.audioExportSuccess)
  } catch (error) {
    message.error(error instanceof Error && error.message ? error.message : copy.value.audioExportFailed)
  } finally {
    audioExporting.value = false
  }
}

const buildPrecheckFailureState = (result: TkGenerationPrecheckRespVO): PrecheckFailureState => {
  const primary = result.errors?.[0]
  return {
    result,
    primary,
    title: primary?.title || copy.value.precheckBlockedTitle,
    message: primary?.message || copy.value.precheckFailed,
    actionHint: primary?.actionHint
  }
}

const precheckGeneration = async (script: DashboardScriptOption) => {
  precheckingGenerationCount.value += 1
  precheckFailure.value = undefined
  try {
    const result = await TkGenerationApi.precheckGeneration(createGenerationPayload(script))
    if (!result.passed) {
      precheckFailure.value = buildPrecheckFailureState(result)
      if (!hasActiveGenerationTasks.value) {
        currentGenerationTask.value = undefined
      }
      generationProgress.percent = Math.max(
        generationProgress.percent,
        generationPhases.value[0]?.percent || 5
      )
      generationProgress.phaseIndex = 0
      message.warning(precheckFailure.value.title || copy.value.precheckFailed)
      return false
    }
    return true
  } finally {
    precheckingGenerationCount.value = Math.max(0, precheckingGenerationCount.value - 1)
  }
}

const goFixPrecheckMaterials = () => {
  const query: Record<string, string> = {
    materialPurpose: createForm.materialPurpose
  }
  if (createForm.libraryId) {
    query.libraryId = String(createForm.libraryId)
  }
  if (precheckFailure.value?.primary?.segmentType) {
    query.segmentType = precheckFailure.value.primary.segmentType
  }
  router.push({ path: '/tk/material-library', query })
}

const handleRecheckGeneration = async () => {
  const script = selectedScript.value
  if (!script?.id) {
    message.warning(copy.value.selectScriptWarning)
    return
  }
  resetTaskProgress(generationProgress, 'generation')
  startTaskProgress(generationProgress, generationPhases.value, 'generation', 15)
  const passed = await precheckGeneration(script)
  if (!passed) {
    finishTaskProgress(generationProgress, generationPhases.value, 'generation', true)
    return
  }
  finishTaskProgress(generationProgress, generationPhases.value, 'generation')
  message.success(copy.value.precheckPassed)
}

const aiAdvantageSlides = computed(() =>
  isEn.value
    ? [
        {
          title: 'AI video editing advantage',
          kicker: 'Material Match',
          desc: 'Automatically match the best material clips',
          image: aiMaterialMatchImage,
          points: ['AI recognizes video scenes', 'Material clips are recommended automatically']
        },
        {
          title: 'Copy insight optimization',
          kicker: 'Copy Insight',
          desc: 'Optimize scripts from multiple conversion angles',
          image: aiCopyOptimizeImage,
          points: ['Extract selling points', 'Generate high-converting options']
        },
        {
          title: 'Natural AI voiceover',
          kicker: 'Voiceover',
          desc: 'Generate voiceover that follows proven short-video pacing',
          image: aiVoiceoverImage,
          points: ['Multiple voices available', 'Audio and visuals are aligned']
        },
        {
          title: 'Production speed boost',
          kicker: 'Auto Production',
          desc: 'Shorten the path from reference analysis to final mixed video',
          image: aiEfficiencyImage,
          points: ['Automated editing workflow', 'Efficient batch generation']
        }
      ]
    : [
        {
          title: 'AI智能混剪优势',
          kicker: 'Random Pick',
          desc: '按环节随机抽取完整素材',
          image: aiMaterialMatchImage,
          points: ['AI识别视频场景', '素材片段自动推荐']
        },
        {
          title: '文案优化提炼',
          kicker: 'Copy Insight',
          desc: '多维度文案优化提点',
          image: aiCopyOptimizeImage,
          points: ['提炼卖点细节', '输出高转化方案']
        },
        {
          title: 'AI配音自然流畅',
          kicker: 'Voiceover',
          desc: '根据爆款节奏生成更自然的配音',
          image: aiVoiceoverImage,
          points: ['多音色可选', '音画节奏自动对齐']
        },
        {
          title: '制作效率大幅提升',
          kicker: 'Auto Production',
          desc: '从对标分析到混剪生成，缩短制作时间',
          image: aiEfficiencyImage,
          points: ['混剪流程自动化', '批量生成更高效']
        }
      ]
)

const courses = computed(() =>
  isEn.value
    ? [
        { title: 'How to copy a TikTok video link', time: '02:30' },
        { title: 'How to choose the 3-second hook', time: '03:15' },
        { title: 'Material library tips', time: '04:20' },
        { title: 'Tips to improve viral potential', time: '05:45' }
      ]
    : [
        { title: '如何复制TikTok视频链接', time: '02:30' },
        { title: '黄金3秒开头怎么选', time: '03:15' },
        { title: '素材库管理技巧', time: '04:20' },
        { title: '提高视频爆款率的Tips', time: '05:45' }
      ]
)

const selectedLibrary = computed(() =>
  currentPurposeLibraries.value.find((item) => item.id === createForm.libraryId)
)

const analysisResults = computed(() => {
  if (!referenceAnalysis.value) {
    return [isLeadGenerationFlow.value ? copy.value.leadAnalysisEmpty : copy.value.analysisEmpty]
  }
  const source =
    insightPreviewMode.value === 'zh'
      ? referenceAnalysis.value.displayAnalysisResultZh || referenceAnalysis.value.analysisResult
      : referenceAnalysis.value.analysisResult
  const parsed = parseStringArray(source)
  if (parsed.length) {
    return parsed
  }
  return [
    `${copy.value.productDetected}${referenceAnalysis.value.productName || copy.value.unidentified}`,
    `${copy.value.coreSellingPoints}${referenceAnalysis.value.coreSellingPoints || '-'}`,
    `${copy.value.targetAudience}${referenceAnalysis.value.targetAudience || '-'}`,
    `${copy.value.usageScenarios}${referenceAnalysis.value.usageScenarios || '-'}`,
    `${copy.value.videoStructure}${referenceAnalysis.value.videoStructure || '-'}`
  ]
})

const analysisDurationText = computed(() => formatDuration(referenceAnalysis.value?.videoDuration))
const analysisPublishTime = computed(() => referenceAnalysis.value?.publishTime || '-')
const referencePreviewUrl = computed(() => referenceAnalysis.value?.resolvedVideoUrl || '')
const referenceCoverUrl = computed(() => referenceAnalysis.value?.coverUrl || '')
function normalizeMaterialPurpose(value?: string): MaterialPurpose {
  return value === MATERIAL_PURPOSE_LEAD_GENERATION
    ? MATERIAL_PURPOSE_LEAD_GENERATION
    : MATERIAL_PURPOSE_ECOMMERCE
}
function normalizeProductCategoryCode(value?: string): ProductCategoryCode {
  const matched = productCategoryItems.find((item) => item.value === value)
  return matched?.value || DEFAULT_PRODUCT_CATEGORY_CODE
}
function normalizeClipPlanMode(value?: string): ClipPlanMode {
  return value === CLIP_PLAN_MODE_FULL_POOL_RANDOM
    ? CLIP_PLAN_MODE_FULL_POOL_RANDOM
    : CLIP_PLAN_MODE_SEGMENTED
}
function resolveClipPlanModeFromRouteConfig(routeConfig?: string): ClipPlanMode {
  if (!routeConfig) {
    return CLIP_PLAN_MODE_SEGMENTED
  }
  try {
    const parsed = JSON.parse(routeConfig) as { clipPlanMode?: string }
    return normalizeClipPlanMode(parsed.clipPlanMode)
  } catch (error) {
    return CLIP_PLAN_MODE_SEGMENTED
  }
}
function normalizeAnalysisProvider(provider?: string): AnalysisProvider {
  if (provider === ANALYSIS_PROVIDER_DASHSCOPE_VIDEO) {
    return ANALYSIS_PROVIDER_GEMINI
  }
  return ANALYSIS_PROVIDER_GEMINI
}
function isManualLeadGenerationSource(value?: string) {
  return Boolean(value?.startsWith(MANUAL_LEAD_GENERATION_SOURCE_PREFIX))
}
const isLeadGenerationFlow = computed(
  () => createForm.materialPurpose === MATERIAL_PURPOSE_LEAD_GENERATION
)
const isEcommerceFlow = computed(() => createForm.materialPurpose === MATERIAL_PURPOSE_ECOMMERCE)
const supportsClipPlanMode = computed(() => isEcommerceFlow.value || isLeadGenerationFlow.value)
const isFullPoolRandomMode = computed(
  () => createForm.clipPlanMode === CLIP_PLAN_MODE_FULL_POOL_RANDOM
)
const isLeadGenerationManualMode = computed(
  () => isLeadGenerationFlow.value && !referenceAnalysis.value?.id
)
const analyzeTitleText = computed(() =>
  isLeadGenerationFlow.value ? copy.value.leadAnalyzeTitle : copy.value.analyzeTitle
)
const analyzeDescText = computed(() =>
  isLeadGenerationFlow.value ? copy.value.leadAnalyzeDesc : copy.value.analyzeDesc
)
const linkPlaceholderText = computed(() =>
  isLeadGenerationFlow.value ? copy.value.leadLinkPlaceholder : copy.value.linkPlaceholder
)
const optionalAnalyzeDisabled = computed(
  () => isLeadGenerationFlow.value && !createForm.sourceUrl.trim()
)

function isAnalysisMatchingCurrentForm(analysis: TkReferenceAnalysisVO | undefined) {
  if (!analysis) {
    return false
  }
  return (
    (analysis.sourceUrl || '').trim() === createForm.sourceUrl.trim() &&
    analysis.libraryId === createForm.libraryId &&
    analysis.targetLanguage === createForm.targetLanguage &&
    normalizeMaterialPurpose(analysis.materialPurpose) === createForm.materialPurpose &&
    normalizeAnalysisProvider(analysis.analysisProvider) === createForm.analysisProvider &&
    Number(analysis.referenceDuration || DEFAULT_TARGET_DURATION) === getTargetDuration()
  )
}
const hasCurrentSuccessfulAnalysis = computed(() => {
  const analysis = referenceAnalysis.value
  if (!analysis || analysis.status !== 'SUCCESS') {
    return false
  }
  return isAnalysisMatchingCurrentForm(analysis)
})
const analyzeButtonText = computed(() =>
  hasCurrentSuccessfulAnalysis.value
    ? copy.value.reanalyze
    : isLeadGenerationFlow.value
      ? copy.value.optionalAnalyze
      : copy.value.startAnalyze
)
const analyzeButtonDisabled = computed(
  () =>
    analyzing.value ||
    generating.value ||
    precheckingGeneration.value ||
    hasActiveGenerationTasks.value
)
const referencePreviewExpired = computed(
  () =>
    referenceAnalysis.value?.status === 'SUCCESS' &&
    !referencePreviewUrl.value &&
    !referenceCoverUrl.value
)

const sellingPoints = computed(() => {
  const source =
    insightPreviewMode.value === 'zh'
      ? referenceAnalysis.value?.displaySellingPointsZh || referenceAnalysis.value?.sellingPoints
      : referenceAnalysis.value?.sellingPoints
  const parsed = parseJsonArray<any>(source)
  if (!parsed.length) {
    return []
  }
  const icons = ['ep:compass', 'ep:wind-power', 'ep:headset', 'ep:timer', 'ep:connection']
  const tones = ['blue', 'violet', 'indigo', 'purple', 'cyan']
  return parsed.slice(0, 5).map((item, index) => ({
    title: item.title || `${copy.value.fallbackSellingTitle}${index + 1}`,
    desc: item.desc || copy.value.fallbackSellingDesc,
    count: item.count || 0,
    badge: item.badge || copy.value.fallbackSellingBadge,
    icon: icons[index % icons.length],
    tone: tones[index % tones.length]
  }))
})

const scriptOptions = computed(() => {
  const options = referenceAnalysis.value?.scriptOptions || []
  if (!options.length) {
    return []
  }
  return options.map((item) => mapScriptOption(item))
})

const manualLeadScriptOption = computed<DashboardScriptOption | undefined>(() => {
  const scriptText = manualLeadScriptText.value.trim()
  if (!isLeadGenerationManualMode.value) {
    return undefined
  }
  return {
    title: copy.value.manualScriptTitle,
    points: copy.value.leadGenerationMaterial,
    originalTitle: copy.value.manualScriptTitle,
    originalPoints: copy.value.leadGenerationMaterial,
    displayTitleZh: copy.value.manualScriptTitle,
    displayPointsZh: copy.value.leadGenerationMaterial,
    rate: '-',
    level: '-',
    levelType: 'mid',
    scriptText,
    displayScriptZh: scriptText
  }
})

const selectedScript = computed(
  () => scriptOptions.value[selectedScriptIndex.value] || scriptOptions.value[0]
)

const selectedScriptsForGeneration = computed(() => {
  if (isLeadGenerationManualMode.value) {
    return manualLeadScriptOption.value ? [manualLeadScriptOption.value] : []
  }
  if (!batchGenerationEnabled.value) {
    return selectedScript.value ? [selectedScript.value] : []
  }
  const indexes = selectedBatchScriptIndexes.value
    .filter((index, position, array) => index >= 0 && array.indexOf(index) === position)
    .sort((left, right) => left - right)
  return indexes.map((index) => scriptOptions.value[index]).filter(Boolean)
})

const plannedGenerationCount = computed(() =>
  selectedScriptsForGeneration.value.length * Number(videosPerScript.value || 1)
)

const finishedBatchTaskCount = computed(
  () =>
    batchGenerationTasks.value.filter(
      (task) => task.status === 'SUCCESS' || task.status === 'FAILED'
    ).length
)

const selectedScriptDisplayTitle = computed(() => {
  const script = selectedScript.value
  if (!script) {
    return ''
  }
  return scriptPreviewMode.value === 'zh'
    ? script.displayTitleZh || script.originalTitle
    : script.originalTitle
})

const selectedScriptDisplayPoints = computed(() => {
  const script = selectedScript.value
  if (!script) {
    return ''
  }
  return scriptPreviewMode.value === 'zh'
    ? script.displayPointsZh || script.originalPoints
    : script.originalPoints
})

const targetLanguageLabel = computed(
  () =>
    languageOptions.value.find((item) => item.value === createForm.targetLanguage)?.label ||
    copy.value.languageFallback
)

const displayScriptOptions = computed<DisplayScriptOption[]>(() => {
  const options = scriptOptions.value
  const count = Math.min(DISPLAY_SCRIPT_COUNT, options.length)
  const indexes = displayScriptIndexes.value.filter(
    (index, position, array) =>
      index >= 0 && index < options.length && array.indexOf(index) === position
  )
  for (let index = 0; indexes.length < count && index < options.length; index++) {
    if (!indexes.includes(index)) {
      indexes.push(index)
    }
  }
  return indexes.slice(0, count).map((sourceIndex) => ({
    ...options[sourceIndex],
    sourceIndex
  }))
})

const displayMaterials = computed(() => {
  return libraries.value.slice(0, 5).map((item) => ({
    id: item.id || item.name,
    name: item.name || copy.value.libraryFallback,
    count: formatNumber(item.videoCount, '0'),
    coverUrl: item.coverUrl || '',
    previewVideoUrl: item.previewVideoUrl || ''
  }))
})

const markMaterialCoverBroken = (id: string | number) => {
  brokenMaterialCovers[id] = true
}

const todayMetrics = computed(() => [
  {
    label: copy.value.generatedVideos,
    value: formatNumber(summary.value.generatedVideoCount, '0'),
    icon: 'ep:video-camera',
    tone: 'blue'
  },
  {
    label: copy.value.consumedCredits,
    value: formatNumber(summary.value.consumedCredits, '0'),
    icon: 'ep:coin',
    tone: 'amber'
  }
])

function formatNumber(value: unknown, fallback: string) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value.toLocaleString('en-US')
  }
  if (typeof value === 'string' && value.trim()) {
    return value
  }
  return fallback
}

function parseJsonArray<T = unknown>(value?: string) {
  if (!value) {
    return [] as T[]
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? (parsed as T[]) : []
  } catch (error) {
    return []
  }
}

function parseStringArray(value?: string) {
  return parseJsonArray<unknown>(value)
    .map((item) => String(item || '').trim())
    .filter(Boolean)
}

function formatDuration(duration?: number) {
  if (!duration || duration <= 0) {
    return '--:--'
  }
  const minutes = Math.floor(duration / 60)
  const seconds = duration % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function getTargetDuration() {
  const duration = Number(createForm.referenceDuration)
  if (!Number.isFinite(duration) || duration <= 0) {
    return DEFAULT_TARGET_DURATION
  }
  return Math.max(MIN_TARGET_DURATION, Math.min(Math.round(duration), MAX_TARGET_DURATION))
}

function normalizeTargetDuration() {
  createForm.referenceDuration = getTargetDuration()
}

function getSubtitlePayload() {
  return {
    subtitleEnabled: isVoiceoverEnabled.value && createForm.subtitleEnabled,
    subtitleStyle: createForm.subtitleStyle,
    subtitlePositionMode: createForm.subtitlePositionMode,
    subtitleKeywordEnabled: createForm.subtitleKeywordEnabled,
    subtitleKeywords: createForm.subtitleKeywords,
    subtitleKeywordMode: createForm.subtitleKeywordMode,
    subtitleKaraokeEnabled: createForm.subtitleKaraokeEnabled,
    subtitleActiveColor: createForm.subtitleActiveColor,
    subtitleKeywordColor: createForm.subtitleKeywordColor,
    subtitleFontSize: createForm.subtitleFontSize
  }
}

function appendSubtitleConfig(formData: FormData) {
  const payload = getSubtitlePayload()
  Object.entries(payload).forEach(([key, value]) => {
    formData.append(key, String(value ?? ''))
  })
}

function getBgmPayload() {
  if (!isLeadGenerationFlow.value || !createForm.bgmEnabled || !createForm.bgmAssetId) {
    return {}
  }
  return {
    bgmEnabled: true,
    bgmAssetId: createForm.bgmAssetId,
    bgmVolume: createForm.bgmVolume
  }
}

function appendBgmConfig(formData: FormData) {
  const payload = getBgmPayload()
  Object.entries(payload).forEach(([key, value]) => {
    formData.append(key, String(value ?? ''))
  })
}

async function ensureBgmReadyForGeneration() {
  if (!isLeadGenerationFlow.value || !createForm.bgmEnabled) {
    return true
  }
  if (!bgmAssets.value.length) {
    await loadBgmAssets()
  } else {
    ensureDefaultBgmSelection()
  }
  if (createForm.bgmAssetId) {
    return true
  }
  bgmConfigExpanded.value = true
  message.warning(copy.value.bgmRequiredWarning)
  return false
}

function mapScriptOption(item: TkReferenceScriptOptionVO): DashboardScriptOption {
  const level = item.conversionLevel || copy.value.mediumLevel
  const rateValue = Number(item.estimatedConversionRate)
  const originalTitle = item.title || `${copy.value.scriptFallbackTitle}${item.optionNo || ''}`
  const originalPoints = item.points || copy.value.scriptFallbackPoints
  const originalScriptText = item.scriptText || originalTitle
  const displayTitleZh = item.displayTitleZh || originalTitle
  const displayPointsZh = item.displayPointsZh || originalPoints
  return {
    id: item.id,
    analysisId: item.analysisId,
    title: insightPreviewMode.value === 'zh' ? displayTitleZh : originalTitle,
    points: insightPreviewMode.value === 'zh' ? displayPointsZh : originalPoints,
    originalTitle,
    originalPoints,
    displayTitleZh,
    displayPointsZh,
    rate: Number.isFinite(rateValue) ? `${rateValue.toFixed(2)}%` : '-',
    level,
    levelType:
      level === '高' || level === 'High'
        ? 'high'
        : level === '中' || level === 'Medium'
          ? 'mid'
          : 'low',
    scriptText: originalScriptText,
    displayScriptZh: item.displayScriptZh || originalScriptText
  }
}
function resetScriptDisplay(selectedIndex = 0) {
  displayScriptIndexes.value = scriptOptions.value
    .map((_, index) => index)
    .slice(0, DISPLAY_SCRIPT_COUNT)
  selectedScriptIndex.value = Math.min(selectedIndex, Math.max(scriptOptions.value.length - 1, 0))
  selectedBatchScriptIndexes.value = selectedScript.value ? [selectedScriptIndex.value] : []
}

function isScriptSelectedForGeneration(index: number) {
  return batchGenerationEnabled.value
    ? selectedBatchScriptIndexes.value.includes(index)
    : selectedScriptIndex.value === index
}

function handleScriptOptionClick(index: number) {
  if (!batchGenerationEnabled.value) {
    selectedScriptIndex.value = index
    selectedBatchScriptIndexes.value = [index]
    return
  }
  if (selectedBatchScriptIndexes.value.includes(index)) {
    selectedBatchScriptIndexes.value = selectedBatchScriptIndexes.value.filter((item) => item !== index)
  } else {
    selectedBatchScriptIndexes.value = [...selectedBatchScriptIndexes.value, index]
  }
  if (!selectedBatchScriptIndexes.value.length) {
    selectedScriptIndex.value = index
  } else if (!selectedBatchScriptIndexes.value.includes(selectedScriptIndex.value)) {
    selectedScriptIndex.value = selectedBatchScriptIndexes.value[0]
  }
}

function pickRandomScriptIndexes(count: number) {
  const indexes = scriptOptions.value.map((_, index) => index)
  for (let index = indexes.length - 1; index > 0; index--) {
    const swapIndex = Math.floor(Math.random() * (index + 1))
    const currentIndex = indexes[index]
    indexes[index] = indexes[swapIndex]
    indexes[swapIndex] = currentIndex
  }
  return indexes.slice(0, count)
}

function isSameIndexBatch(nextIndexes: number[]) {
  return (
    nextIndexes.length === displayScriptIndexes.value.length &&
    nextIndexes.every((index, position) => index === displayScriptIndexes.value[position])
  )
}

function handleChangeScriptBatch() {
  if (!scriptOptions.value.length) {
    message.warning(copy.value.finishAnalysisWarning)
    return
  }
  const count = Math.min(DISPLAY_SCRIPT_COUNT, scriptOptions.value.length)
  let nextIndexes = pickRandomScriptIndexes(count)
  if (isSameIndexBatch(nextIndexes)) {
    nextIndexes = nextIndexes.slice(1).concat(nextIndexes[0])
  }
  displayScriptIndexes.value = nextIndexes
  if (!displayScriptIndexes.value.includes(selectedScriptIndex.value)) {
    selectedScriptIndex.value = displayScriptIndexes.value[0] || 0
  }
}

function consumeReplayPayload() {
  const raw = sessionStorage.getItem(TK_GENERATION_REPLAY_KEY)
  if (!raw) {
    return undefined
  }
  sessionStorage.removeItem(TK_GENERATION_REPLAY_KEY)
  try {
    return JSON.parse(raw) as {
      type?: 'analysis' | 'generation'
      analysis?: TkReferenceAnalysisVO
      generation?: TkGenerationTaskVO
    }
  } catch (error) {
    return undefined
  }
}

function findScriptIndexById(scriptOptionId?: number) {
  if (!scriptOptionId) {
    return 0
  }
  const index = referenceAnalysis.value?.scriptOptions?.findIndex(
    (item) => item.id === scriptOptionId
  )
  return index !== undefined && index >= 0 ? index : 0
}

async function recoverLatestSuccessfulAnalysis(
  sourceUrl: string,
  libraryId: number,
  targetLanguage: string | undefined,
  materialPurpose: MaterialPurpose,
  analysisProvider: AnalysisProvider,
  startedAt: number
) {
  try {
    const latest = await TkReferenceApi.getLatest({
      libraryId,
      sourceUrl,
      targetLanguage,
      materialPurpose,
      analysisProvider
    })
    return isFreshSuccessfulAnalysis(latest, startedAt) ? latest : undefined
  } catch (error) {
    return undefined
  }
}

async function waitForReferenceAnalysis(analysis: TkReferenceAnalysisVO) {
  const analysisId = analysis.id
  if (!analysisId) {
    return analysis
  }
  let current = analysis
  const startedAt = Date.now()
  while (isPendingAnalysis(current)) {
    if (Date.now() - startedAt > ANALYSIS_POLL_TIMEOUT_MS) {
      throw new Error(taskProgressCopy.value.analysisPollingTimeout)
    }
    await sleep(ANALYSIS_POLL_INTERVAL_MS)
    current = await TkReferenceApi.getAnalysis(analysisId)
    referenceAnalysis.value = current
  }
  if (current.status === 'FAILED') {
    throw new Error(current.failReason || taskProgressCopy.value.analysisFailed)
  }
  return current
}

async function hydrateReplayFromAnalysis(analysis: TkReferenceAnalysisVO) {
  if (!analysis.sourceUrl || !analysis.libraryId) {
    message.warning(copy.value.missingReplayAnalysis)
    return
  }

  hydratingReplay.value = true
  try {
    createForm.sourceUrl = analysis.sourceUrl
    createForm.libraryId = analysis.libraryId
    createForm.referenceDuration = analysis.referenceDuration || DEFAULT_TARGET_DURATION
    createForm.voiceCode = createForm.voiceCode || defaultVoiceCode
    createForm.targetLanguage = analysis.targetLanguage || defaultTargetLanguage
    createForm.materialPurpose = normalizeMaterialPurpose(analysis.materialPurpose)
    createForm.productCategoryCode = DEFAULT_PRODUCT_CATEGORY_CODE
    createForm.analysisProvider = normalizeAnalysisProvider(analysis.analysisProvider)
    createForm.openingVideoUrl = ''
    createForm.openingClipStartSecond = DEFAULT_OPENING_CLIP_START
    createForm.openingClipEndSecond = DEFAULT_OPENING_CLIP_START + DEFAULT_OPENING_CLIP_DURATION
    openingVideoFile.value = undefined
    openingUploadRef.value?.clearFiles()
    referenceAnalysis.value = analysis
    resetScriptDisplay()
    activeStep.value = 4
    analysisResultExpanded.value = true
    message.success(copy.value.replayAnalysisSuccess)
  } finally {
    await nextTick()
    hydratingReplay.value = false
  }
}

async function hydrateReplayFromGeneration(task: TkGenerationTaskVO) {
  if (!task.libraryId) {
    message.warning(copy.value.missingReplayGeneration)
    return
  }

  hydratingReplay.value = true
  try {
    const materialPurpose = normalizeMaterialPurpose(task.materialPurpose)
    const taskSourceUrl = task.sourceUrl || ''
    const manualLeadReplay =
      materialPurpose === MATERIAL_PURPOSE_LEAD_GENERATION &&
      (!taskSourceUrl || isManualLeadGenerationSource(taskSourceUrl))

    if (!manualLeadReplay && !taskSourceUrl) {
      message.warning(copy.value.missingReplayGeneration)
      return
    }

    createForm.sourceUrl = isManualLeadGenerationSource(task.sourceUrl) ? '' : (task.sourceUrl || '')
    createForm.libraryId = task.libraryId
    createForm.targetLanguage = task.targetLanguage || defaultTargetLanguage
    createForm.materialPurpose = materialPurpose
    createForm.productCategoryCode = normalizeProductCategoryCode(task.productCategoryCode)
    createForm.clipPlanMode =
      materialPurpose === MATERIAL_PURPOSE_LEAD_GENERATION
        ? resolveClipPlanModeFromRouteConfig(task.generationRouteConfig)
        : CLIP_PLAN_MODE_SEGMENTED
    createForm.analysisProvider = ANALYSIS_PROVIDER_GEMINI
    await restoreVoiceSelection(task)
    createForm.referenceDuration =
      task.referenceDuration || task.targetDuration || DEFAULT_TARGET_DURATION
    createForm.openingVideoUrl = task.openingVideoUrl || ''
    createForm.openingClipStartSecond = task.openingClipStartSecond ?? DEFAULT_OPENING_CLIP_START
    createForm.openingClipEndSecond =
      task.openingClipEndSecond ?? createForm.openingClipStartSecond + DEFAULT_OPENING_CLIP_DURATION
    createForm.bgmEnabled = Boolean(task.bgmEnabled)
    createForm.bgmAssetId = task.bgmAssetId
    createForm.bgmVolume = task.bgmVolume || 0.1
    openingVideoFile.value = undefined
    openingUploadRef.value?.clearFiles()
    manualLeadScriptText.value = ''
    if (manualLeadReplay) {
      await loadBgmAssets()
    }

    if (manualLeadReplay) {
      referenceAnalysis.value = undefined
      manualLeadScriptText.value = (task.promptText || task.scriptText || '').trim()
    } else if (task.referenceAnalysisId) {
      const latest = await TkReferenceApi.getLatest({
        libraryId: task.libraryId,
        sourceUrl: taskSourceUrl,
        targetLanguage: createForm.targetLanguage,
        materialPurpose: createForm.materialPurpose,
        analysisProvider: createForm.analysisProvider
      })
      referenceAnalysis.value =
        latest?.id === task.referenceAnalysisId ? latest : latest || undefined
    } else {
      referenceAnalysis.value = await TkReferenceApi.getLatest({
        libraryId: task.libraryId,
        sourceUrl: taskSourceUrl,
        targetLanguage: createForm.targetLanguage,
        materialPurpose: createForm.materialPurpose,
        analysisProvider: createForm.analysisProvider
      })
    }

    resetScriptDisplay(findScriptIndexById(task.scriptOptionId))
    activeStep.value = referenceAnalysis.value || manualLeadReplay ? 4 : 0
    message.success(
      task.openingVideoUrl
        ? copy.value.replayGenerationSuccessWithUrl
        : copy.value.replayGenerationSuccessNeedFile
    )
  } finally {
    await nextTick()
    hydratingReplay.value = false
  }
}

async function hydrateReplayPayload() {
  const payload = consumeReplayPayload()
  if (!payload) {
    return
  }
  if (payload.type === 'analysis' && payload.analysis) {
    await hydrateReplayFromAnalysis(payload.analysis)
    return
  }
  if (payload.type === 'generation' && payload.generation) {
    await hydrateReplayFromGeneration(payload.generation)
  }
}

const getData = async () => {
  await refreshCreditBalance()
  const data = await TkDashboardApi.getSummary()
  summary.value = data || {}
  libraries.value = data?.libraries || []
  recentTasks.value = data?.recentTasks || []
  await ensureCurrentPurposeLibraries()
  if (!createForm.libraryId && currentPurposeLibraries.value.length) {
    createForm.libraryId = currentPurposeLibraries.value[0].id
  }
  await hydrateReplayPayload()
}

const ensureCurrentPurposeLibraries = async () => {
  if (currentPurposeLibraries.value.length) {
    return
  }
  const page = await TkMaterialApi.getLibraryPage({
    pageNo: 1,
    pageSize: 10,
    materialPurpose: createForm.materialPurpose
  })
  const loadedLibraries = (page.list || []) as TkMaterialLibraryVO[]
  const otherPurposeLibraries = libraries.value.filter(
    (item) => normalizeMaterialPurpose(item.materialPurpose) !== createForm.materialPurpose
  )
  libraries.value = [...otherPurposeLibraries, ...loadedLibraries]
}

const refreshCreditBalance = async () => {
  try {
    creditBalance.value = await TkCreditApi.getBalance()
    maybeShowRechargeDetailPopup()
  } catch (error) {
    creditBalance.value = {}
  }
}

const validateAnalysisForm = () => {
  analysisValidation.sourceUrl = !isLeadGenerationFlow.value && !createForm.sourceUrl.trim()
  analysisValidation.libraryId = !createForm.libraryId
  return !analysisValidation.sourceUrl && !analysisValidation.libraryId
}

const handleAnalyzeButtonClick = async () => {
  await handleAnalyzeLink(false)
}

const handleForceReanalyze = async () => {
  await handleAnalyzeLink(true)
}

const handleAnalyzeLink = async (forceRefresh = false, silentProgress = false) => {
  if (!validateAnalysisForm()) {
    return
  }
  if (isLeadGenerationFlow.value && !createForm.sourceUrl.trim()) {
    return
  }
  sampleInfoVisible.value = false
  createForm.analysisProvider = ANALYSIS_PROVIDER_GEMINI
  const sourceUrl = createForm.sourceUrl.trim()
  const libraryId = createForm.libraryId!

  const analysisStartedAt = Date.now()
  analyzing.value = true
  analysisResultExpanded.value = true
  suppressAnalysisProgress.value = silentProgress
  if (silentProgress) {
    resetTaskProgress(analysisProgress, 'analysis')
  } else {
    startTaskProgress(analysisProgress, analysisPhases.value, 'analysis', 88)
  }
  try {
    referenceAnalysis.value = undefined
    selectedScriptIndex.value = 0
    activeStep.value = 0
    const submittedAnalysis = await TkReferenceApi.analyze({
      companyId: selectedLibrary.value?.companyId,
      sourceUrl,
      libraryId,
      referenceDuration: getTargetDuration(),
      targetLanguage: createForm.targetLanguage,
      materialPurpose: createForm.materialPurpose,
      analysisProvider: createForm.analysisProvider,
      forceRefresh
    })
    referenceAnalysis.value = await waitForReferenceAnalysis(submittedAnalysis)
    resetScriptDisplay()
    activeStep.value = 2
    await refreshCreditBalance()
    if (!silentProgress) {
      finishTaskProgress(analysisProgress, analysisPhases.value, 'analysis')
    }
    message.success(copy.value.analysisSuccess)
    return referenceAnalysis.value
  } catch (error) {
    const recoveredAnalysis = await recoverLatestSuccessfulAnalysis(
      sourceUrl,
      libraryId,
      createForm.targetLanguage,
      createForm.materialPurpose,
      createForm.analysisProvider,
      analysisStartedAt
    )
    if (recoveredAnalysis) {
      referenceAnalysis.value = recoveredAnalysis
      selectedScriptIndex.value = 0
      resetScriptDisplay()
      activeStep.value = 2
      await refreshCreditBalance()
      if (!silentProgress) {
        finishTaskProgress(analysisProgress, analysisPhases.value, 'analysis')
      }
      message.success(copy.value.analysisSuccess)
      return referenceAnalysis.value
    }
    referenceAnalysis.value = undefined
    selectedScriptIndex.value = 0
    displayScriptIndexes.value = []
    activeStep.value = 0
    if (!silentProgress) {
      finishTaskProgress(analysisProgress, analysisPhases.value, 'analysis', true)
    }
    return undefined
  } finally {
    analyzing.value = false
    if (silentProgress) {
      suppressAnalysisProgress.value = false
    }
  }
}

const handleRegenerateScripts = async () => {
  if (!referenceAnalysis.value?.id) {
    message.warning(copy.value.finishAnalysisWarning)
    return
  }

  regeneratingScripts.value = true
  try {
    referenceAnalysis.value = await TkReferenceApi.regenerateScriptOptions(
      referenceAnalysis.value.id,
      { referenceDuration: getTargetDuration() }
    )
    resetScriptDisplay()
    activeStep.value = 2
    message.success(copy.value.regenerateSuccess)
  } finally {
    regeneratingScripts.value = false
  }
}

const handleUseSample = () => {
  sampleInfoVisible.value = !sampleInfoVisible.value
  if (sampleInfoVisible.value) {
    analysisValidation.sourceUrl = false
  }
}

const goDataDashboard = () => {
  router.push({ path: '/tk/data-dashboard' })
}

const createMaterialLibraryQuery = () => {
  const query: Record<string, string> = {
    materialPurpose: createForm.materialPurpose
  }
  if (createForm.libraryId) {
    query.libraryId = String(createForm.libraryId)
  }
  return query
}

const goMaterialLibrary = () => {
  router.push({ path: '/tk/material-library', query: createMaterialLibraryQuery() })
}

const goMaterialLibraryUpload = () => {
  router.push({
    path: '/tk/material-library',
    query: {
      ...createMaterialLibraryQuery(),
      segmentType: 'GENERAL'
    }
  })
}

const stopVoicePreview = () => {
  if (voicePreviewAudio.value) {
    voicePreviewAudio.value.pause()
    voicePreviewAudio.value = undefined
  }
  if (voicePreviewUrl.value) {
    URL.revokeObjectURL(voicePreviewUrl.value)
    voicePreviewUrl.value = ''
  }
}

const stopBgmPreview = () => {
  if (bgmPreviewAudio.value) {
    bgmPreviewAudio.value.pause()
    bgmPreviewAudio.value = undefined
  }
}

const handleSaveMimoVoice = async () => {
  if (!mimoVoiceSaveReady.value) {
    message.warning(copy.value.voiceConfigIncompleteWarning)
    return
  }
  const defaultName =
    createForm.mimoVoiceMode === MIMO_VOICE_MODE_DESIGN
      ? copy.value.mimoVoiceDesignMode
      : copy.value.mimoVoiceCloneMode
  const name = window.prompt(copy.value.mimoSaveNamePrompt, defaultName)
  if (!name?.trim()) {
    return
  }
  savingMimoVoice.value = true
  try {
    const tags =
      createForm.mimoVoiceMode === MIMO_VOICE_MODE_DESIGN
        ? `${copy.value.ttsProviderMimo},${copy.value.mimoVoiceDesignMode}`
        : `${copy.value.ttsProviderMimo},${copy.value.mimoVoiceCloneMode}`
    const id =
      createForm.mimoVoiceMode === MIMO_VOICE_MODE_DESIGN
        ? await TkVoiceProfileApi.createMimoDesign(name.trim(), createForm.mimoVoicePrompt.trim(), tags)
        : await TkVoiceProfileApi.createMimoClone(
            name.trim(),
            window.confirm(copy.value.mimoSaveConsentWarning),
            createForm.mimoVoiceSampleUrl.trim(),
            tags
          )
    await loadCustomVoices()
    createForm.mimoVoiceProfileId = Number(id)
    message.success(copy.value.mimoSaveSuccess)
  } catch (error) {
    message.error(copy.value.mimoSaveFailed)
  } finally {
    savingMimoVoice.value = false
  }
}

const handlePreviewVoice = async () => {
  if (!isVoiceoverEnabled.value) {
    message.info(copy.value.voiceDisabledSummary)
    return
  }
  if (!voiceConfigReady.value) {
    message.warning(copy.value.voiceConfigIncompleteWarning)
    return
  }

  voiceConfigExpanded.value = true
  stopVoicePreview()
  voicePreviewing.value = true
  try {
    const audioBlob = await TkGenerationApi.previewVoice({
      ...selectedVoicePayload(),
      targetLanguage: createForm.targetLanguage
    })
    voicePreviewUrl.value = URL.createObjectURL(audioBlob)
    const audio = new Audio(voicePreviewUrl.value)
    voicePreviewAudio.value = audio
    audio.onended = stopVoicePreview
    audio.onerror = () => {
      stopVoicePreview()
      message.error(copy.value.voicePlayError)
    }
    await audio.play()
  } finally {
    voicePreviewing.value = false
  }
}

const handlePreviewBgm = async () => {
  if (!selectedBgmAsset.value?.fileUrl) {
    return
  }
  stopBgmPreview()
  const audio = new Audio(selectedBgmAsset.value.fileUrl)
  bgmPreviewAudio.value = audio
  audio.volume = Math.max(0.01, Math.min(0.3, createForm.bgmVolume))
  audio.onended = stopBgmPreview
  audio.onerror = () => {
    stopBgmPreview()
    message.error(copy.value.bgmPlayError)
  }
  await audio.play()
}

const handleBgmUploadChange = async (file: any) => {
  const rawFile = file.raw as File | undefined
  if (!rawFile) {
    return
  }
  bgmUploading.value = true
  try {
    const id = Number(await TkBgmAssetApi.upload(rawFile.name, undefined, rawFile))
    await loadBgmAssets()
    createForm.bgmAssetId = id
    createForm.bgmEnabled = true
    message.success(copy.value.bgmUploadSuccess)
  } catch (error) {
    message.error(copy.value.bgmUploadError)
  } finally {
    bgmUploading.value = false
  }
}

const buildOpeningGenerationFormData = (script: DashboardScriptOption) => {
  const formData = new FormData()
  if (createForm.sourceUrl.trim()) {
    formData.append('sourceUrl', createForm.sourceUrl.trim())
  }
  formData.append('libraryId', String(createForm.libraryId))
  if (selectedLibrary.value?.companyId) {
    formData.append('companyId', String(selectedLibrary.value.companyId))
  }
  formData.append('voiceEnabled', String(isLeadGenerationFlow.value ? isVoiceoverEnabled.value : true))
  const voiceSelection = selectedVoicePayload()
  if ('ttsProvider' in voiceSelection && voiceSelection.ttsProvider) {
    formData.append('ttsProvider', voiceSelection.ttsProvider)
  }
  if ('voiceProfileId' in voiceSelection && voiceSelection.voiceProfileId) {
    formData.append('voiceProfileId', String(voiceSelection.voiceProfileId))
  } else if ('voiceCode' in voiceSelection && voiceSelection.voiceCode) {
    formData.append('voiceCode', voiceSelection.voiceCode)
  }
  if ('mimoVoiceMode' in voiceSelection && voiceSelection.mimoVoiceMode) {
    formData.append('mimoVoiceMode', voiceSelection.mimoVoiceMode)
  }
  if ('mimoVoiceCode' in voiceSelection && voiceSelection.mimoVoiceCode) {
    formData.append('mimoVoiceCode', voiceSelection.mimoVoiceCode)
  }
  if ('mimoVoicePrompt' in voiceSelection && voiceSelection.mimoVoicePrompt) {
    formData.append('mimoVoicePrompt', voiceSelection.mimoVoicePrompt)
  }
  if ('mimoVoiceSampleUrl' in voiceSelection && voiceSelection.mimoVoiceSampleUrl) {
    formData.append('mimoVoiceSampleUrl', voiceSelection.mimoVoiceSampleUrl)
  }
  formData.append('targetLanguage', createForm.targetLanguage)
  formData.append('materialPurpose', createForm.materialPurpose)
  formData.append('productCategoryCode', DEFAULT_PRODUCT_CATEGORY_CODE)
  if (supportsClipPlanMode.value) {
    formData.append('clipPlanMode', createForm.clipPlanMode)
  }
  if (referenceAnalysis.value?.id) {
    formData.append('referenceAnalysisId', String(referenceAnalysis.value.id))
  }
  if (script.id) {
    formData.append('scriptOptionId', String(script.id))
  }
  formData.append('referenceDuration', String(getTargetDuration()))
  formData.append('promptText', resolvePromptTextForGeneration(script))
  formData.append('openingVideoName', openingVideoFile.value?.name || 'opening.mp4')
  appendBgmConfig(formData)
  appendSubtitleConfig(formData)
  if (openingVideoFile.value) {
    formData.append('openingVideoFile', openingVideoFile.value)
  }
  return formData
}

const precheckGenerationScripts = async (scripts: DashboardScriptOption[]) => {
  for (const script of scripts) {
    const passed = await precheckGeneration(script)
    if (!passed) {
      return false
    }
  }
  return true
}

const createBatchGenerationTaskIds = async (scripts: DashboardScriptOption[]) => {
  const count = batchGenerationEnabled.value ? Number(videosPerScript.value || 1) : 1
  if (isLeadGenerationManualMode.value) {
    const ids: number[] = []
    for (let index = 0; index < count; index++) {
      if (openingVideoFile.value) {
        ids.push(Number(await TkGenerationApi.createGenerationWithOpening(buildOpeningGenerationFormData(scripts[0]))))
      } else {
        ids.push(Number(await TkGenerationApi.createGeneration(createGenerationPayload(scripts[0]))))
      }
    }
    return ids
  }
  if (openingVideoFile.value) {
    const ids: number[] = []
    for (const script of scripts) {
      for (let index = 0; index < count; index++) {
        ids.push(Number(await TkGenerationApi.createGenerationWithOpening(buildOpeningGenerationFormData(script))))
      }
    }
    return ids
  }
  if (batchGenerationEnabled.value) {
    const payload = createGenerationPayload(scripts[0])
    payload.scriptOptionId = undefined
    payload.scriptOptionIds = scripts.map((script) => script.id).filter(Boolean) as number[]
    payload.videosPerScript = count
    return (await TkGenerationApi.createGenerationBatch(payload)).map(Number)
  }
  return [Number(await TkGenerationApi.createGeneration(createGenerationPayload(scripts[0])))]
}

const getTrackedGenerationTaskIds = () =>
  batchGenerationTasks.value
    .map((task) => Number(task.id))
    .filter((id) => id && !Number.isNaN(id))

const registerGenerationTasks = (taskIds: number[]) => {
  batchGenerationTasks.value = mergeGenerationTasks(
    batchGenerationTasks.value,
    taskIds
  ) as TkGenerationTaskStatusVO[]
  const trackedTaskIds = getTrackedGenerationTaskIds()
  if (trackedTaskIds.length) {
    startGenerationBatchPolling(trackedTaskIds)
  }
}

const handleCreateGeneration = async () => {
  if (!createForm.libraryId || (!isLeadGenerationManualMode.value && !createForm.sourceUrl.trim())) {
    message.warning(
      isLeadGenerationManualMode.value
        ? copy.value.leadGenerationMissingWarning
        : copy.value.generationMissingWarning
    )
    return
  }
  if (isVoiceoverEnabled.value && !voiceConfigReady.value) {
    message.warning(copy.value.voiceConfigIncompleteWarning)
    return
  }
  const startFreshGenerationSession = !hasActiveGenerationTasks.value
  const finishFreshGenerationProgress = (failed = true) => {
    if (startFreshGenerationSession && !hasActiveGenerationTasks.value) {
      finishTaskProgress(generationProgress, generationPhases.value, 'generation', failed)
    }
  }
  generationSubmittingCount.value += 1
  precheckFailure.value = undefined
  if (startFreshGenerationSession) {
    currentGenerationTask.value = undefined
    batchGenerationTasks.value = []
    resetTaskProgress(analysisProgress, 'analysis')
    startGenerationSubmissionProgress()
  }
  try {
    if (!isLeadGenerationManualMode.value && (!referenceAnalysis.value?.id || !selectedScript.value?.id)) {
      await handleAnalyzeLink(false, true)
    }
    const scripts = selectedScriptsForGeneration.value
    if (
      !scripts.length ||
      (!isLeadGenerationManualMode.value &&
        (!referenceAnalysis.value?.id || scripts.some((script) => !script?.id)))
    ) {
      message.warning(copy.value.selectScriptWarning)
      finishFreshGenerationProgress()
      return
    }
    if (plannedGenerationCount.value > 30) {
      message.warning(copy.value.batchLimitWarning)
      finishFreshGenerationProgress()
      return
    }
    if (!(await ensureBgmReadyForGeneration())) {
      finishFreshGenerationProgress()
      return
    }
    const precheckPassed = await precheckGenerationScripts(scripts)
    if (!precheckPassed) {
      finishFreshGenerationProgress()
      return
    }

    const taskIds = (await createBatchGenerationTaskIds(scripts)).filter(
      (id) => id && !Number.isNaN(id)
    )
    if (!taskIds.length) {
      finishFreshGenerationProgress()
      return
    }
    message.success(taskIds.length > 1 ? copy.value.batchGenerationQueued : copy.value.generationCreated)
    registerGenerationTasks(taskIds)
    await refreshCreditBalance()
    activeStep.value = 5
  } catch (error) {
    if (startFreshGenerationSession && !hasActiveGenerationTasks.value) {
      clearGenerationPolling()
      finishTaskProgress(generationProgress, generationPhases.value, 'generation', true)
    }
    throw error
  } finally {
    generationSubmittingCount.value = Math.max(0, generationSubmittingCount.value - 1)
  }
}

const handleRetryGeneration = async () => {
  const taskId = currentGenerationTask.value?.id
  if (!taskId) {
    return
  }
  precheckFailure.value = undefined
  resetTaskProgress(generationProgress, 'generation')
  startGenerationSubmissionProgress()
  await TkGenerationApi.retryGeneration(taskId)
  message.success(copy.value.generationRetrying)
  startGenerationPolling(taskId)
}

const handleCopyGenerationLink = async () => {
  const outputUrl = currentGenerationTask.value?.outputUrl
  if (!outputUrl) {
    return
  }
  await navigator.clipboard.writeText(outputUrl)
  message.success(copy.value.copySuccess)
}

const handleCopyAnalysisTrace = async () => {
  const businessTraceId = referenceAnalysis.value?.businessTraceId
  if (!businessTraceId) {
    return
  }
  await navigator.clipboard.writeText(businessTraceId)
  message.success(copy.value.businessTraceCopySuccess)
}

const clearCurrentGenerationTask = () => {
  currentGenerationTask.value = undefined
  batchGenerationTasks.value = []
  precheckFailure.value = undefined
  resetTaskProgress(generationProgress, 'generation')
}

void aiAdvantageSlides
void courses
void todayMetrics
void handleCreateGeneration

watch(batchGenerationEnabled, (enabled) => {
  if (enabled) {
    selectedBatchScriptIndexes.value = selectedScript.value ? [selectedScriptIndex.value] : []
    return
  }
  selectedBatchScriptIndexes.value = selectedScript.value ? [selectedScriptIndex.value] : []
  videosPerScript.value = 1
})

watch(selectedScriptIndex, (index) => {
  if (!batchGenerationEnabled.value) {
    selectedBatchScriptIndexes.value = [index]
  }
})

watch(
  () => createForm.materialPurpose,
  async () => {
    if (hydratingReplay.value) {
      return
    }
    if (!currentPurposeLibraries.value.some((item) => item.id === createForm.libraryId)) {
      createForm.libraryId = undefined
    }
    await ensureCurrentPurposeLibraries()
    if (!createForm.libraryId && currentPurposeLibraries.value.length) {
      createForm.libraryId = currentPurposeLibraries.value[0].id
    }
    createForm.analysisProvider = ANALYSIS_PROVIDER_GEMINI
    createForm.productCategoryCode = DEFAULT_PRODUCT_CATEGORY_CODE
    createForm.clipPlanMode = CLIP_PLAN_MODE_SEGMENTED
    if (isLeadGenerationFlow.value) {
      await loadBgmAssets()
    } else {
      createForm.voiceEnabled = true
      createForm.subtitleEnabled = true
      stopBgmPreview()
    }
  }
)

watch(
  () => createForm.voiceEnabled,
  (enabled) => {
    if (!isLeadGenerationFlow.value || enabled) {
      return
    }
    createForm.subtitleEnabled = false
    stopVoicePreview()
  }
)

watch(
  [isLeadGenerationManualMode, hasManualLeadScriptText],
  ([manualMode, hasText]) => {
    if (!manualMode || hasText) {
      return
    }
    createForm.voiceEnabled = false
    createForm.subtitleEnabled = false
    stopVoicePreview()
  },
  { immediate: true }
)

const handleOpeningVideoChange = (file: any) => {
  openingVideoFile.value = file.raw
}

const handleOpeningVideoRemove = () => {
  openingVideoFile.value = undefined
}

watch(
  () =>
    [
      createForm.sourceUrl,
      createForm.libraryId,
      createForm.targetLanguage,
      createForm.materialPurpose,
      createForm.referenceDuration
    ] as const,
  ([sourceUrl, libraryId]) => {
    if (sourceUrl.trim()) {
      analysisValidation.sourceUrl = false
      sampleInfoVisible.value = false
    }
    if (libraryId) {
      analysisValidation.libraryId = false
    }
    if (hydratingReplay.value) {
      return
    }
    if (referenceAnalysis.value && !isAnalysisMatchingCurrentForm(referenceAnalysis.value)) {
      referenceAnalysis.value = undefined
      selectedScriptIndex.value = 0
      displayScriptIndexes.value = []
      activeStep.value = 0
      if (!analysisResultRunning.value && !analysisProgress.failed) {
        analysisResultExpanded.value = false
      }
    }
  }
)

watch(
  () => [createForm.voiceCode, createForm.targetLanguage] as const,
  () => stopVoicePreview()
)

watch(
  () => [createForm.bgmAssetId, createForm.bgmVolume, createForm.bgmEnabled] as const,
  () => stopBgmPreview()
)

watch(
  () => createForm.openingVideoUrl,
  (url) => {
    if (!url) {
      createForm.openingClipStartSecond = DEFAULT_OPENING_CLIP_START
      createForm.openingClipEndSecond = DEFAULT_OPENING_CLIP_START + DEFAULT_OPENING_CLIP_DURATION
    }
  }
)

onMounted(() => {
  getData()
  loadCustomVoices().catch(() => undefined)
  if (isLeadGenerationFlow.value) {
    loadBgmAssets().catch(() => undefined)
  }
})
onUnmounted(() => {
  stopVoicePreview()
  stopBgmPreview()
  clearTaskTimer('analysis')
  clearTaskTimer('generation')
  clearGenerationPolling()
})
</script>

<style scoped>
.tk-home {
  min-height: 100%;
  padding: 20px 24px 24px;
  color: #172033;
  background:
    radial-gradient(circle at 4% 0%, rgb(91 111 255 / 8%), transparent 28%),
    radial-gradient(circle at 100% 14%, rgb(65 196 191 / 10%), transparent 30%), #f7f9fd;
}

.home-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.home-header h1 {
  display: flex;
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.25;
  color: #0c1220;
  align-items: center;
  gap: 9px;
}

.home-header h1 span {
  display: inline-flex;
  height: 23px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #5e6bff, #8458ff);
  border-radius: 8px;
  align-items: center;
}

.home-header p {
  margin: 9px 0 0;
  font-size: 14px;
  color: #708099;
}

.credit-status {
  display: inline-flex;
  min-height: 42px;
  padding: 0 14px;
  color: #1e293b;
  background: #fff;
  border: 1px solid #dbe5f2;
  border-radius: 8px;
  align-items: center;
  gap: 8px;
  box-shadow: 0 10px 24px rgb(32 45 84 / 7%);
}

.credit-status strong {
  font-size: 14px;
}

.credit-status em {
  font-size: 12px;
  color: #64748b;
}

.credit-status em {
  font-style: normal;
  font-weight: 700;
  color: #b45309;
}

.credit-status.warning {
  background: #fffbeb;
  border-color: #facc15;
}

.recharge-detail-link {
  height: auto;
  padding: 0;
  font-size: 12px;
  font-weight: 700;
}

.recharge-detail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.recharge-detail section {
  padding: 14px;
  background: #f8fbff;
  border: 1px solid #e3ebf7;
  border-radius: 12px;
}

.recharge-detail h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #172033;
}

.recharge-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.recharge-section-title h3 {
  margin: 0;
}

.recharge-table-wrap {
  overflow-x: auto;
  background: #fff;
  border: 1px solid #e4ebf5;
  border-radius: 10px;
}

.recharge-table {
  width: 100%;
  border-collapse: collapse;
}

.recharge-table th,
.recharge-table td {
  padding: 11px 12px;
  font-size: 13px;
  line-height: 1.45;
  text-align: left;
  border-bottom: 1px solid #edf2f8;
}

.recharge-table th {
  font-weight: 800;
  color: #475569;
  background: #f1f5fb;
}

.recharge-table tr:last-child td {
  border-bottom: 0;
}

.recharge-table td:last-child {
  font-weight: 800;
  color: #2563eb;
}

.recharge-rule-list {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.recharge-rule-list li {
  position: relative;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
}

.recharge-rule-list li::before {
  position: absolute;
  top: 9px;
  left: 2px;
  width: 6px;
  height: 6px;
  content: '';
  background: #4f7cff;
  border-radius: 999px;
}

.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 290px;
  gap: 18px;
  align-items: start;
}

.main-column,
.side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.panel {
  background: rgb(255 255 255 / 92%);
  border: 1px solid #e7edf7;
  border-radius: 8px;
  box-shadow: 0 10px 28px rgb(32 45 84 / 6%);
}

.flow-panel {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  padding: 18px;
  overflow: hidden;
}

.flow-node {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 72px;
  padding: 12px 13px 11px;
  background: #fff;
  border: 1px solid #e8edf8;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgb(15 23 42 / 4%);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease,
    transform 0.2s ease;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 8px 10px;
}

.flow-node.status-current {
  background:
    linear-gradient(135deg, rgb(108 92 231 / 12%), rgb(255 255 255 / 96%)),
    #fff;
  border-color: #7c5cff;
  box-shadow: 0 12px 28px rgb(93 99 255 / 16%);
  transform: translateY(-1px);
}

.flow-node.status-completed {
  background:
    linear-gradient(135deg, rgb(34 197 94 / 10%), rgb(255 255 255 / 96%)),
    #fff;
  border-color: #a7f3d0;
}

.flow-node.status-pending {
  background: #fbfcff;
  border-color: #e8edf8;
  box-shadow: none;
}

.flow-node.status-pending .flow-icon {
  background: #d8e0ee;
  box-shadow: none;
}

.flow-node.status-pending .flow-copy strong,
.flow-node.status-pending .flow-copy span {
  color: #7a879b;
}

.flow-icon,
.metric-icon,
.selling-icon {
  display: grid;
  width: 36px;
  height: 36px;
  color: #fff;
  border-radius: 50%;
  flex: 0 0 auto;
  box-shadow: 0 8px 18px rgb(74 91 180 / 18%);
  place-items: center;
}

.flow-node.status-current .flow-icon {
  animation: flow-current-pulse 1.8s ease-in-out infinite;
}

.flow-node.status-completed .flow-icon {
  background: linear-gradient(135deg, #22c55e, #14b8a6);
}

.flow-step-number {
  position: absolute;
  top: 8px;
  right: 10px;
  font-size: 10px;
  font-weight: 900;
  line-height: 1;
  color: #a3adc2;
}

.flow-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.flow-copy strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 800;
  color: #182236;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-copy span {
  overflow: hidden;
  font-size: 11px;
  color: #708099;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-status-label {
  grid-column: 2;
  justify-self: start;
  padding: 2px 7px;
  font-size: 10px;
  font-weight: 800;
  line-height: 1.4;
  color: #64748b;
  background: #eef3fb;
  border-radius: 999px;
}

.flow-node.status-current .flow-status-label {
  color: #5b45d8;
  background: #eeeaff;
}

.flow-node.status-completed .flow-status-label {
  color: #047857;
  background: #dcfce7;
}

.flow-connector {
  position: absolute;
  right: -17px;
  z-index: 1;
  width: 18px;
  height: 2px;
  background: repeating-linear-gradient(90deg, #cbd5e1 0 4px, transparent 4px 8px);
  border-radius: 999px;
}

.flow-connector.done {
  background: linear-gradient(90deg, #22c55e, #3b82f6);
}

@keyframes flow-current-pulse {
  0%,
  100% {
    box-shadow: 0 8px 18px rgb(93 99 255 / 18%);
  }

  50% {
    box-shadow: 0 10px 24px rgb(93 99 255 / 32%);
  }
}

.top-grid {
  display: grid;
  grid-template-columns: minmax(430px, 1.12fr) minmax(430px, 0.88fr);
  gap: 16px;
}

.bottom-grid {
  display: grid;
  grid-template-columns: minmax(430px, 1.08fr) minmax(430px, 0.92fr);
  gap: 16px;
}

.analysis-panel,
.script-panel,
.insight-panel,
.materials-panel,
.final-panel,
.stats-panel,
.course-panel {
  padding: 16px;
}

.panel-heading {
  margin-bottom: 14px;
}

.panel-heading > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-heading h2,
.side-title h2,
.ai-panel h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: #172033;
}

.panel-heading p,
.course-panel > p {
  margin: 7px 0 0;
  font-size: 13px;
  color: #708099;
}

.step-label {
  display: grid;
  width: 18px;
  height: 18px;
  font-size: 12px;
  font-weight: 800;
  color: #5d63ff;
  background: #edf0ff;
  border-radius: 50%;
  place-items: center;
}

.inline-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.heading-actions {
  margin-left: auto;
}

.inline-heading em {
  font-size: 12px;
  font-style: normal;
  color: #7b89a2;
}

.inline-heading a,
.link-action,
.course-panel a {
  font-size: 13px;
  font-weight: 700;
  color: #5e63ff;
  text-decoration: none;
}

.link-action {
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.compact-heading {
  margin-bottom: 10px;
}

.analysis-filter-row {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr) 132px;
  gap: 8px;
  align-items: end;
  padding: 9px 10px;
  margin-bottom: 10px;
  background: #f8faff;
  border: 1px solid #e5ebf7;
  border-radius: 8px;
}

.analysis-filter-item {
  min-width: 0;
}

.analysis-filter-item span {
  display: block;
  margin-bottom: 5px;
  overflow: hidden;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.25;
  color: #3b4861;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-filter-control,
.analysis-duration-input {
  width: 100%;
}

.analysis-filter-control :deep(.el-select__wrapper),
.analysis-duration-input :deep(.el-input__wrapper) {
  min-height: 32px;
  border-radius: 7px;
  box-shadow: 0 0 0 1px #dfe7f5 inset;
}

.analysis-filter-item.invalid .analysis-filter-control :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px #f87171 inset;
}

.clip-plan-mode-field {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: 132px minmax(260px, 360px);
  gap: 10px;
  align-items: center;
  padding-top: 2px;
}

.clip-plan-mode-field span {
  margin-bottom: 0;
}

.clip-plan-mode-control {
  width: 100%;
}

.clip-plan-mode-control :deep(.el-segmented__item) {
  min-width: 128px;
}

.link-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 116px;
  gap: 12px;
}

.analysis-link-block.invalid .link-input :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f87171 inset;
}

.link-input :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dfe7f5 inset;
}

.primary-action,
.generate-button {
  background: linear-gradient(135deg, #4f5dff, #9b63f5);
  border: 0;
  border-radius: 8px;
  box-shadow: 0 12px 22px rgb(83 92 255 / 24%);
}

.primary-action :deep(.iconify),
.generate-button :deep(.iconify) {
  margin-right: 6px;
}

.sample-button {
  height: 34px;
  margin-top: 10px;
  margin-left: calc(100% - 104px);
  color: #58677f;
  border-color: #dfe7f5;
}

.sample-button.active {
  color: #4f5dff;
  background: #f2f4ff;
  border-color: #cfd6ff;
}

.analysis-field-message {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}

.analysis-field-message :deep(.iconify) {
  flex: 0 0 auto;
  margin-top: 1px;
  font-size: 14px;
}

.analysis-field-message.error {
  color: #b91c1c;
}

.analysis-field-message.info {
  padding: 7px 9px;
  color: #56647c;
  background: #f6f8ff;
  border: 1px solid #e2e8ff;
  border-radius: 7px;
}

.analysis-field-message.info :deep(.iconify) {
  color: #5d63ff;
}

.analysis-body {
  display: grid;
  grid-template-columns: 178px minmax(0, 1fr);
  gap: 12px;
  margin-top: 10px;
}

.analysis-body.single {
  grid-template-columns: minmax(0, 1fr);
}

.reference-card {
  position: relative;
  height: 218px;
  overflow: hidden;
  background: #0c111d;
  border-radius: 8px;
}

.reference-card.empty {
  display: grid;
  background: linear-gradient(145deg, #182033, #0f1728);
  place-items: center;
}

.tiktok-mark {
  position: absolute;
  top: 9px;
  left: 9px;
  z-index: 2;
  display: grid;
  width: 24px;
  height: 24px;
  font-size: 17px;
  font-weight: 800;
  color: #fff;
  background: #050505;
  border-radius: 7px;
  place-items: center;
}

.reference-media {
  width: 100%;
  height: 100%;
  background: #05070c;
  object-fit: cover;
}

.placeholder-media {
  filter: saturate(1.05) contrast(1.03);
}

.reference-expired {
  position: absolute;
  right: 10px;
  bottom: 10px;
  left: 10px;
  z-index: 2;
  display: flex;
  padding: 7px 9px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  color: #dbe7ff;
  background: rgb(15 23 42 / 82%);
  border: 1px solid rgb(148 163 184 / 28%);
  border-radius: 7px;
  align-items: center;
  gap: 6px;
}

.reference-expired :deep(.iconify) {
  flex: 0 0 auto;
  font-size: 15px;
}

.reference-empty {
  display: grid;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #b8c5dc;
  justify-items: center;
}

.reference-empty :deep(.iconify) {
  font-size: 28px;
  color: #7a8caf;
}

.analysis-result-drawer {
  min-width: 0;
  overflow: hidden;
  background: #fbfcff;
  border: 1px solid #dce5f3;
  border-radius: 8px;
}

.analysis-result-drawer.full {
  grid-column: 1 / -1;
}

.analysis-result-head {
  display: grid;
  width: 100%;
  min-height: 52px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  grid-template-columns: minmax(112px, auto) minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.analysis-result-title {
  display: inline-flex;
  min-width: 0;
  font-size: 13px;
  font-weight: 800;
  color: #172033;
  align-items: center;
  gap: 7px;
}

.analysis-result-title :deep(.iconify) {
  flex: 0 0 auto;
  font-size: 16px;
  color: #22a866;
}

.analysis-result-summary {
  overflow: hidden;
  font-size: 12px;
  color: #64748b;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-result-body {
  padding: 0 12px 12px;
  border-top: 1px solid #e8edf7;
}

.analysis-result-body .task-progress-card,
.analysis-result-body .analysis-result {
  margin-top: 12px;
}

.analysis-result {
  min-width: 0;
  padding: 14px 15px;
  background: linear-gradient(180deg, #fbfdff, #f7faff);
  border: 1px solid #dce5f3;
  border-radius: 8px;
}

.result-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  color: #22a866;
}

.analysis-result p {
  margin: 0 0 10px;
  font-size: 12px;
  color: #64748b;
}

.analysis-result p span {
  display: inline-block;
  width: 14px;
}

.analysis-trace-line {
  display: flex;
  min-height: 28px;
  padding: 6px 9px;
  margin: -2px 0 10px;
  font-size: 12px;
  color: #475569;
  background: #eef5ff;
  border-radius: 6px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.analysis-trace-line > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-trace-line :deep(.el-button) {
  flex: 0 0 auto;
  gap: 4px;
  padding: 4px 6px;
}

.analysis-trace-line :deep(.iconify) {
  font-size: 13px;
}

.analysis-result h3 {
  margin: 0 0 7px;
  font-size: 13px;
  font-weight: 800;
  color: #172033;
}

.subsection-title {
  display: flex;
  margin-bottom: 7px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.subsection-title h3 {
  margin-bottom: 0;
}

.analysis-result ul,
.ai-panel ul {
  padding: 0;
  margin: 0;
  list-style: none;
}

.analysis-result li {
  display: flex;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: #52627a;
  align-items: center;
  gap: 6px;
}

.analysis-result li :deep(.iconify) {
  flex: 0 0 auto;
  color: #32b476;
}

.task-progress-card {
  min-width: 0;
  padding: 14px 15px;
  overflow: hidden;
  background: linear-gradient(180deg, #fff, #f7fbff);
  border: 1px solid #dce7f5;
  border-radius: 8px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 80%);
}

.analysis-progress {
  min-height: 218px;
}

.generation-progress {
  width: 100%;
}

.task-progress-card.failed {
  background: #fff7f7;
  border-color: #fecaca;
}

.task-progress-head {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.task-progress-icon {
  display: grid;
  width: 34px;
  height: 34px;
  color: #fff;
  background: linear-gradient(135deg, #2f70ff, #18b6a7);
  border-radius: 8px;
  place-items: center;
}

.task-progress-icon :deep(.iconify) {
  font-size: 18px;
}

.task-progress-head strong {
  display: block;
  overflow: hidden;
  font-size: 14px;
  font-weight: 800;
  color: #172033;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-progress-head p {
  margin: 4px 0 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.35;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-progress-head > span {
  font-size: 22px;
  font-weight: 850;
  line-height: 1;
  color: #0f766e;
  font-variant-numeric: tabular-nums;
}

.task-progress-card.failed .task-progress-icon {
  background: linear-gradient(135deg, #ef4444, #f97316);
}

.task-progress-card.failed .task-progress-head > span {
  color: #dc2626;
}

.task-progress-card :deep(.el-progress-bar__outer) {
  background-color: #e7eef8;
}

.generation-task-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  font-size: 12px;
  color: #526179;
}

.generation-task-detail span {
  padding: 5px 8px;
  background: #f4f7fb;
  border: 1px solid #e0e7f2;
  border-radius: 6px;
}

.generation-result-preview {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.generation-result-preview video {
  width: 100%;
  max-height: 320px;
  background: #0f172a;
  border-radius: 8px;
}

.generation-result-name {
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
}

.generation-result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.generation-failure {
  display: grid;
  padding: 10px;
  margin-top: 12px;
  color: #7f1d1d;
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 8px;
  gap: 8px;
}

.generation-failure p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.generation-failure-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.generation-failure-title-row strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.precheck-action-hint {
  color: #9f1239;
}

.precheck-gap-table {
  display: grid;
  overflow: hidden;
  background: #fff;
  border: 1px solid #fecdd3;
  border-radius: 7px;
}

.precheck-gap-row {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(48px, 0.6fr) minmax(48px, 0.6fr) minmax(
      64px,
      0.8fr
    );
  gap: 6px;
  align-items: center;
  padding: 7px 8px;
  font-size: 12px;
  line-height: 1.35;
  color: #4b5563;
  border-top: 1px solid #ffe4e6;
}

.precheck-gap-row:first-child {
  border-top: 0;
}

.precheck-gap-row.head {
  font-size: 11px;
  font-weight: 800;
  color: #7f1d1d;
  background: #fff7f7;
}

.precheck-gap-row span,
.precheck-gap-row strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.precheck-gap-row strong {
  color: #15803d;
}

.precheck-gap-row.insufficient strong {
  color: #dc2626;
}

.precheck-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.precheck-failure small {
  color: #991b1b;
}

.task-phase-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(78px, 1fr));
  gap: 6px;
  margin-top: 12px;
}

.task-phase-list span {
  min-width: 0;
  padding: 6px 7px;
  overflow: hidden;
  font-size: 11px;
  font-weight: 750;
  color: #6b7a91;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #f1f5fb;
  border: 1px solid #e4ebf5;
  border-radius: 7px;
}

.task-phase-list span.done {
  color: #0f766e;
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.task-phase-list span.active {
  color: #fff;
  background: #2563eb;
  border-color: #2563eb;
}

.task-progress-card.failed .task-phase-list span.active {
  background: #dc2626;
  border-color: #dc2626;
}

.task-progress-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  font-size: 12px;
  color: #71809a;
}

.task-progress-meta span:last-child {
  overflow: hidden;
  font-weight: 700;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.script-table {
  overflow: hidden;
  border: 1px solid #e8edf6;
  border-radius: 8px;
}

.script-head,
.script-row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) 76px 44px 44px;
  align-items: center;
  gap: 8px;
}

.script-head {
  padding: 10px 12px;
  font-size: 12px;
  color: #71809a;
  background: #f7f9fd;
}

.script-head span:first-child {
  grid-column: 2;
}

.script-row {
  width: 100%;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 0;
  border-top: 1px solid #e8edf6;
}

.script-row.selected {
  background: #fbfcff;
}

.radio-dot {
  width: 14px;
  height: 14px;
  border: 1px solid #c8d3e6;
  border-radius: 50%;
}

.script-row.selected .radio-dot {
  border: 4px solid #5a62ff;
}

.batch-generate-box {
  display: grid;
  gap: 10px;
  padding: 12px;
  margin: 4px 0 14px;
  background: #f7f9fd;
  border: 1px solid #e3e9f4;
  border-radius: 8px;
}

.batch-switch-row,
.batch-options-row,
.generation-batch-head,
.generation-batch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.batch-switch-row strong {
  display: block;
  font-size: 13px;
  color: #172033;
}

.batch-switch-row span,
.batch-options-row span,
.batch-options-row em {
  font-size: 12px;
  font-style: normal;
  color: #71809a;
}

.generation-batch-list {
  display: grid;
  gap: 8px;
  padding: 10px;
  margin-top: 10px;
  background: #f8faff;
  border: 1px solid #e3e9f4;
  border-radius: 8px;
}

.generation-batch-head strong,
.generation-batch-head span {
  font-size: 12px;
  color: #52627a;
}

.generation-batch-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 72px;
}

.generation-batch-row span,
.generation-batch-row em {
  font-size: 12px;
  font-style: normal;
  color: #71809a;
}

.script-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.script-title strong,
.script-title small {
  overflow: visible;
  line-height: 1.45;
  text-overflow: initial;
  overflow-wrap: anywhere;
  white-space: normal;
}

.script-title strong {
  font-size: 13px;
  color: #172033;
}

.script-title small {
  font-size: 11px;
  color: #7a879d;
}

.rate {
  font-size: 12px;
  color: #52627a;
}

.level {
  font-size: 12px;
  font-weight: 800;
}

.level-high {
  color: #20a464;
}

.level-mid {
  color: #ef9b18;
}

.level-low {
  color: #8b96aa;
}

.preview-link {
  font-size: 12px;
  font-weight: 700;
  color: #5b63ff;
}

.table-foot {
  display: flex;
  margin-top: 12px;
  font-size: 12px;
  color: #75849b;
  align-items: center;
  justify-content: space-between;
}

.table-foot button {
  display: flex;
  padding: 0;
  font-size: 12px;
  color: #5b63ff;
  cursor: pointer;
  background: transparent;
  border: 0;
  align-items: center;
  gap: 5px;
}

.script-empty,
.panel-empty {
  display: flex;
  min-height: 96px;
  color: #7b879c;
  background: #f8faff;
  border: 1px dashed #d9e2ef;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.script-empty {
  border-top: 1px dashed #d9e2ef;
}

.panel-empty {
  grid-column: 1 / -1;
  border-radius: 8px;
}

.selling-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.selling-card {
  min-width: 0;
  padding: 14px;
  background: linear-gradient(180deg, #fbfcff, #f7f9ff);
  border: 1px solid #e6edf8;
  border-radius: 8px;
}

.selling-card strong,
.material-card strong {
  display: block;
  margin-top: 9px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 800;
  color: #172033;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selling-card span,
.selling-card small,
.material-card span,
.add-material small,
.chosen-script small {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  font-size: 12px;
  color: #708099;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selling-card span,
.chosen-script small {
  overflow: visible;
  line-height: 1.55;
  text-overflow: initial;
  white-space: normal;
}

.manual-script-input {
  width: 100%;
  margin-top: 10px;
}

.selling-card em {
  display: inline-flex;
  padding: 4px 9px;
  margin-top: 11px;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
  color: #5262ff;
  background: #eef0ff;
  border-radius: 999px;
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.material-card,
.material-empty,
.add-material {
  min-width: 0;
  text-align: center;
}

.material-thumb,
.add-material {
  height: 82px;
  overflow: hidden;
  border-radius: 8px;
}

.material-thumb {
  position: relative;
  background: #eef2f7;
}

.material-thumb img,
.material-thumb video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.material-thumb.empty {
  display: flex;
  color: #93a2b7;
  background: #f4f7fb;
  border: 1px dashed #d9e2ef;
  align-items: center;
  justify-content: center;
}

.material-thumb.empty :deep(.iconify) {
  font-size: 26px;
}

.material-empty {
  display: flex;
  grid-column: span 5;
  min-height: 122px;
  color: #708099;
  background: #f8faff;
  border: 1px dashed #dfe7f4;
  border-radius: 8px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.material-empty :deep(.iconify) {
  margin-bottom: 8px;
  font-size: 26px;
  color: #93a2b7;
}

.material-empty span {
  font-size: 13px;
  font-weight: 800;
  color: #172033;
}

.material-empty small {
  display: block;
  margin-top: 6px;
  font-size: 12px;
}

.add-material {
  display: flex;
  width: 100%;
  color: #516077;
  cursor: pointer;
  background: #f8faff;
  border: 1px solid #dfe7f4;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.add-material :deep(.iconify) {
  margin-bottom: 8px;
  font-size: 24px;
  color: #172033;
}

.add-material span {
  font-size: 13px;
  font-weight: 800;
}

.final-panel {
  display: grid;
  grid-template-columns: minmax(0, 0.88fr) minmax(0, 1.12fr);
  gap: 18px;
}

.chosen-script {
  position: relative;
  width: 100%;
  min-height: 216px;
  padding: 16px 16px 42px;
  text-align: left;
  background: linear-gradient(180deg, #fff, #fbfbff);
  border: 1px solid #7168ff;
  border-radius: 8px;
}

.chosen-script strong {
  display: block;
  font-size: 15px;
  line-height: 1.55;
  color: #172033;
}

.chosen-script span {
  display: block;
  margin-top: 12px;
  font-size: 12px;
  color: #61718a;
}

.chosen-script span em {
  font-style: normal;
  font-weight: 800;
  color: #24a668;
}

.script-preview-tabs {
  display: flex;
  padding: 3px;
  margin-top: 12px;
  background: #eef2f8;
  border-radius: 8px;
  gap: 2px;
}

.mini-tabs {
  flex: 0 1 auto;
  max-width: 260px;
  margin-top: 0;
}

.mini-tabs button {
  min-height: 24px;
  padding: 3px 7px;
  font-size: 10px;
}

.script-preview-tabs button {
  min-height: 26px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.3;
  color: #64738c;
  white-space: normal;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
  flex: 1;
}

.script-preview-tabs button.active {
  color: #26314a;
  background: #fff;
  box-shadow: 0 1px 3px rgb(30 42 68 / 12%);
}

.script-preview-text {
  margin: 10px 24px 0 0;
  overflow: visible;
  font-size: 12px;
  line-height: 1.55;
  color: #52627a;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.manual-voice-duration-hint {
  display: flex;
  padding: 9px 10px;
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.5;
  color: #466088;
  background: #eef6ff;
  border: 1px solid #bfdcff;
  border-radius: 8px;
  gap: 6px;
  align-items: flex-start;
}

.manual-voice-duration-hint span {
  display: inline;
  margin-top: 0;
  color: inherit;
}

.manual-voice-duration-hint :deep(.iconify) {
  flex: 0 0 auto;
  margin-top: 1px;
  font-size: 15px;
}

.manual-voice-duration-hint.is-warning {
  color: #8a5d10;
  background: #fff7e6;
  border-color: #ffd891;
}

.manual-voice-duration-hint.is-danger {
  color: #a33b32;
  background: #fff1f0;
  border-color: #ffb4ad;
}

.chosen-script > :deep(.iconify) {
  position: absolute;
  right: 14px;
  bottom: 14px;
  font-size: 18px;
  color: #625cff;
}

.config-box label {
  display: block;
  margin: 12px 0 8px;
  font-size: 12px;
  font-weight: 800;
  color: #172033;
}

.config-relocated-note {
  display: block;
  margin: -2px 0 8px;
  font-size: 12px;
  line-height: 1.5;
  color: #8a97ad;
}

.library-select {
  width: 100%;
}

.duration-input {
  width: 100%;
}

.opening-clip-range {
  margin-top: 12px;
}

.opening-clip-controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.opening-clip-select {
  width: 100%;
}

.voice-select-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.voice-config-body .voice-select-row {
  margin-top: 12px;
}

.voice-provider-row {
  width: 100%;
  margin-bottom: 12px;
}

.voice-provider-row :deep(.el-radio-button) {
  flex: 1;
}

.voice-provider-row :deep(.el-radio-button__inner) {
  width: 100%;
}

.mimo-voice-grid {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.mimo-voice-grid > label,
.voice-config-body > label {
  font-size: 12px;
  font-weight: 600;
  color: #56657c;
}

.voice-preview-row {
  margin-top: 12px;
}

.field-hint {
  display: block;
  margin: -4px 0 4px;
  font-size: 12px;
  color: #7b89a2;
}

.voice-preview-button {
  grid-column: 1 / -1;
  height: 38px;
  min-width: 82px;
  border-radius: 8px;
}

.voice-preview-button :deep(.iconify) {
  margin-right: 4px;
}

.bgm-select-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.bgm-select-row .el-button {
  height: 38px;
  border-radius: 8px;
}

.bgm-select-row .el-button :deep(.iconify),
.bgm-upload :deep(.iconify) {
  margin-right: 4px;
}

.bgm-volume-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) 38px;
  gap: 10px;
  align-items: center;
  margin-top: 12px;
}

.bgm-volume-row span,
.bgm-volume-row em {
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
  color: #4f5f77;
}

.bgm-volume-row em {
  text-align: right;
}

.bgm-upload {
  margin-top: 10px;
}

.config-drawer {
  margin-top: 12px;
  overflow: hidden;
  background: #fbfcff;
  border: 1px solid #e2e9f5;
  border-radius: 8px;
}

.config-drawer-head {
  display: flex;
  width: 100%;
  min-height: 50px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.config-drawer-title {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.config-drawer-title strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.25;
  color: #172033;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-drawer-title small {
  overflow: hidden;
  font-size: 12px;
  line-height: 1.35;
  color: #8390a5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-drawer-meta {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.config-drawer-meta em {
  max-width: 178px;
  padding: 3px 8px;
  overflow: hidden;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
  color: #475569;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #eef4ff;
  border-radius: 999px;
}

.config-drawer-chevron {
  font-size: 15px;
  color: #7a88a1;
  transition: transform 0.18s ease;
}

.config-drawer-chevron.expanded {
  transform: rotate(180deg);
}

.config-drawer-body {
  padding: 0 12px 12px;
  border-top: 1px solid #e8edf7;
}

.config-drawer-body > label:first-child {
  margin-top: 12px;
}

.subtitle-config {
  margin-top: 12px;
}

.subtitle-config-head {
  padding-right: 10px;
}

.subtitle-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.subtitle-switch-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 14px;
  padding-top: 26px;
}

.subtitle-keywords {
  margin-top: 10px;
}

.subtitle-style-preview {
  --subtitle-preview-font-size: 19px;
  display: grid;
  gap: 6px;
  margin-top: 10px;
}

.subtitle-preview-screen {
  position: relative;
  min-height: 158px;
  height: clamp(158px, 18vw, 210px);
  padding: 14px 12px;
  overflow: hidden;
  background: #111827;
  border: 1px solid #dce5f4;
  border-radius: 8px;
}

.subtitle-preview-screen::before {
  position: absolute;
  inset: 12px 18%;
  content: '';
  border: 1px dashed rgba(255, 255, 255, 0.18);
  border-radius: 8px;
}

.subtitle-preview-caption {
  position: absolute;
  left: 50%;
  z-index: 2;
  max-width: calc(100% - 24px);
  min-height: 28px;
  padding: 0 2px;
  font-size: var(--subtitle-preview-font-size);
  font-weight: 900;
  line-height: 1.2;
  color: #fff;
  text-align: center;
  text-shadow:
    0 1px 0 #000,
    1px 0 0 #000,
    0 -1px 0 #000,
    -1px 0 0 #000;
  overflow-wrap: anywhere;
  transform: translateX(-50%);
}

.subtitle-preview-caption strong {
  color: #ffd84d;
}

.subtitle-preview-caption::after {
  position: absolute;
  right: 12%;
  bottom: -5px;
  left: 12%;
  height: 3px;
  content: '';
  background: transparent;
  border-radius: 6px;
}

.subtitle-style-preview.has-karaoke-preview .subtitle-preview-caption::after {
  background: linear-gradient(90deg, #35f27a 0 54%, rgba(255, 255, 255, 0.3) 54% 100%);
}

.subtitle-style-preview small {
  font-size: 12px;
  line-height: 1.45;
  color: #667085;
}

.subtitle-style-preview.is-size-small .subtitle-preview-caption {
  --subtitle-preview-font-size: 16px;
}

.subtitle-style-preview.is-size-medium .subtitle-preview-caption {
  --subtitle-preview-font-size: 19px;
}

.subtitle-style-preview.is-size-large .subtitle-preview-caption {
  --subtitle-preview-font-size: 23px;
}

.subtitle-style-preview.is-classic_white .subtitle-preview-caption strong {
  color: #fff;
}

.subtitle-style-preview.is-yellow_keyword .subtitle-preview-caption {
  font-size: calc(var(--subtitle-preview-font-size) + 1px);
  text-shadow:
    0 2px 0 #111,
    2px 0 0 #111,
    0 -2px 0 #111,
    -2px 0 0 #111;
}

.subtitle-style-preview.is-tiktok_large .subtitle-preview-caption {
  font-size: calc(var(--subtitle-preview-font-size) + 5px);
  text-shadow:
    0 3px 0 #080a12,
    3px 0 0 #080a12,
    0 -3px 0 #080a12,
    -3px 0 0 #080a12,
    2px 2px 0 #00f2ff;
  text-transform: uppercase;
}

.subtitle-style-preview.is-tiktok_large .subtitle-preview-caption strong {
  color: #ff3b8d;
}

.subtitle-style-preview.is-promo_bold .subtitle-preview-caption {
  padding: 7px 12px;
  color: #fff200;
  text-shadow: none;
  background: #e02020;
  border: 2px solid #2b0505;
  border-radius: 6px;
}

.subtitle-style-preview.is-promo_bold .subtitle-preview-caption strong {
  color: #fff;
}

.subtitle-style-preview.is-clean_product .subtitle-preview-screen,
.subtitle-style-preview.is-step_card .subtitle-preview-screen {
  background: #eef4fb;
}

.subtitle-style-preview.is-clean_product .subtitle-preview-caption,
.subtitle-style-preview.is-step_card .subtitle-preview-caption {
  padding: 8px 12px;
  color: #131f2a;
  text-shadow: none;
  background: #f8fbff;
  border: 1px solid #d5e4f0;
  border-radius: 6px;
}

.subtitle-style-preview.is-clean_product .subtitle-preview-caption strong {
  color: #1677ff;
}

.subtitle-style-preview.is-neon_pop .subtitle-preview-caption {
  font-size: calc(var(--subtitle-preview-font-size) + 3px);
  color: #00f2ff;
  text-shadow:
    0 2px 0 #080a12,
    2px 0 0 #080a12,
    0 -2px 0 #080a12,
    -2px 0 0 #080a12,
    0 0 8px #00f2ff;
}

.subtitle-style-preview.is-neon_pop .subtitle-preview-caption strong {
  color: #ff6bc8;
}

.subtitle-style-preview.is-yellow_story .subtitle-preview-caption {
  font-size: calc(var(--subtitle-preview-font-size) + 3px);
  font-style: italic;
  color: #ffe837;
  text-shadow:
    0 2px 0 #151515,
    2px 0 0 #151515,
    0 -2px 0 #151515,
    -2px 0 0 #151515;
}

.subtitle-style-preview.is-yellow_story .subtitle-preview-caption strong {
  color: #ff3d00;
}

.subtitle-style-preview.is-price_flash .subtitle-preview-caption {
  padding: 7px 12px;
  font-size: calc(var(--subtitle-preview-font-size) + 4px);
  color: #ff0;
  text-shadow: none;
  background: #111;
  border: 2px solid #000;
  border-radius: 6px;
}

.subtitle-style-preview.is-price_flash .subtitle-preview-caption strong {
  color: #f60;
}

.subtitle-style-preview.is-step_card .subtitle-preview-caption {
  border-left: 4px solid #0ea5e9;
}

.subtitle-style-preview.is-step_card .subtitle-preview-caption strong {
  color: #0ea5e9;
}

.subtitle-style-preview.is-brand_minimal .subtitle-preview-screen {
  background: #edf2f7;
}

.subtitle-style-preview.is-brand_minimal .subtitle-preview-caption {
  padding: 7px 14px;
  color: #374151;
  text-shadow: none;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  box-shadow: 0 6px 18px rgba(31, 41, 55, 0.12);
}

.subtitle-style-preview.is-brand_minimal .subtitle-preview-caption strong {
  color: #2563eb;
}

.subtitle-style-preview.is-comment_bubble .subtitle-preview-screen {
  background: #15202b;
}

.subtitle-style-preview.is-comment_bubble .subtitle-preview-caption {
  padding: 8px 13px;
  color: #101a22;
  text-shadow: none;
  background: #fff;
  border: 2px solid #d6e1ea;
  border-radius: 8px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.22);
}

.subtitle-style-preview.is-comment_bubble .subtitle-preview-caption::before {
  position: absolute;
  bottom: -8px;
  left: 22px;
  width: 14px;
  height: 14px;
  content: '';
  background: #fff;
  border-right: 2px solid #d6e1ea;
  border-bottom: 2px solid #d6e1ea;
  transform: rotate(45deg);
}

.subtitle-style-preview.is-comment_bubble .subtitle-preview-caption strong {
  color: #1677ff;
}

.subtitle-position-guide span {
  position: absolute;
  z-index: 1;
  width: 48px;
  height: 4px;
  pointer-events: none;
  content: '';
  background: rgba(53, 242, 122, 0.74);
  border-radius: 8px;
  opacity: 0;
  transform: translateX(-50%);
}

.subtitle-position-guide .guide-top {
  top: 19%;
  left: 50%;
}

.subtitle-position-guide .guide-upper {
  top: 31%;
  left: 50%;
}

.subtitle-position-guide .guide-middle {
  top: 64%;
  left: 50%;
}

.subtitle-position-guide .guide-bottom {
  bottom: 16px;
  left: 50%;
}

.subtitle-position-guide .guide-left-lower {
  top: 62%;
  left: 34%;
  width: 42px;
}

.subtitle-style-preview.is-position-smart_safe .subtitle-preview-caption,
.subtitle-style-preview.is-position-fixed_bottom .subtitle-preview-caption {
  bottom: 16px;
}

.subtitle-style-preview.is-position-fixed_middle .subtitle-preview-caption {
  top: 64%;
  bottom: auto;
  transform: translate(-50%, -50%);
}

.subtitle-style-preview.is-position-smart_safe .guide-bottom,
.subtitle-style-preview.is-position-smart_safe .guide-middle,
.subtitle-style-preview.is-position-smart_safe .guide-left-lower {
  opacity: 0.48;
}

.subtitle-style-preview.is-position-fixed_bottom .guide-bottom {
  opacity: 0.58;
}

.subtitle-style-preview.is-position-fixed_middle .guide-middle {
  opacity: 0.58;
}

.subtitle-style-preview.is-position-alternate .subtitle-preview-caption {
  animation: subtitle-preview-alternate 4s steps(1, end) infinite;
}

.subtitle-style-preview.is-position-alternate .guide-top,
.subtitle-style-preview.is-position-alternate .guide-bottom {
  opacity: 0.58;
}

.subtitle-style-preview.is-position-sentence_rotate .subtitle-preview-caption {
  animation: subtitle-preview-rotate 8s steps(1, end) infinite;
}

.subtitle-style-preview.is-position-sentence_rotate .guide-top,
.subtitle-style-preview.is-position-sentence_rotate .guide-upper,
.subtitle-style-preview.is-position-sentence_rotate .guide-middle,
.subtitle-style-preview.is-position-sentence_rotate .guide-bottom {
  opacity: 0.48;
}

.subtitle-style-preview.is-position-random_safe .subtitle-preview-caption {
  animation: subtitle-preview-random 7s steps(1, end) infinite;
}

.subtitle-style-preview.is-position-random_safe .guide-top,
.subtitle-style-preview.is-position-random_safe .guide-upper,
.subtitle-style-preview.is-position-random_safe .guide-middle,
.subtitle-style-preview.is-position-random_safe .guide-bottom {
  opacity: 0.38;
}

@keyframes subtitle-preview-alternate {
  0%,
  49.9% {
    top: auto;
    bottom: 16px;
    transform: translateX(-50%);
  }

  50%,
  100% {
    top: 19%;
    bottom: auto;
    transform: translateX(-50%);
  }
}

@keyframes subtitle-preview-rotate {
  0%,
  24.9% {
    top: 19%;
    bottom: auto;
    transform: translateX(-50%);
  }

  25%,
  49.9% {
    top: 31%;
    bottom: auto;
    transform: translateX(-50%);
  }

  50%,
  74.9% {
    top: 64%;
    bottom: auto;
    transform: translate(-50%, -50%);
  }

  75%,
  100% {
    top: auto;
    bottom: 16px;
    transform: translateX(-50%);
  }
}

@keyframes subtitle-preview-random {
  0%,
  27.9% {
    top: 31%;
    bottom: auto;
    transform: translateX(-50%);
  }

  28%,
  56.9% {
    top: auto;
    bottom: 16px;
    transform: translateX(-50%);
  }

  57%,
  82.9% {
    top: 19%;
    bottom: auto;
    transform: translateX(-50%);
  }

  83%,
  100% {
    top: 64%;
    bottom: auto;
    transform: translate(-50%, -50%);
  }
}

.library-select :deep(.el-select__wrapper),
.opening-clip-select :deep(.el-select__wrapper),
.duration-input :deep(.el-input__wrapper),
.config-box :deep(.el-input__wrapper) {
  min-height: 38px;
  border-radius: 8px;
}

.opening-upload :deep(.el-upload),
.opening-upload :deep(.el-upload-dragger) {
  width: 100%;
}

.opening-upload :deep(.el-upload-dragger) {
  display: flex;
  height: 112px;
  padding: 16px;
  background: #fbfcff;
  border-color: #dce5f4;
  border-radius: 8px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.opening-upload :deep(.iconify) {
  font-size: 28px;
  color: #4b5872;
}

.opening-upload strong {
  margin-top: 7px;
  font-size: 13px;
  color: #172033;
}

.opening-upload small {
  max-width: 290px;
  margin-top: 5px;
  font-size: 11px;
  line-height: 1.45;
  color: #8290a7;
}

.or-line {
  margin: 8px 0 -3px;
  font-size: 12px;
  color: #8290a7;
  text-align: center;
}

.generate-submit {
  grid-column: 1 / -1;
}

.generate-action-row {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.generate-audio-button {
  min-width: 190px;
}

.audio-export-result {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 14px;
}

.audio-export-result audio {
  max-width: min(360px, 100%);
  height: 36px;
}

.generate-submit.progressing {
  min-height: 176px;
}

.generation-repeat-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.generate-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 800;
}

.generate-submit p {
  margin: 10px 0 0;
  font-size: 13px;
  color: #77869d;
  text-align: center;
}

.side-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.side-title span {
  font-size: 12px;
  color: #8290a7;
}

.metric-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 13px 0;
}

.metric-row + .metric-row {
  border-top: 1px solid #edf2f8;
}

.metric-row span {
  display: block;
  font-size: 12px;
  color: #607088;
}

.metric-row strong {
  display: block;
  margin-top: 5px;
  font-size: 20px;
  line-height: 1.1;
  color: #0f172a;
}

@media (max-width: 640px) {
  .generate-action-row,
  .audio-export-result {
    align-items: stretch;
    flex-direction: column;
  }

  .generate-audio-button,
  .generate-button {
    width: 100%;
  }
}

.stats-dashboard-link {
  width: 100%;
  margin-top: 12px;
}

.ai-panel {
  position: relative;
  min-height: 276px;
  padding: 0;
  overflow: hidden;
  color: #fff;
  background: #0d1528;
  border-radius: 8px;
  box-shadow: 0 18px 32px rgb(20 30 55 / 20%);
}

.ai-panel h2 {
  position: relative;
  z-index: 1;
  margin: 6px 0 0;
  font-size: 18px;
  line-height: 1.25;
  color: #fff;
}

.ai-carousel {
  height: 100%;
}

.ai-carousel :deep(.el-carousel__container) {
  overflow: hidden;
  border-radius: 8px;
}

.ai-carousel :deep(.el-carousel__indicators--outside) {
  position: absolute;
  right: 16px;
  bottom: 11px;
  left: auto;
  z-index: 3;
  display: inline-flex;
  transform: none;
}

.ai-carousel :deep(.el-carousel__button) {
  width: 16px;
  height: 3px;
  background: rgb(255 255 255 / 72%);
  border-radius: 999px;
}

.ai-slide {
  position: relative;
  height: 236px;
  overflow: hidden;
  border-radius: 8px;
}

.ai-slide-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ai-slide-shade {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgb(6 10 24 / 86%) 0%, rgb(9 15 33 / 66%) 48%, rgb(9 15 33 / 18%) 100%),
    linear-gradient(0deg, rgb(8 12 26 / 64%) 0%, rgb(8 12 26 / 0%) 54%);
}

.ai-slide-content {
  position: relative;
  z-index: 1;
  display: flex;
  width: min(230px, 86%);
  height: 100%;
  padding: 18px;
  flex-direction: column;
  justify-content: center;
}

.ai-slide-kicker {
  width: fit-content;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
  color: #bff5ff;
  background: rgb(74 213 255 / 16%);
  border: 1px solid rgb(151 230 255 / 32%);
  border-radius: 999px;
}

.ai-slide-content p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: rgb(255 255 255 / 86%);
}

.ai-panel ul {
  position: relative;
  z-index: 1;
  margin-top: 14px;
}

.ai-panel li {
  display: flex;
  margin-top: 8px;
  font-size: 13px;
  color: rgb(255 255 255 / 94%);
  align-items: center;
  gap: 8px;
}

.ai-panel li :deep(.iconify) {
  flex: 0 0 auto;
  color: #7ce8ff;
}

.course-panel > p {
  margin-bottom: 12px;
}

.course-row {
  display: grid;
  padding: 11px 0;
  font-size: 13px;
  color: #4f5f77;
  grid-template-columns: 18px minmax(0, 1fr) 42px;
  gap: 8px;
  align-items: center;
}

.course-row :deep(.iconify) {
  font-size: 14px;
  color: #5d63ff;
}

.course-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-row em {
  font-style: normal;
  color: #65758d;
  text-align: right;
}

.tone-violet {
  background: linear-gradient(135deg, #6657ff, #9078ff);
}

.tone-blue {
  background: linear-gradient(135deg, #5978ff, #73a7ff);
}

.tone-indigo {
  background: linear-gradient(135deg, #4f46e5, #7c83ff);
}

.tone-purple {
  background: linear-gradient(135deg, #a565ff, #c384ff);
}

.tone-cyan {
  background: linear-gradient(135deg, #5ebfe3, #8edff0);
}

.tone-coral {
  background: linear-gradient(135deg, #ff7767, #ff9c74);
}

.tone-amber {
  background: linear-gradient(135deg, #f5bd4f, #ffd476);
}

@media (width <= 1480px) {
  .home-layout {
    grid-template-columns: 1fr;
  }

  .side-column {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (width <= 1180px) {
  .flow-panel,
  .selling-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .flow-connector {
    display: none;
  }

  .top-grid,
  .bottom-grid,
  .final-panel,
  .side-column {
    grid-template-columns: 1fr;
  }
}

@media (width <= 760px) {
  .tk-home {
    padding: 14px;
  }

  .home-header {
    flex-direction: column;
  }

  .home-header h1 {
    flex-wrap: wrap;
    font-size: 24px;
  }

  .flow-panel,
  .selling-grid,
  .material-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .top-grid,
  .bottom-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .analysis-body,
  .analysis-filter-row,
  .link-row {
    grid-template-columns: 1fr;
  }

  .clip-plan-mode-field {
    grid-template-columns: 1fr;
  }

  .sample-button {
    margin-left: 0;
  }

  .script-head,
  .script-row {
    grid-template-columns: 20px minmax(0, 1fr) 56px 32px;
  }

  .script-head span:last-child,
  .preview-link {
    display: none;
  }

  .ai-panel {
    min-height: 252px;
  }

  .ai-slide {
    height: 216px;
  }

  .ai-slide-content {
    width: min(250px, 90%);
    padding: 16px;
  }
}
</style>
