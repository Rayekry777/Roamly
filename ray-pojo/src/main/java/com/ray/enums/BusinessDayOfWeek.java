package com.ray.enums;

/** 入驻营业时间使用的星期枚举及中文释义。 */
public enum BusinessDayOfWeek {
    MONDAY("星期一"),
    TUESDAY("星期二"),
    WEDNESDAY("星期三"),
    THURSDAY("星期四"),
    FRIDAY("星期五"),
    SATURDAY("星期六"),
    SUNDAY("星期日");

    private final String label;

    BusinessDayOfWeek(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
