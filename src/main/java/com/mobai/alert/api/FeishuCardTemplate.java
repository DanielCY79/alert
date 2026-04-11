package com.mobai.alert.api;

/**
 * Feishu interactive card header template.
 */
public enum FeishuCardTemplate {

    GREY("grey"),
    YELLOW("yellow"),
    GREEN("green"),
    BLUE("blue");

    private final String templateCode;

    FeishuCardTemplate(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateCode() {
        return templateCode;
    }
}
