package cc.mrbird.febs.common.domain;

/**
 * 根据什么限制，登录的是ip
 */
public enum LimitType {
    // 传统类型
    CUSTOMER,
    // 根据 IP 限制
    IP;
}
