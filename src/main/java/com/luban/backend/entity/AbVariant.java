package com.luban.backend.entity;

/**
 * AB 变体（v02 ab 域）；表 ab_variants。
 * experiment_id + label(A/B) 唯一；page_version_id 指向 page_versions。
 */
public class AbVariant {
    private String id;
    private String experimentId;
    private String label;           // A/B
    private String pageVersionId;
    private int weight;             // 权重（A/B 默认 50/50）
    private boolean isControl;      // 是否对照组

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getPageVersionId() { return pageVersionId; }
    public void setPageVersionId(String pageVersionId) { this.pageVersionId = pageVersionId; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public boolean isControl() { return isControl; }
    public void setControl(boolean control) { isControl = control; }
}
