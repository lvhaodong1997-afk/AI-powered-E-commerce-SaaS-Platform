package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultTkKeywordHighlightService implements TkKeywordHighlightService {

    private static final Pattern PRICE_PATTERN = Pattern.compile("([￥$€£]?\\d+(?:\\.\\d+)?\\s?(?:元|块|折|%|off|OFF)?)");
    private static final String[] SELLING_WORDS = {
            "显瘦", "防晒", "轻薄", "透气", "舒适", "百搭", "爆款", "新品", "优惠", "买一送一",
            "限时", "免运费", "TikTok", "Shop", "discount", "sale", "new", "free shipping",
            "comfortable", "breathable", "support", "easy", "fast", "quick", "limited", "offer"
    };
    private static final String[] SOCIAL_HOOK_WORDS = {
            "stop", "secret", "finally", "why", "pov", "fast", "easy", "quick", "before", "after",
            "别再", "秘密", "终于", "反差", "立刻", "马上", "省心"
    };
    private static final String[] COMMERCE_WORDS = {
            "limited", "offer", "only", "deal", "discount", "sale", "off", "free shipping",
            "限时", "优惠", "折扣", "立减", "免运费", "到手价", "买一送一", "爆款"
    };
    private static final String[] PRODUCT_EXPLAINER_WORDS = {
            "comfortable", "breathable", "support", "steady", "soft", "lightweight", "waterproof", "durable",
            "舒适", "透气", "支撑", "稳定", "轻薄", "防水", "耐用", "细节"
    };

    @Override
    public List<String> resolveKeywords(TkGenerationTaskDO task, String scriptText) {
        Set<String> keywords = new LinkedHashSet<>();
        if (Boolean.TRUE.equals(task.getSubtitleKeywordEnabled())) {
            addManualKeywords(keywords, task.getSubtitleKeywords());
            String mode = StrUtil.blankToDefault(task.getSubtitleKeywordMode(), "auto_manual");
            if (StrUtil.containsAnyIgnoreCase(mode, "auto")) {
                addAutoKeywords(keywords, scriptText);
                addStyleKeywords(keywords, task.getSubtitleStyle(), scriptText);
            }
        }
        return new ArrayList<>(keywords);
    }

    private void addManualKeywords(Set<String> keywords, String rawKeywords) {
        if (StrUtil.isBlank(rawKeywords)) {
            return;
        }
        for (String item : rawKeywords.split("[,，、\\n\\r]+")) {
            String keyword = StrUtil.trim(item);
            if (StrUtil.isNotBlank(keyword)) {
                keywords.add(keyword);
            }
        }
    }

    private void addAutoKeywords(Set<String> keywords, String scriptText) {
        if (StrUtil.isBlank(scriptText)) {
            return;
        }
        Matcher matcher = PRICE_PATTERN.matcher(scriptText);
        while (matcher.find()) {
            String keyword = StrUtil.trim(matcher.group(1));
            if (StrUtil.length(keyword) >= 2) {
                keywords.add(keyword);
            }
        }
        for (String word : SELLING_WORDS) {
            if (StrUtil.containsIgnoreCase(scriptText, word)) {
                keywords.add(word);
            }
        }
    }

    private void addStyleKeywords(Set<String> keywords, String subtitleStyle, String scriptText) {
        if (StrUtil.isBlank(scriptText)) {
            return;
        }
        String style = StrUtil.blankToDefault(subtitleStyle, "");
        if (StrUtil.equalsAny(style, "yellow_story", "neon_pop", "tiktok_large", "comment_bubble")) {
            addMatchingWords(keywords, scriptText, SOCIAL_HOOK_WORDS);
        }
        if (StrUtil.equalsAny(style, "promo_bold", "price_flash", "yellow_keyword")) {
            addMatchingWords(keywords, scriptText, COMMERCE_WORDS);
        }
        if (StrUtil.equalsAny(style, "clean_product", "step_card", "brand_minimal")) {
            addMatchingWords(keywords, scriptText, PRODUCT_EXPLAINER_WORDS);
        }
    }

    private void addMatchingWords(Set<String> keywords, String scriptText, String[] candidates) {
        for (String word : candidates) {
            if (StrUtil.containsIgnoreCase(scriptText, word)) {
                keywords.add(word);
            }
        }
    }

}
